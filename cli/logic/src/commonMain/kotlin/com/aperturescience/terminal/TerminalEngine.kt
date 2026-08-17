package com.aperturescience.terminal

import com.aperturescience.terminal.TerminalEngine.Companion.PAGE_SIZE
import com.aperturescience.terminal.TerminalEngine.Companion.WRAP_WIDTH
import com.aperturescience.terminal.data.QuestionType
import com.aperturescience.terminal.data.TerminalData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

/**
 * Faithful port of the state machine in the decompiled `DoAction.as` (AS2) script that drives
 * ApertureScience17 (2007-10-17).swf. Mirrors that script's `processInput0/1/2/5`, `switchPage`,
 * `formatQuestion`, `thecakeisalie`, `bosskey`, and `notesdisplay` functions closely enough that
 * behavior (including its quirks) should match - see [Mode] for how "where the user currently is"
 * is represented (a sealed hierarchy, not the original's raw `entryMode`/`qon` int pair).
 *
 * This class has no UI dependency of any kind, and never terminates the process itself (no
 * `exitProcess`/`System.exit()` anywhere here - that would be fatal to embed in a test suite or a
 * server): [liveLine] is a plain [StateFlow] any front end can collect, [onKeyEvent] takes a
 * plain key name, and [exitRequested] is how it signals "this session is over" - the host
 * decides what that actually means (end a coroutine, close a connection, exit a CLI process...).
 * Ctrl+C is handled by the UI layer, not here - it's our own escape hatch, not part of the
 * original terminal's modeled behavior.
 *
 * Rendering matches the original's `clearScreen()`-then-redraw model: every new page wipes
 * [EngineState.pageContent] and rebuilds it from scratch, exactly like the original wiped its
 * Flash canvas before drawing the next banner - including that whatever you just typed disappears
 * along with everything else. It is not echoed anywhere once submitted, matching the original.
 *
 * Deliberate deviations from the original, since this targets a real terminal instead of a
 * Flash canvas:
 *  - `gdxt.php` server calls have no backend; they are no-ops. `uid` is synthesized locally.
 *  - Flash's `getURL()` navigation (LOGOUT / PLAY PORTAL) ends the session instead of opening
 *    a browser.
 *  - The cosmetic "glitching UID digits" and rare cake-image flicker are not reproduced.
 */
class TerminalEngine(
    private val instantReveal: Boolean = false,
    initialState: EngineState? = null,
) {
    private val _liveLine = MutableStateFlow("")
    val liveLine: StateFlow<String> = _liveLine.asStateFlow()

    private val _exitRequested = MutableStateFlow(false)

    /** True once the session should end (LOGOUT/PLAY PORTAL) - see the class doc. */
    val exitRequested: StateFlow<Boolean> = _exitRequested.asStateFlow()

    private lateinit var scope: CoroutineScope

    /** Every mutable field this engine carries between turns, held as one value instead of
     * scattered across separate properties - every transition reassigns this via `.copy(...)`.
     * See [EngineState] and [captureState]/[initialState] for how a host resumes a session from
     * one of these without keeping a live [TerminalEngine] around. */
    private var state: EngineState =
        initialState ?: EngineState(
            mode = Mode.Login.Initial,
            isAdmin = false,
            uid = synthesizeUid(),
            pageContent = "",
            input = "",
            wrapWidth = WRAP_WIDTH,
            isLocked = true,
        )

    // Fully determined by isAdmin - not stored, so there's no way for these to go stale relative to
    // it (a regular login never reassigns them; the original AS2 port did, redundantly, since it
    // had no equivalent of a computed property).
    private val gladosHeader: String
        get() =
            if (state.isAdmin) {
                "GLaDOS v1.07a (c) 1982 Aperture Science, Inc."
            } else {
                "GLaDOS v1.07 (c) 1982 Aperture Science, Inc."
            }
    private val gladosPrompt: String
        get() = if (state.isAdmin) "^^ADMIN> " else " ^^B:\\> "

    init {
        // _liveLine defaults to "" to match today's behavior (nothing is shown before boot()),
        // which is exactly right for the no-initialState case. Restoring from a captured
        // EngineState is different: the caller expects the resumed engine to already be showing
        // the same content the original was, not a blank screen until the next turn - so seed it
        // here the same way updateLiveLine() would from the restored pageContent/input.
        if (initialState != null) updateLiveLine()
    }

    /**
     * Sets the exact column width future [reveal]-wrapped lines are hard-wrapped to - a plain
     * assignment, not a cap: callers who want [WRAP_WIDTH] as an upper bound (matching the
     * original's own pixel-wrap threshold, e.g. a real terminal that happens to be wider than
     * that) apply `minOf(columns, WRAP_WIDTH)` themselves (see `ui-terminal`'s `App.kt`). A host
     * whose own rendering already reflows text correctly - e.g. `ui-web`'s CSS `white-space:
     * pre-wrap` - can instead pass something far larger than any real line to suppress this
     * hard-wrap entirely, so only one layer (its own) is ever deciding where lines actually
     * break; see `ui-web`'s `Main.kt` for why running both at once, even nominally agreeing on
     * "100", still produced visibly mismatched wraps in practice.
     */
    fun setViewportWidth(columns: Int) {
        if (columns > 0) state = state.copy(wrapWidth = columns)
    }

    /**
     * Snapshots every field that varies turn-to-turn, for a host that can't keep this instance
     * alive in memory between turns (e.g. a stateless request/response session) - pass the result
     * to another `TerminalEngine`'s [initialState] constructor parameter to resume exactly where
     * this one left off. See [EngineState] and the "batch API" section below for the intended
     * pairing (`instantReveal = true` + [bootTurn]/[submitLine]/[advance]/[page]).
     */
    fun captureState(): EngineState = state

    fun boot(coroutineScope: CoroutineScope) {
        scope = coroutineScope
        scope.launch { bootBody() }
    }

    private suspend fun bootBody() {
        clearScreen()
        reveal(TerminalData.qar[0], TerminalData.qdelay[0])
    }

    // ---------------------------------------------------------------------
    // Synchronous "batch" API for stateless hosts (e.g. a request/response Minitel service) that
    // can't stay subscribed to a long-lived session the way boot()/onKeyEvent() assume - a host
    // like that only ever sees one already-validated line of input per turn, never individual
    // keystrokes. Meaningful only with instantReveal = true: every call below runs an entire turn
    // to completion without ever touching `scope` (unlike boot()/onKeyEvent(), which fire-and-
    // forget via `scope.launch`), then returns the resulting [liveLine] snapshot directly. Callers
    // persist/restore session state via [EngineState]/[captureState]/the [initialState]
    // constructor parameter instead of keeping this instance alive in memory between turns.
    // ---------------------------------------------------------------------

    /** [boot]'s synchronous equivalent - only meaningful on a freshly-constructed engine. */
    suspend fun bootTurn(): String {
        bootBody()
        return liveLine.value
    }

    /** Submits [line] as a whole already-validated line of input - the batch-API equivalent of
     * typing [line] character by character via [onKeyEvent] and then pressing Enter. */
    suspend fun submitLine(line: String): String {
        setInputLine(line)
        handleEnter()
        return liveLine.value
    }

    /** The "any accepted key" equivalent [onKeyEvent] uses for MODE_NOTES/MODE_CAKE/MODE_BOSSKEY,
     * where there is no line-based input at all - just some input event advancing the page. */
    suspend fun advance(): String {
        when (state.mode) {
            Mode.Cake, Mode.BossKey -> toggleCakeBosskey()
            else -> handleEnter()
        }
        return liveLine.value
    }

    /** [handlePaging]'s synchronous equivalent (Q21's own >[PAGE_SIZE]-choice pagination). */
    suspend fun page(delta: Int): String {
        handlePagingBody(delta)
        return liveLine.value
    }

    fun onKeyEvent(key: String): Boolean {
        if (state.isLocked) return true

        // In the cake/bosskey easter egg, ANY accepted key toggles between the two screens -
        // there is no line-based input at all here (matches the original's onKeyDown handler).
        if (state.mode == Mode.Cake || state.mode == Mode.BossKey) {
            if (isAcceptedRawKey(key)) {
                scope.launch { toggleCakeBosskey() }
            }
            return true
        }

        // NOTES.EXE forces every keystroke to behave like Enter (notesCursor in the original).
        if (state.mode is Mode.Notes) {
            if (isAcceptedRawKey(key)) {
                scope.launch { handleEnter() }
            }
            return true
        }

        when {
            key == "Enter" -> scope.launch { handleEnter() }
            key == "Backspace" -> {
                if (state.input.isNotEmpty()) {
                    state = state.copy(input = state.input.dropLast(1))
                    updateLiveLine()
                }
            }

            key == "PageUp" && state.mode is Mode.Application -> handlePaging(-PAGE_SIZE)
            key == "PageDown" && state.mode is Mode.Application -> handlePaging(PAGE_SIZE)
            key.length == 1 -> {
                val c = key[0]
                if (isAcceptedChar(c) && state.input.length < MAX_INPUT_LENGTH) {
                    state = state.copy(input = state.input + c.uppercaseChar())
                    updateLiveLine()
                }
            }
        }
        return true
    }

    private fun updateLiveLine() {
        val echoed = if (state.mode is Mode.Login.Password) "*".repeat(state.input.length) else state.input
        _liveLine.value = state.pageContent + echoed
    }

    private fun clearScreen() {
        state = state.copy(pageContent = "")
        _liveLine.value = ""
    }

    private suspend fun handlePagingBody(delta: Int) {
        // No guard needed against a non-Application mode the way the original's equivalent
        // (indexing TerminalData.questions[qon - 1] using whatever qon happened to hold) would
        // have needed one - the sealed Mode makes "paginate while there's no question on screen"
        // a type-level impossibility to handle here rather than a silent misbehavior.
        val current = state.mode as? Mode.Application ?: return
        val choices = TerminalData.questions[current.questionNumber - 1].choices
        if (choices.size <= PAGE_SIZE) return
        val next = (current.pageOffset + delta).coerceIn(0, choices.size - 1)
        if (next != current.pageOffset) {
            state = state.copy(mode = current.copy(pageOffset = next), isLocked = true)
            clearScreen()
            showQuestion()
        }
    }

    private fun handlePaging(delta: Int) {
        scope.launch { handlePagingBody(delta) }
    }

    /** Whole-line equivalent of the per-character filtering [onKeyEvent] applies as the user
     * types - shared so [submitLine] accepts exactly the same characters as typing would. */
    private fun setInputLine(line: String) {
        val filtered =
            buildString {
                for (c in line) {
                    if (isAcceptedChar(c) && length < MAX_INPUT_LENGTH) {
                        append(c.uppercaseChar())
                    }
                }
            }
        state = state.copy(input = filtered)
    }

    private suspend fun handleEnter() {
        state = state.copy(isLocked = true)
        val submitted = state.input

        when (val current = state.mode) {
            is Mode.Login -> dispatchLogin(current, submitted)
            is Mode.Shell -> dispatchShell(submitted)
            is Mode.Application -> dispatchApplication(current, submitted)
            is Mode.Notes -> dispatchNotes(current)
            // CAKE/BOSSKEY's "any key" is handled via advance()/onKeyEvent calling
            // toggleCakeBosskey() directly - matches the original's onKeyDown, which never routed
            // Enter through processInput while in either of those two screens.
            Mode.Cake, Mode.BossKey -> Unit
        }
    }

    // ---------------------------------------------------------------------
    // Mode.Login — login / job-application flow (processInput0 + switchPage case 0)
    // ---------------------------------------------------------------------
    private suspend fun dispatchLogin(
        current: Mode.Login,
        text: String,
    ) {
        var advance = false
        when (current) {
            Mode.Login.Initial -> {
                advance = text == "LOGON" || text == "LOGIN" || text == "USER"
                if (advance) state = state.copy(mode = Mode.Login.Username)
                if (text == "HELP" || text == "?") {
                    advance = true
                    state = state.copy(mode = Mode.Login.Help)
                }
            }
            // The original aliased qon==8 (help just shown) back to qon==0's own logic - reading
            // help and pressing anything follows the exact same rules as the initial "> " prompt.
            Mode.Login.Help -> {
                advance = text == "LOGON" || text == "LOGIN" || text == "USER"
                if (advance) state = state.copy(mode = Mode.Login.Username)
                if (text == "HELP" || text == "?") {
                    advance = true
                    state = state.copy(mode = Mode.Login.Help)
                }
            }

            Mode.Login.Username -> {
                advance = text.length > 2
                state = state.copy(isAdmin = text == "CJOHNSON")
                if (advance) state = state.copy(mode = Mode.Login.Password(isRetry = false))
            }

            is Mode.Login.Password -> {
                if (state.isAdmin) {
                    advance = text == "TIER3"
                    state = state.copy(isAdmin = advance)
                } else {
                    advance = text == "PORTAL" || text == "PORTALS"
                }
                // The page always advances - a wrong password redisplays this prompt with an
                // error (the isRetry=true screen) instead of blocking input; only a correct
                // password actually unlocks the shell.
                state =
                    state.copy(
                        mode =
                            if (advance) {
                                Mode.Shell()
                            } else {
                                advance = true
                                Mode.Login.Password(isRetry = true)
                            },
                    )
            }

            Mode.Login.ApplicationIntro -> {
                if (text == "CONTINUE") {
                    advance = true
                    state = state.copy(mode = Mode.Login.ApplicationUidDisplay)
                }
                if (text == "QUIT") {
                    advance = true
                    state = state.copy(mode = Mode.Shell())
                }
            }

            Mode.Login.ApplicationUidDisplay -> {
                if (text == "CONTINUE") {
                    advance = true
                    state = state.copy(mode = Mode.Application(questionNumber = 1))
                }
                if (text == "QUIT") {
                    advance = true
                    state = state.copy(mode = Mode.Shell())
                }
            }

            Mode.Login.UinEntry -> {
                state = state.copy(mode = if (text == "THECAKEISALIE") Mode.Cake else Mode.Login.Terminal)
                advance = true
            }
            // Terminal "does not match" screen - there is no way back from here.
            Mode.Login.Terminal -> advance = false
        }

        finishTurn(advance)
    }

    // ---------------------------------------------------------------------
    // Mode.Shell — GLaDOS shell (processInput1)
    // ---------------------------------------------------------------------
    private suspend fun dispatchShell(rawText: String) {
        val text = rawText.trimStart()
        if (text.isEmpty()) {
            // The original returns immediately without even clearing the field.
            _liveLine.value = state.pageContent + state.input
            state = state.copy(isLocked = false)
            return
        }
        val args = text.split(" ")
        var message = ""
        when (args[0]) {
            "THECAKEISALIE" -> state = state.copy(mode = Mode.Cake)

            "DIR", "CATALOG", "DIRECTORY", "LIST", "LS", "CAT" -> {
                message =
                    if (state.isAdmin) {
                        "\nDISK VOLUME 255 [WORKSTATION CJOHNSON]\n\n" +
                            "     I  019  APPLY.EXE\n     I  004  NOTES.EXE\n\n" +
                            "2 FILE(S) IN 23 BLOCKS\n"
                    } else {
                        "\nDISK VOLUME 255 [NEW EMPLOYEE WORKSTATION]\n\n" +
                            "     I  019  APPLY.EXE\n\n1 FILE(S) IN 19 BLOCKS\n"
                    }
            }

            "IP" -> message = " \n\nuid:${state.uid}\n"

            "HELP", "LIB", "?" -> {
                message =
                    if (state.isAdmin) {
                        " \n\nLIB\n     NOTES\n     APPEND\n     ATTRIB\n     COPY\n     DIR\n     ERASE\n" +
                            "     FORMAT\n     INTERROGATE\n     LIB\n     PLAY\n     RENAME\n     TAPEDISK"
                    } else {
                        " \n\nLIB\n     APPEND\n     ATTRIB\n     COPY\n     DIR\n     ERASE\n     FORMAT\n" +
                            "     INTERROGATE\n     LIB\n     PLAY\n     RENAME\n     TAPEDISK"
                    }
            }

            "LOGOUT", "BYE", "LOGOFF", "VALVE" -> {
                farewell("ERROR: STORE NOT FOUND")
                return
            }

            "APPEND", "ATTRIB", "COPY", "FORMAT", "ERASE", "RENAME" ->
                message = "\n\nERROR 15 [Disk is write protected]"

            "PLAY" ->
                when {
                    args.size == 1 -> message = "\n\nERROR 03 [What would you like to play?]"
                    args.getOrNull(1) == "PORTAL" -> {
                        farewell("ERROR: TRAILER NOT FOUND")
                        return
                    }
                }

            "INTERROGATE" ->
                message =
                    when {
                        args.size == 1 -> "\n\nERROR 02 [Command requires at least one parameter]"
                        state.isAdmin -> "\n\nERROR 07 [Unknown Employee]"
                        else -> "\n\nERROR 01 [Illegal attempt to initiate disciplinary action]"
                    }

            "TAPEDISK" -> message = "\n\nERROR 18 [User not authorized to transfer system tapes]"

            "NOTES", "NOTES.EXE" -> {
                if (state.isAdmin) {
                    state = state.copy(mode = Mode.Notes(page = 1))
                } else {
                    message = "\n\nERROR 24 [File '${args[0]}' not found]"
                }
            }

            "APPLY", "APPLY.EXE" -> state = state.copy(mode = Mode.Login.ApplicationIntro)

            else -> message = "\n\nERROR 24 [File '${args[0]}' not found]"
        }

        // Only overwrite mode with the freshly-built message if a branch above didn't already
        // switch to a different screen entirely (THECAKEISALIE/NOTES/APPLY) - those already set
        // their own target mode, which this must not clobber.
        if (state.mode is Mode.Shell) {
            state = state.copy(mode = Mode.Shell(message = message))
        }
        showNextPage()
    }

    // ---------------------------------------------------------------------
    // Mode.Application — job application questionnaire (processInput2)
    // ---------------------------------------------------------------------
    private suspend fun dispatchApplication(
        current: Mode.Application,
        text: String,
    ) {
        // Matches an original off-by-one: once qon reaches the question count, Enter ends the
        // form immediately without validating (or submitting) whatever was typed for the last
        // question.
        if (current.questionNumber >= TerminalData.questions.size) {
            state = state.copy(mode = Mode.Login.UinEntry)
            showNextPage()
            return
        }

        val question = TerminalData.questions[current.questionNumber - 1]
        var advance = false
        if (question.type != QuestionType.TEXT) {
            val choice = text.toIntOrNull()
            if (choice != null && choice > 0 && choice <= question.choices.size) {
                advance = true
            }
        } else if (current.questionNumber != 51) {
            advance = true
        }

        state =
            state.copy(
                mode =
                    if (text == "QUIT") {
                        advance = true
                        Mode.Shell()
                    } else if (advance) {
                        current.copy(questionNumber = current.questionNumber + 1, pageOffset = 0)
                    } else {
                        current
                    },
            )

        finishTurn(advance)
    }

    // ---------------------------------------------------------------------
    // Mode.Notes — NOTES.EXE reader (processInput5)
    // ---------------------------------------------------------------------
    private suspend fun dispatchNotes(current: Mode.Notes) {
        val nextPage = current.page + 1
        state = state.copy(mode = if (nextPage > MAX_NOTES_PAGE) Mode.Shell() else Mode.Notes(page = nextPage))
        showNextPage()
    }

    // ---------------------------------------------------------------------
    // shared helpers
    // ---------------------------------------------------------------------

    /** Mirrors the accept/reject branch at the bottom of processInput0/processInput2. */
    private suspend fun finishTurn(advance: Boolean) {
        if (advance) {
            showNextPage()
        } else {
            state = state.copy(input = "", isLocked = false)
            _liveLine.value = state.pageContent
        }
    }

    /** Mirrors switchPage(): clears the screen, then dispatches on the (possibly just-changed)
     * [state]'s mode - exactly one clear-and-redraw per page transition. Unlike the original's
     * `qon++`-then-lookup, the mode already holds the exact target screen by the time this runs
     * (every transition above sets it directly), so there's no index arithmetic left to do here. */
    private suspend fun showNextPage() {
        clearScreen()
        state = state.copy(input = "")
        when (val current = state.mode) {
            is Mode.Login -> reveal(TerminalData.qar[current.qarIndex], TerminalData.qdelay[current.qarIndex])
            is Mode.Shell -> reveal(gladosHeader + current.message + gladosPrompt, GLADOS_SPEED)
            is Mode.Application -> showQuestion()
            Mode.BossKey -> revealInstant(BOSSKEY_SPREADSHEET)
            Mode.Cake -> {
                reveal(CAKE_MONOLOGUE_1, 0, unlockAfter = false)
                maybeDelay(2000) // stand-in for security02.flv playback
                reveal(CAKE_MONOLOGUE_2, 0)
            }
            // Verbatim, matching the original's notesdisplay(): cjHistory's own text already
            // ends in "[MORE]"/"[END]" (see TerminalData) - nothing appended here, faithfully
            // (the original never printed a "press ENTER to continue" hint either; any accepted
            // key already advances a NOTES.EXE page regardless, see onKeyEvent's MODE_NOTES arm).
            is Mode.Notes -> reveal(TerminalData.cjHistory[current.page - 1], NOTES_SPEED)
        }
    }

    private suspend fun showQuestion() {
        val current = state.mode as Mode.Application
        val question = TerminalData.questions[current.questionNumber - 1]
        val header = "Form FORMS-EN-2873-FORM - Page ${current.questionNumber}\n\n${question.text}\n\n"
        if (question.type == QuestionType.TEXT) {
            reveal(header + "> ", 25)
            return
        }
        reveal(header, if (current.pageOffset > 0) 1 else 15, unlockAfter = false)

        val total = question.choices.size
        val padWidth = (total + 1).toString().length
        val pageEnd = minOf(current.pageOffset + PAGE_SIZE, total)
        val body =
            buildString {
                for (i in current.pageOffset until pageEnd) {
                    append((i + 1).toString().padStart(padWidth, '0'))
                    append("] ")
                    append(question.choices[i])
                    append('\n')
                }
            }
        val prompt =
            if (total > PAGE_SIZE) {
                "[$total total choices : PGUP/PGDN to navigate]> "
            } else {
                "> "
            }
        revealInstant(body + prompt)
    }

    private suspend fun toggleCakeBosskey() {
        state = state.copy(mode = if (state.mode == Mode.Cake) Mode.BossKey else Mode.Cake)
        showNextPage()
    }

    /**
     * Ends the session in place of the original's `getURL()` browser navigation (LOGOUT to
     * steampowered.com, PLAY PORTAL to the trailer), which a terminal can't perform - shown as
     * an in-universe terminal error rather than a bracketed dev note explaining what would have
     * happened (matching the CAKE_MONOLOGUE_1 security02.flv fix: `[errorMessage]`, not
     * "this would open <url> in your browser").
     */
    private suspend fun farewell(errorMessage: String) {
        clearScreen()
        reveal("\n[$errorMessage]\n", GLADOS_SPEED)
        maybeDelay(400)
        _exitRequested.value = true
    }

    /**
     * Every real wall-clock suspension in this class - the typewriter effect in [reveal], and the
     * cosmetic pauses in [farewell]/the cake monologue above - goes through here instead of
     * calling `delay()` directly, so [instantReveal] hosts (a request/response session that must
     * run a whole turn to completion synchronously, see the batch API below [boot]) get identical
     * *content* with zero wall-clock cost, while [boot]/[onKeyEvent]-driven hosts (`ui-terminal`,
     * `ui-web`) see exactly today's timing (`instantReveal` defaults to `false`).
     */
    private suspend fun maybeDelay(ms: Long) {
        if (!instantReveal) {
            delay(ms.milliseconds)
        }
    }

    /**
     * Types [text] out one character at a time, appending onto whatever is already in
     * [EngineState.pageContent] (call [clearScreen] first for a fresh page - a full page is
     * usually built from several chained `reveal`/`revealInstant` calls, e.g. a question's header
     * followed by its instantly-revealed choice list, matching how the original's
     * `formatQuestion()` made one `placeText()` call - which cleared - followed by manually
     * placed choice text - which didn't). delayMs <= 0 reveals instantly.
     *
     * `^` (the original's newline marker) becomes a line break and `@` (its UID placeholder)
     * is substituted, exactly as `placeText()` did in the source AS2 - this lets strings sourced
     * verbatim from [TerminalData] be passed straight through unmodified. Lines are also
     * word-wrapped to [EngineState.wrapWidth] (see [setViewportWidth] for who sets it to what and
     * why): defaults to [WRAP_WIDTH], matching the original's own pixel-width auto-wrap
     * threshold. For a host that redraws by diffing/repositioning a cursor rather than replacing
     * whole-page content wholesale (observed with Mosaic), a single long line left unwrapped - or
     * wrapped wider than that host's actual terminal column count - soft-wraps in the real
     * terminal while still animating, which desyncs the host's own redraw bookkeeping and leaves
     * stray duplicate rows behind; such a host must keep the wrap width at or below its real
     * column count via [setViewportWidth]. A host that instead redraws by wholesale-replacing
     * rendered content (no cursor/diffing involved at all) has no such risk and may leave
     * hard-wrapping to its own rendering entirely by setting it far wider than any real line.
     */
    private suspend fun reveal(
        text: String,
        delayMs: Int,
        unlockAfter: Boolean = true,
    ) {
        val logicalLines = text.replace("@", "[${state.uid}]").replace("^", "\n").split("\n")
        val lines = logicalLines.flatMap { wordWrap(it, state.wrapWidth) }
        for ((index, line) in lines.withIndex()) {
            val base = state.pageContent
            if (delayMs > 0) {
                val sb = StringBuilder()
                for (ch in line) {
                    sb.append(ch)
                    _liveLine.value = base + sb
                    maybeDelay(delayMs.toLong())
                }
                state = state.copy(pageContent = base + sb)
            } else {
                state = state.copy(pageContent = base + line)
                _liveLine.value = state.pageContent
            }
            if (index != lines.lastIndex) {
                state = state.copy(pageContent = state.pageContent + "\n")
            }
        }
        _liveLine.value = state.pageContent
        if (unlockAfter) {
            state = state.copy(isLocked = false)
        }
    }

    /** Reveals every line instantly, appending onto [EngineState.pageContent] (see [reveal]). */
    private suspend fun revealInstant(text: String) = reveal(text, 0)

    private fun wordWrap(
        line: String,
        width: Int,
    ): List<String> {
        if (line.length <= width) return listOf(line)
        val result = mutableListOf<String>()
        val current = StringBuilder()
        for (word in line.split(" ")) {
            var w = word
            while (w.length > width) {
                if (current.isNotEmpty()) {
                    result.add(current.toString())
                    current.clear()
                }
                result.add(w.substring(0, width))
                w = w.substring(width)
            }
            val candidateLen = current.length + (if (current.isEmpty()) 0 else 1) + w.length
            if (candidateLen > width && current.isNotEmpty()) {
                result.add(current.toString())
                current.clear()
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(w)
        }
        if (current.isNotEmpty() || result.isEmpty()) result.add(current.toString())
        return result
    }

    /** Which [TerminalData.qar]/[TerminalData.qdelay] entry each [Mode.Login] state reveals -
     * purely a rendering detail local to [showNextPage], unlike the original AS2 port's `qon`
     * (which also doubled as the persisted state identifier - see [Mode]'s doc). */
    private val Mode.Login.qarIndex: Int
        get() =
            when (this) {
                Mode.Login.Initial -> 0
                Mode.Login.Username -> 1
                is Mode.Login.Password -> if (isRetry) 3 else 2
                Mode.Login.ApplicationIntro -> 4
                Mode.Login.ApplicationUidDisplay -> 5
                Mode.Login.Help -> 8
                Mode.Login.UinEntry -> 9
                Mode.Login.Terminal -> 10
            }

    companion object {
        private const val GLADOS_SPEED = 7
        private const val NOTES_SPEED = 3
        private const val MAX_INPUT_LENGTH = 65

        /** Q21's own >[PAGE_SIZE]-choice pagination page size (see [handlePagingBody]/[page]) -
         * public so a stateless host knows what delta to pass to [page]. */
        const val PAGE_SIZE = 104

        /**
         * Default wrap width, matching the original's own pixel-width auto-wrap threshold.
         * Public so a CLI-style host can reproduce the "cap at this, never wrap wider" behavior
         * itself when calling [setViewportWidth] with a real (possibly wider) terminal column
         * count - see that function's doc.
         */
        const val WRAP_WIDTH = 100
        private const val MAX_NOTES_PAGE = 4

        private fun isAcceptedChar(c: Char): Boolean =
            c.isDigit() || c.uppercaseChar() in 'A'..'Z' || c == ' ' || c == '?'

        private fun isAcceptedRawKey(key: String): Boolean =
            key == "Enter" ||
                key == "Backspace" ||
                key == " " ||
                (key.length == 1 && isAcceptedChar(key[0]))

        private fun synthesizeUid(): String {
            val chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
            return buildString {
                repeat(12) { append(chars[Random.nextInt(chars.length)]) }
            }
        }

        // The original's thecakeisalie() doesn't use placeText()'s normal typewriter+auto-wrap
        // path at all - it manually places each par[i] entry as its own text field, stacked by Y
        // position. Sentences that ran long were split across several par[] entries purely
        // because they didn't fit the original embedded Flash movie's much narrower canvas
        // width, not because they're actually separate thoughts - e.g. par[5..8] is one
        // continuous run ("I did find out a few things ... Check out this security feed.")
        // spread across four array entries. Reconstructed here as actual sentences/paragraphs
        // (joined with spaces, not \n) so this reflows normally instead of hard-breaking
        // mid-clause the way the original's array boundaries would - matching how TerminalData's
        // qar[]/cjhistory[]-sourced text (a different, auto-wrapping rendering path in the
        // original) already only hard-breaks at genuine paragraph boundaries.
        private val CAKE_MONOLOGUE_1 =
            listOf(
                ">",
                ">>>&!>>",
                "When was the last time you left the building?",
                "Has anybody left the building lately?",
                "I don't know why we're in lockdown. I don't know who's in charge.",
                "I did find out a few things, like these terminals don't have to tap out " +
                    "characters one at a time. And while we're all working on twenty year old " +
                    "equipment, somehow they can afford to build an 'Enrichment Center'. Check " +
                    "out this security feed.",
                "Whatever the hell a 'relaxation vault' is, it doesn't have any doors.",
                "",
                "[ERROR: SECURITY02.FLV NOT FOUND]",
                "",
            ).joinToString("\n")

        private val CAKE_MONOLOGUE_2 =
            listOf(
                "",
                "I don't think going home is part of our job description anymore.",
                "If a supervisor walks by, press return!",
            ).joinToString("\n")

        private val BOSSKEY_SPREADSHEET =
            """
            B8    (L) TOTAL
                A         B          C          D          E
             1
             2       ITEM      UNITS      PER-U       EXT
             3       ----      -----      -----      ------
             4      FLOUR         50      21.50     1075.00
             5  INTUB-XLG          1 974,999.99  974,999.99
             6  TACK-THMB         75       0.02        1.50
             7                                    ----------
             8  TOTAL                     976,076.49
            """.trimIndent() + "\n"
    }
}
