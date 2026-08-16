package com.aperturescience.terminal

import com.aperturescience.terminal.data.QuestionType
import com.aperturescience.terminal.data.TerminalData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Faithful port of the state machine in the decompiled `DoAction.as` (AS2) script that drives
 * ApertureScience17 (2007-10-17).swf. Mirrors that script's `entryMode`/`qon` state pair and its
 * `processInput0/1/2/5`, `switchPage`, `formatQuestion`, `thecakeisalie`, `bosskey`, and
 * `notesdisplay` functions closely enough that behavior (including its quirks) should match.
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
 * [pageContent] and rebuilds it from scratch, exactly like the original wiped its Flash canvas
 * before drawing the next banner - including that whatever you just typed disappears along with
 * everything else. It is not echoed anywhere once submitted, matching the original.
 *
 * Deliberate deviations from the original, since this targets a real terminal instead of a
 * Flash canvas:
 *  - `gdxt.php` server calls have no backend; they are no-ops. `uid` is synthesized locally.
 *  - Flash's `getURL()` navigation (LOGOUT / PLAY PORTAL) ends the session instead of opening
 *    a browser.
 *  - The cosmetic "glitching UID digits" and rare cake-image flicker are not reproduced.
 */
class TerminalEngine {
    private val _liveLine = MutableStateFlow("")
    val liveLine: StateFlow<String> = _liveLine.asStateFlow()

    private val _exitRequested = MutableStateFlow(false)

    /** True once the session should end (LOGOUT/PLAY PORTAL) - see the class doc. */
    val exitRequested: StateFlow<Boolean> = _exitRequested.asStateFlow()

    private var isLocked = true

    private lateinit var scope: CoroutineScope

    private val input = StringBuilder()

    /** Everything drawn on the current page so far, up to (not including) the live input. */
    private var pageContent = ""

    // --- state mirroring DoAction.as globals ---
    private var entryMode = MODE_LOGIN
    private var qon = 0
    private var isCj = false
    private var notesPage = 0
    private var pageOffset = 0
    private var gladosHeader = "GLaDOS v1.07 (c) 1982 Aperture Science, Inc."
    private var gladosPrompt = " ^^B:\\> "
    private var gladosMessage = ""
    private val uid = synthesizeUid()

    fun boot(coroutineScope: CoroutineScope) {
        scope = coroutineScope
        scope.launch {
            clearScreen()
            reveal(TerminalData.qar[0], TerminalData.qdelay[0])
        }
    }

    fun onKeyEvent(key: String): Boolean {
        if (isLocked) return true

        // In the cake/bosskey easter egg, ANY accepted key toggles between the two screens -
        // there is no line-based input at all here (matches the original's onKeyDown handler).
        if (entryMode == MODE_CAKE || entryMode == MODE_BOSSKEY) {
            if (isAcceptedRawKey(key)) {
                scope.launch { toggleCakeBosskey() }
            }
            return true
        }

        // NOTES.EXE forces every keystroke to behave like Enter (notesCursor in the original).
        if (entryMode == MODE_NOTES) {
            if (isAcceptedRawKey(key)) {
                scope.launch { handleEnter() }
            }
            return true
        }

        when {
            key == "Enter" -> scope.launch { handleEnter() }
            key == "Backspace" -> {
                if (input.isNotEmpty()) {
                    input.deleteCharAt(input.length - 1)
                    updateLiveLine()
                }
            }
            key == "PageUp" && entryMode == MODE_APPLICATION -> handlePaging(-PAGE_SIZE)
            key == "PageDown" && entryMode == MODE_APPLICATION -> handlePaging(PAGE_SIZE)
            key.length == 1 -> {
                val c = key[0]
                if (isAcceptedChar(c) && input.length < MAX_INPUT_LENGTH) {
                    input.append(c.uppercaseChar())
                    updateLiveLine()
                }
            }
        }
        return true
    }

    private fun updateLiveLine() {
        val isPasswordPrompt = entryMode == MODE_LOGIN && (qon == 2 || qon == 3)
        val echoed = if (isPasswordPrompt) "*".repeat(input.length) else input.toString()
        _liveLine.value = pageContent + echoed
    }

    private fun clearScreen() {
        pageContent = ""
        _liveLine.value = ""
    }

    private fun handlePaging(delta: Int) {
        val choices = TerminalData.questions[qon - 1].choices
        if (choices.size <= PAGE_SIZE) return
        val next = (pageOffset + delta).coerceIn(0, choices.size - 1)
        if (next != pageOffset) {
            pageOffset = next
            isLocked = true
            scope.launch {
                clearScreen()
                showQuestion()
            }
        }
    }

    private suspend fun handleEnter() {
        isLocked = true
        val submitted = input.toString()

        when (entryMode) {
            MODE_LOGIN -> dispatchLogin(submitted)
            MODE_SHELL -> dispatchShell(submitted)
            MODE_APPLICATION -> dispatchApplication(submitted)
            MODE_NOTES -> dispatchNotes()
        }
    }

    // ---------------------------------------------------------------------
    // entryMode 0 — login / job-application flow (processInput0 + switchPage case 0)
    // ---------------------------------------------------------------------
    private suspend fun dispatchLogin(text: String) {
        var advance = false
        // Mirrors the original's `case 8: qon = 0` and `case 3: qon = 2` switch fallthroughs,
        // which Kotlin's `when` does not support - normalize qon first so the shared logic below
        // (the `0 ->` / `2 ->` arms) runs for both the original and the fallthrough-aliased qon.
        if (qon == 8) qon = 0
        if (qon == 3) qon = 2
        when (qon) {
            0 -> {
                advance = text == "LOGON" || text == "LOGIN" || text == "USER"
                if (text == "HELP" || text == "?") {
                    advance = true
                    qon = 7
                }
            }
            1 -> {
                advance = text.length > 2
                isCj = text == "CJOHNSON"
            }
            2 -> {
                if (isCj) {
                    advance = text == "TIER3"
                    isCj = advance
                    if (isCj) {
                        gladosHeader = "GLaDOS v1.07a (c) 1982 Aperture Science, Inc."
                        gladosPrompt = "^^ADMIN> "
                    }
                } else {
                    advance = text == "PORTAL" || text == "PORTALS"
                }
                // The page always advances - a wrong password redisplays this prompt with an
                // error (qon 3, aliased to 2 above) instead of blocking input; only a correct
                // password actually unlocks the shell.
                if (advance) {
                    entryMode = MODE_SHELL
                } else {
                    advance = true
                }
            }
            4 -> {
                if (text == "CONTINUE") advance = true
                if (text == "QUIT") {
                    qon = 0
                    advance = true
                    entryMode = MODE_SHELL
                }
            }
            5 -> {
                if (text == "CONTINUE") {
                    advance = true
                    qon = 0
                    entryMode = MODE_APPLICATION
                }
                if (text == "QUIT") {
                    qon = 0
                    advance = true
                    entryMode = MODE_SHELL
                }
            }
            9 -> {
                if (text == "THECAKEISALIE") {
                    entryMode = MODE_CAKE
                }
                advance = true
            }
            // Terminal "does not match" screen - there is no way back from here.
            10 -> advance = false
            else -> advance = true
        }

        finishTurn(advance)
    }

    // ---------------------------------------------------------------------
    // entryMode 1 — GLaDOS shell (processInput1)
    // ---------------------------------------------------------------------
    private suspend fun dispatchShell(rawText: String) {
        val text = rawText.trimStart()
        if (text.isEmpty()) {
            // The original returns immediately without even clearing the field.
            _liveLine.value = pageContent + input
            isLocked = false
            return
        }
        val args = text.split(" ")
        gladosMessage = ""
        when (args[0]) {
            "THECAKEISALIE" -> entryMode = MODE_CAKE

            "DIR", "CATALOG", "DIRECTORY", "LIST", "LS", "CAT" -> {
                gladosMessage =
                    if (isCj) {
                        "\nDISK VOLUME 255 [WORKSTATION CJOHNSON]\n\n" +
                            "     I  019  APPLY.EXE\n     I  004  NOTES.EXE\n\n" +
                            "2 FILE(S) IN 23 BLOCKS\n"
                    } else {
                        "\nDISK VOLUME 255 [NEW EMPLOYEE WORKSTATION]\n\n" +
                            "     I  019  APPLY.EXE\n\n1 FILE(S) IN 19 BLOCKS\n"
                    }
            }

            "IP" -> gladosMessage = " \n\nuid:$uid\n"

            "HELP", "LIB", "?" -> {
                gladosMessage =
                    if (isCj) {
                        " \n\nLIB\n     NOTES\n     APPEND\n     ATTRIB\n     COPY\n     DIR\n     ERASE\n" +
                            "     FORMAT\n     INTERROGATE\n     LIB\n     PLAY\n     RENAME\n     TAPEDISK"
                    } else {
                        " \n\nLIB\n     APPEND\n     ATTRIB\n     COPY\n     DIR\n     ERASE\n     FORMAT\n" +
                            "     INTERROGATE\n     LIB\n     PLAY\n     RENAME\n     TAPEDISK"
                    }
            }

            "LOGOUT", "BYE", "LOGOFF", "VALVE" -> {
                farewell("http://www.steampowered.com/")
                return
            }

            "APPEND", "ATTRIB", "COPY", "FORMAT", "ERASE", "RENAME" ->
                gladosMessage = "\n\nERROR 15 [Disk is write protected]"

            "PLAY" ->
                when {
                    args.size == 1 -> gladosMessage = "\n\nERROR 03 [What would you like to play?]"
                    args.getOrNull(1) == "PORTAL" -> {
                        farewell("http://www.youtube.com/watch?v=0h50K2NVJHM")
                        return
                    }
                }

            "INTERROGATE" ->
                gladosMessage =
                    when {
                        args.size == 1 -> "\n\nERROR 02 [Command requires at least one parameter]"
                        isCj -> "\n\nERROR 07 [Unknown Employee]"
                        else -> "\n\nERROR 01 [Illegal attempt to initiate disciplinary action]"
                    }

            "TAPEDISK" -> gladosMessage = "\n\nERROR 18 [User not authorized to transfer system tapes]"

            "NOTES", "NOTES.EXE" -> {
                if (isCj) {
                    qon = 50
                    entryMode = MODE_NOTES
                    notesPage = 1
                } else {
                    gladosMessage = "\n\nERROR 24 [File '${args[0]}' not found]"
                }
            }

            "APPLY", "APPLY.EXE" -> {
                qon = 3
                entryMode = MODE_LOGIN
            }

            else -> gladosMessage = "\n\nERROR 24 [File '${args[0]}' not found]"
        }

        showNextPage()
    }

    // ---------------------------------------------------------------------
    // entryMode 2 — job application questionnaire (processInput2)
    // ---------------------------------------------------------------------
    private suspend fun dispatchApplication(text: String) {
        // Matches an original off-by-one: once qon reaches the question count, Enter ends the
        // form immediately without validating (or submitting) whatever was typed for the last
        // question.
        if (qon >= TerminalData.questions.size) {
            qon = 8
            entryMode = MODE_LOGIN
            showNextPage()
            return
        }

        val question = TerminalData.questions[qon - 1]
        var advance = false
        if (question.type != QuestionType.TEXT) {
            val choice = text.toIntOrNull()
            if (choice != null && choice > 0 && choice <= question.choices.size) {
                advance = true
            }
        } else if (qon != 51) {
            advance = true
        }
        if (text == "QUIT") {
            qon = 0
            entryMode = MODE_SHELL
            advance = true
        }

        finishTurn(advance)
    }

    // ---------------------------------------------------------------------
    // entryMode 5 — NOTES.EXE reader (processInput5)
    // ---------------------------------------------------------------------
    private suspend fun dispatchNotes() {
        notesPage += 1
        if (notesPage > MAX_NOTES_PAGE) {
            entryMode = MODE_SHELL
        }
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
            input.clear()
            _liveLine.value = pageContent
            isLocked = false
        }
    }

    /** Mirrors switchPage(): clears the screen, resets pageOffset, then dispatches on the
     * (possibly just-changed) entryMode - exactly one clear-and-redraw per page transition. */
    private suspend fun showNextPage() {
        clearScreen()
        pageOffset = 0
        input.clear()
        when (entryMode) {
            MODE_LOGIN -> {
                qon++
                reveal(TerminalData.qar[qon], TerminalData.qdelay[qon])
            }
            MODE_SHELL -> reveal(gladosHeader + gladosMessage + gladosPrompt, GLADOS_SPEED)
            MODE_APPLICATION -> {
                qon++
                showQuestion()
            }
            MODE_BOSSKEY -> revealInstant(BOSSKEY_SPREADSHEET)
            MODE_CAKE -> {
                reveal(CAKE_MONOLOGUE_1, 0, unlockAfter = false)
                delay(2000) // stand-in for security02.flv playback
                reveal(CAKE_MONOLOGUE_2, 0)
            }
            MODE_NOTES -> reveal(TerminalData.cjHistory[notesPage - 1] + "\n\n[press ENTER to continue]", NOTES_SPEED)
        }
    }

    private suspend fun showQuestion() {
        val question = TerminalData.questions[qon - 1]
        val header = "Form FORMS-EN-2873-FORM - Page $qon\n\n${question.text}\n\n"
        if (question.type == QuestionType.TEXT) {
            reveal(header + "> ", 25)
            return
        }
        reveal(header, if (pageOffset > 0) 1 else 15, unlockAfter = false)

        val total = question.choices.size
        val padWidth = (total + 1).toString().length
        val pageEnd = minOf(pageOffset + PAGE_SIZE, total)
        val body =
            buildString {
                for (i in pageOffset until pageEnd) {
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
        entryMode = if (entryMode == MODE_CAKE) MODE_BOSSKEY else MODE_CAKE
        showNextPage()
    }

    private suspend fun farewell(url: String) {
        clearScreen()
        reveal("\n[Connection closed. This would open $url in your browser.]\n", GLADOS_SPEED)
        delay(400)
        _exitRequested.value = true
    }

    /**
     * Types [text] out one character at a time, appending onto whatever is already in
     * [pageContent] (call [clearScreen] first for a fresh page - a full page is usually built
     * from several chained `reveal`/`revealInstant` calls, e.g. a question's header followed by
     * its instantly-revealed choice list, matching how the original's `formatQuestion()` made
     * one `placeText()` call - which cleared - followed by manually placed choice text - which
     * didn't). delayMs <= 0 reveals instantly.
     *
     * `^` (the original's newline marker) becomes a line break and `@` (its UID placeholder)
     * is substituted, exactly as `placeText()` did in the source AS2 - this lets strings sourced
     * verbatim from [TerminalData] be passed straight through unmodified. Lines are also
     * word-wrapped to [WRAP_WIDTH]: the original auto-wrapped unbroken text past a pixel-width
     * threshold too, and without it a single long line soft-wraps in the real terminal while
     * still animating, which can desync a UI's redraw bookkeeping (observed with Mosaic) and
     * leave stray duplicate rows behind.
     */
    private suspend fun reveal(
        text: String,
        delayMs: Int,
        unlockAfter: Boolean = true,
    ) {
        val logicalLines = text.replace("@", "[$uid]").replace("^", "\n").split("\n")
        val lines = logicalLines.flatMap { wordWrap(it, WRAP_WIDTH) }
        for ((index, line) in lines.withIndex()) {
            val base = pageContent
            if (delayMs > 0) {
                val sb = StringBuilder()
                for (ch in line) {
                    sb.append(ch)
                    _liveLine.value = base + sb
                    delay(delayMs.toLong())
                }
                pageContent = base + sb
            } else {
                pageContent = base + line
                _liveLine.value = pageContent
            }
            if (index != lines.lastIndex) {
                pageContent += "\n"
            }
        }
        _liveLine.value = pageContent
        if (unlockAfter) {
            isLocked = false
        }
    }

    /** Reveals every line instantly, appending onto [pageContent] (see [reveal]). */
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

    companion object {
        private const val MODE_LOGIN = 0
        private const val MODE_SHELL = 1
        private const val MODE_APPLICATION = 2
        private const val MODE_BOSSKEY = 3
        private const val MODE_CAKE = 4
        private const val MODE_NOTES = 5

        private const val GLADOS_SPEED = 7
        private const val NOTES_SPEED = 3
        private const val MAX_INPUT_LENGTH = 65
        private const val PAGE_SIZE = 104
        private const val WRAP_WIDTH = 100
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

        private val CAKE_MONOLOGUE_1 =
            listOf(
                ">",
                ">>>&!>>",
                "When was the last time you left the building?",
                "Has anybody left the building lately?",
                "I don't know why we're in lockdown. I don't know who's in charge.",
                "I did find out a few things, like these terminals don't have to",
                "tap out characters one at a time. And while we're all working",
                "on twenty year old equipment, somehow they can afford to build",
                "an 'Enrichment Center'. Check out this security feed.",
                "Whatever the hell a 'relaxation vault' is, it",
                "doesn't have any doors.",
                "",
                "[security02.flv would play here]",
                "",
            ).joinToString("\n")

        private val CAKE_MONOLOGUE_2 =
            listOf(
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
