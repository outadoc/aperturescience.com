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

/** Faithful port of `DoAction.as`'s state machine (ApertureScience17 SWF). UI-agnostic:
 * [liveLine]/[exitRequested] are plain [StateFlow]s, never calls `exitProcess()`. */
class TerminalEngine(
    private val instantReveal: Boolean = false,
    initialState: EngineState? = null,
) {
    private val _liveLine = MutableStateFlow("")
    val liveLine: StateFlow<String> = _liveLine.asStateFlow()

    private val _annotations = MutableStateFlow<List<TextAnnotation>>(emptyList())

    /** Tagged spans over [liveLine] (e.g. [BLINK_TAG]) - live counterpart to
     * [EngineState.annotations], published as soon as a span finishes revealing. */
    val annotations: StateFlow<List<TextAnnotation>> = _annotations.asStateFlow()

    private val _exitRequested = MutableStateFlow(false)

    /** True once the session should end (LOGOUT/PLAY PORTAL). */
    val exitRequested: StateFlow<Boolean> = _exitRequested.asStateFlow()

    private lateinit var scope: CoroutineScope

    /** Every mutable field, held as one value - every transition reassigns via `.copy(...)`. */
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

    // Derived from isAdmin, not stored - a regular login never reassigns these.
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
        // Seeds liveLine/annotations when resuming from a captured state, so they're not blank
        // until next turn.
        if (initialState != null) {
            updateLiveLine()
            _annotations.value = state.annotations
        }
    }

    /** Column width future [reveal]-wrapped lines are hard-wrapped to - not a cap; callers who
     * want [WRAP_WIDTH] as an upper bound apply `minOf(columns, WRAP_WIDTH)` themselves. */
    fun setViewportWidth(columns: Int) {
        if (columns > 0) state = state.copy(wrapWidth = columns)
    }

    /** Snapshots state for a host that can't keep this instance alive between turns (e.g. a
     * stateless HTTP session) - see [EngineState] and the batch API below. */
    fun captureState(): EngineState = state

    fun boot(coroutineScope: CoroutineScope) {
        scope = coroutineScope
        scope.launch { bootBody() }
    }

    private suspend fun bootBody() {
        clearScreen()
        reveal(TerminalData.qar[0], TerminalData.qdelay[0])
    }

    // Synchronous "batch" API for stateless hosts (e.g. a request/response Minitel service):
    // runs a whole turn to completion via instantReveal, never touching `scope`.

    /** [boot]'s synchronous equivalent - only meaningful on a freshly-constructed engine. */
    suspend fun bootTurn(): String {
        bootBody()
        return liveLine.value
    }

    /** Submits [line] as a whole already-validated line, the batch-API equivalent of typing it
     * via [onKeyEvent] then pressing Enter. */
    suspend fun submitLine(line: String): String {
        setInputLine(line)
        handleEnter()
        return liveLine.value
    }

    /** The "any accepted key" equivalent [onKeyEvent] uses for NOTES/CAKE/BOSSKEY. */
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
        val namedKey = NamedKey.from(key)

        // Cake/bosskey: any accepted key toggles between the two screens, no line input at all.
        if (state.mode == Mode.Cake || state.mode == Mode.BossKey) {
            if (isAcceptedRawKey(namedKey, key)) {
                scope.launch { toggleCakeBosskey() }
            }
            return true
        }

        // NOTES.EXE forces every keystroke to behave like Enter.
        if (state.mode is Mode.Notes) {
            if (isAcceptedRawKey(namedKey, key)) {
                scope.launch { handleEnter() }
            }
            return true
        }

        when {
            namedKey == NamedKey.ENTER -> scope.launch { handleEnter() }
            namedKey == NamedKey.BACKSPACE -> {
                if (state.input.isNotEmpty()) {
                    state = state.copy(input = state.input.dropLast(1))
                    updateLiveLine()
                }
            }

            namedKey == NamedKey.PAGE_UP && state.mode is Mode.Application -> handlePaging(-PAGE_SIZE)
            namedKey == NamedKey.PAGE_DOWN && state.mode is Mode.Application -> handlePaging(PAGE_SIZE)
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
        state = state.copy(pageContent = "", annotations = emptyList())
        _liveLine.value = ""
        _annotations.value = emptyList()
    }

    private suspend fun handlePagingBody(delta: Int) {
        val current = state.mode as? Mode.Application ?: return
        val choices = TerminalData.questions[current.questionNumber - 1].choices
        if (choices.size <= PAGE_SIZE) return
        // Matches the original: overshooting past the end reverts to the untouched offset rather
        // than clamping to the last index, so a PageDown past the final (already-complete) page
        // is a no-op instead of landing on a spurious page showing only the last item alone.
        var next = current.pageOffset + delta
        if (next < 0) next = 0
        if (next > choices.size) next = current.pageOffset
        if (next != current.pageOffset) {
            state = state.copy(mode = current.copy(pageOffset = next), isLocked = true)
            clearScreen()
            showQuestion()
        }
    }

    private fun handlePaging(delta: Int) {
        scope.launch { handlePagingBody(delta) }
    }

    /** Whole-line equivalent of [onKeyEvent]'s per-character filtering. */
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
            // Cake/bosskey's any-key handling goes through advance()/toggleCakeBosskey(), not here.
            Mode.Cake, Mode.BossKey -> Unit
        }
    }

    // Mode.Login — login / job-application flow (processInput0 + switchPage case 0)
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
            // Reading help then typing anything follows the same rules as the initial prompt.
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
                // Always advances - a wrong password redisplays with an error, not a block.
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
            // Dead end - there is no way back from here.
            Mode.Login.Terminal -> advance = false
        }

        finishTurn(advance)
    }

    // Mode.Shell — GLaDOS shell (processInput1)
    private suspend fun dispatchShell(rawText: String) {
        val text = rawText.trimStart()
        if (text.isEmpty()) {
            // Matches the original: returns immediately without even clearing the field.
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
                        "\n\nDISK VOLUME 255 [WORKSTATION CJOHNSON]\n\n" +
                            "     I  019  APPLY.EXE\n     I  004  NOTES.EXE\n\n" +
                            "2 FILE(S) IN 23 BLOCKS\n\n"
                    } else {
                        "\n\nDISK VOLUME 255 [NEW EMPLOYEE WORKSTATION]\n\n" +
                            "     I  019  APPLY.EXE\n\n1 FILE(S) IN 19 BLOCKS\n\n"
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

        // Skip if a branch above already switched screens (Cake/Notes/Apply).
        if (state.mode is Mode.Shell) {
            state = state.copy(mode = Mode.Shell(message = message))
        }
        showNextPage()
    }

    // Mode.Application — job application questionnaire (processInput2)
    private suspend fun dispatchApplication(
        current: Mode.Application,
        text: String,
    ) {
        // Off-by-one: reaching the question count ends the form without validating the last answer.
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

    // Mode.Notes — NOTES.EXE reader (processInput5)
    private suspend fun dispatchNotes(current: Mode.Notes) {
        val nextPage = current.page + 1
        state = state.copy(mode = if (nextPage > MAX_NOTES_PAGE) Mode.Shell() else Mode.Notes(page = nextPage))
        showNextPage()
    }

    // shared helpers

    /** Accept/reject branch at the bottom of processInput0/processInput2. */
    private suspend fun finishTurn(advance: Boolean) {
        if (advance) {
            showNextPage()
        } else {
            state = state.copy(input = "", isLocked = false)
            _liveLine.value = state.pageContent
        }
    }

    /** Clears and redraws for [state]'s mode - already the exact target screen by the time this
     * runs, so there's no index math left to do here (mirrors switchPage()). */
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
            // cjHistory already ends in "[MORE]"/"[END]" - nothing appended here.
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

    /** Ends the session in place of the original's browser navigation (LOGOUT/PLAY PORTAL),
     * which a terminal can't perform - shown as an in-universe terminal error instead. */
    private suspend fun farewell(errorMessage: String) {
        clearScreen()
        reveal("\n[$errorMessage]\n", GLADOS_SPEED)
        maybeDelay(400)
        _exitRequested.value = true
    }

    /** Every wall-clock suspension goes through here, so [instantReveal] hosts get zero-cost
     * content while [boot]/[onKeyEvent] hosts keep real timing. */
    private suspend fun maybeDelay(ms: Long) {
        if (!instantReveal) {
            delay(ms.milliseconds)
        }
    }

    /** Types [text] out one character at a time onto [EngineState.pageContent]. `^` becomes a
     * newline, `@` the uid (wrapped in [BLINK_START]/[BLINK_END], see [markAnnotationEnd]),
     * word-wrapped to [EngineState.wrapWidth]. delayMs <= 0 reveals instantly. */
    private suspend fun reveal(
        text: String,
        delayMs: Int,
        unlockAfter: Boolean = true,
    ) {
        val uidToken = "$BLINK_START[${state.uid}]$BLINK_END"
        val logicalLines = text.replace("@", uidToken).replace("^", "\n").split("\n")
        val lines = logicalLines.flatMap { wordWrap(it, state.wrapWidth) }
        var blinkStart: Int? = null
        for ((index, line) in lines.withIndex()) {
            val base = state.pageContent
            val sb = StringBuilder()
            for (ch in line) {
                when (ch) {
                    BLINK_START -> blinkStart = base.length + sb.length
                    BLINK_END -> blinkStart?.let { markAnnotationEnd(BLINK_TAG, it, base.length + sb.length) }
                    else -> {
                        sb.append(ch)
                        if (delayMs > 0) {
                            _liveLine.value = base + sb
                            maybeDelay(delayMs.toLong())
                        }
                    }
                }
            }
            state = state.copy(pageContent = base + sb)
            _liveLine.value = state.pageContent
            if (index != lines.lastIndex) {
                state = state.copy(pageContent = state.pageContent + "\n")
            }
        }
        _liveLine.value = state.pageContent
        if (unlockAfter) {
            state = state.copy(isLocked = false)
        }
    }

    /** Publishes a [TextAnnotation] the instant a marked span finishes revealing - not deferred
     * to the end of the whole [reveal] call, so a still-typing trailing paragraph doesn't delay
     * e.g. blink onset. */
    private fun markAnnotationEnd(
        tag: String,
        start: Int,
        end: Int,
    ) {
        state = state.copy(annotations = state.annotations + TextAnnotation(tag, start until end))
        _annotations.value = state.annotations
    }

    /** Reveals every line instantly, appending onto [EngineState.pageContent] (see [reveal]). */
    private suspend fun revealInstant(text: String) = reveal(text, 0)

    private fun wordWrap(
        line: String,
        width: Int,
    ): List<String> {
        if (visibleLength(line) <= width) return listOf(line)
        val result = mutableListOf<String>()
        val current = StringBuilder()
        for (word in line.split(" ")) {
            var w = word
            while (visibleLength(w) > width) {
                if (current.isNotEmpty()) {
                    result.add(current.toString())
                    current.clear()
                }
                val (head, tail) = splitAtVisibleWidth(w, width)
                result.add(head)
                w = tail
            }
            val candidateLen = visibleLength(current) + (if (current.isEmpty()) 0 else 1) + visibleLength(w)
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

    /** [BLINK_START]/[BLINK_END] carry no visible width - wrap decisions go by what actually
     * occupies a column, not raw [CharSequence.length]. */
    private fun visibleLength(s: CharSequence): Int {
        var count = 0
        for (c in s) if (c != BLINK_START && c != BLINK_END) count++
        return count
    }

    /** [wordWrap]'s hard-cut path, marker-aware: splits [s] at exactly [width] visible
     * characters, keeping any [BLINK_START]/[BLINK_END] interleaved within the head intact and in
     * order. A marker landing right at the cut boundary stays attached to the head, so it can
     * never end up alone as an all-invisible trailing "line". */
    private fun splitAtVisibleWidth(
        s: String,
        width: Int,
    ): Pair<String, String> {
        var visible = 0
        var i = 0
        while (i < s.length && visible < width) {
            if (s[i] != BLINK_START && s[i] != BLINK_END) visible++
            i++
        }
        while (i < s.length && (s[i] == BLINK_START || s[i] == BLINK_END)) i++
        return s.substring(0, i) to s.substring(i)
    }

    /** Which qar/qdelay entry each [Mode.Login] state reveals - a rendering detail local to
     * [showNextPage]. */
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

        /** Q21's own >[PAGE_SIZE]-choice pagination size - public so a host knows what delta to
         * pass to [page]. */
        const val PAGE_SIZE = 104

        /** Default wrap width, matching the original's pixel-width auto-wrap threshold. */
        const val WRAP_WIDTH = 100
        private const val MAX_NOTES_PAGE = 4

        /** Never appear in real content (control chars) - mark the start/end of a [reveal]d span
         * that should carry a [TextAnnotation], stripped from [EngineState.pageContent] itself. */
        private const val BLINK_START = '\u0002' // STX
        private const val BLINK_END = '\u0003' // ETX

        /** Keys [onKeyEvent] treats specially, as opposed to a single printable character (see
         * [isAcceptedChar]) - the one place mapping the raw platform key-name string (both
         * `ui-terminal`'s Mosaic and `ui-web`'s DOM `KeyboardEvent.key` already agree on these
         * names) to a definite set the engine understands, instead of scattering string literals. */
        private enum class NamedKey(
            val keyName: String,
        ) {
            ENTER("Enter"),
            BACKSPACE("Backspace"),
            PAGE_UP("PageUp"),
            PAGE_DOWN("PageDown"),
            ARROW_LEFT("ArrowLeft"),
            ;

            companion object {
                private val byName = entries.associateBy { it.keyName }

                fun from(key: String): NamedKey? = byName[key]
            }
        }

        private fun isAcceptedChar(c: Char): Boolean =
            c.isDigit() || c.uppercaseChar() in 'A'..'Z' || c == ' ' || c == '?'

        private fun isAcceptedRawKey(
            namedKey: NamedKey?,
            key: String,
        ): Boolean = namedKey != null || key == " " || (key.length == 1 && isAcceptedChar(key[0]))

        private fun synthesizeUid(): String {
            // 64 chars, matching the "64 digit UIN(+L)" prompt text and the original site.
            val chars = "0123456789abcdefghijklmnopqrstuvwxyz"
            return buildString {
                repeat(64) { append(chars[Random.nextInt(chars.length)]) }
            }
        }

        // Reconstructed as real sentences/paragraphs, not the original's per-line array split
        // (an artifact of the Flash canvas' narrower width, not separate thoughts).
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
