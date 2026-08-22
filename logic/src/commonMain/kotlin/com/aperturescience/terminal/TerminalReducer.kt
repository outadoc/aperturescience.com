package com.aperturescience.terminal

import com.aperturescience.terminal.TerminalReducer.gladosPrompt
import com.aperturescience.terminal.TerminalReducer.reduceApplication
import com.aperturescience.terminal.TerminalReducer.reduceKeyPressed
import com.aperturescience.terminal.TerminalReducer.reduceLogin
import com.aperturescience.terminal.TerminalReducer.reduceShell
import com.aperturescience.terminal.TerminalReducer.showNextPage
import com.aperturescience.terminal.data.QuestionType
import com.aperturescience.terminal.data.TerminalData

/**
 * Default wrap width, matching the original's pixel-width auto-wrap threshold.
 */
const val WRAP_WIDTH = 100

private const val GLADOS_SPEED = 7
private const val NOTES_SPEED = 3
private const val MAX_NOTES_PAGE = 4
private const val STORE_URL = "https://store.steampowered.com/app/400/Portal"

/**
 * The pure core of `TerminalEngine`: every mode-transition/command-dispatch rule from the
 * original `DoAction.as` state machine, as pure functions of `state` returning a [Reduction].
 */
object TerminalReducer {
    fun reduce(
        state: EngineState,
        intent: Intent,
    ): Reduction =
        when (intent) {
            Intent.Boot -> {
                showNextPage(state)
            }

            is Intent.KeyPressed -> {
                reduceKeyPressed(
                    state = state,
                    key = intent.key,
                )
            }

            is Intent.LineSubmitted -> {
                reduceLineSubmitted(
                    state = state,
                    line = intent.line,
                )
            }

            Intent.Advanced -> {
                reduceAdvanced(state)
            }

            is Intent.ViewportResized -> {
                Reduction(
                    if (intent.columns > 0) {
                        state.copy(
                            wrapWidth = intent.columns,
                        )
                    } else {
                        state
                    },
                )
            }

            is Intent.CharacterRevealed -> {
                reduceCharacterRevealed(
                    state = state,
                    char = intent.char,
                )
            }

            Intent.Unlocked -> {
                Reduction(
                    state.copy(
                        isLocked = false,
                    ),
                )
            }

            Intent.ExitRequested -> {
                Reduction(
                    state.copy(
                        exitRequested = true,
                    ),
                )
            }
        }

    private fun reduceCharacterRevealed(
        state: EngineState,
        char: Char,
    ): Reduction =
        when {
            char in START_CHAR_TO_TAG -> {
                Reduction(
                    state.copy(
                        pendingAnnotationStart = state.pageContent.length,
                        pendingAnnotationTag = START_CHAR_TO_TAG.getValue(char),
                    ),
                )
            }

            char == BLINK_END -> {
                val start = state.pendingAnnotationStart
                val tag = state.pendingAnnotationTag
                Reduction(
                    if (start == null || tag == null) {
                        state
                    } else {
                        state.copy(
                            annotations =
                                state.annotations +
                                    TextAnnotation(
                                        tag = tag,
                                        range = start until state.pageContent.length,
                                    ),
                            pendingAnnotationStart = null,
                            pendingAnnotationTag = null,
                        )
                    },
                )
            }

            else -> {
                Reduction(
                    state.copy(
                        pageContent = state.pageContent + char,
                    ),
                )
            }
        }

    private fun reduceKeyPressed(
        state: EngineState,
        key: String,
    ): Reduction {
        if (state.isLocked) {
            return Reduction(state)
        }

        val namedKey = NamedKey.from(key)

        // Cake/bosskey: any accepted key toggles between the two screens, no line input at all.
        if (state.mode == Mode.Cake || state.mode == Mode.BossKey) {
            return if (isAcceptedRawKey(namedKey, key)) {
                reduceAdvanced(state)
            } else {
                Reduction(state)
            }
        }

        // NOTES.EXE forces every keystroke to behave like Enter.
        if (state.mode is Mode.Notes) {
            return if (isAcceptedRawKey(namedKey, key)) {
                commitEnter(state)
            } else {
                Reduction(state)
            }
        }

        return when {
            namedKey == NamedKey.ENTER -> {
                commitEnter(state)
            }

            namedKey == NamedKey.BACKSPACE -> {
                if (state.input.isNotEmpty()) {
                    Reduction(
                        state.copy(
                            input = state.input.dropLast(1),
                        ),
                    )
                } else {
                    Reduction(state)
                }
            }

            key.length == 1 -> {
                val c = key[0]
                if (isAcceptedChar(c) && state.input.length < MAX_INPUT_LENGTH) {
                    Reduction(
                        state.copy(
                            input = state.input + c.uppercaseChar(),
                        ),
                    )
                } else {
                    Reduction(state)
                }
            }

            else -> {
                Reduction(state)
            }
        }
    }

    /**
     * Whole-line equivalent of [reduceKeyPressed]'s per-character filtering.
     */
    private fun reduceLineSubmitted(
        state: EngineState,
        line: String,
    ): Reduction {
        val filtered =
            buildString {
                for (c in line) {
                    if (isAcceptedChar(c) && length < MAX_INPUT_LENGTH) {
                        append(c.uppercaseChar())
                    }
                }
            }

        return commitEnter(
            state.copy(
                input = filtered,
            ),
        )
    }

    /**
     * The "any accepted key" equivalent [reduceKeyPressed] uses for NOTES/CAKE/BOSSKEY.
     */
    private fun reduceAdvanced(state: EngineState): Reduction =
        when (state.mode) {
            Mode.Cake,
            Mode.BossKey,
            -> {
                toggleCakeBosskey(state)
            }

            else -> {
                commitEnter(state)
            }
        }

    private fun commitEnter(state: EngineState): Reduction {
        val locked = state.copy(isLocked = true)
        val submitted = locked.input
        return when (val current = locked.mode) {
            is Mode.Login -> {
                reduceLogin(
                    state = locked,
                    current = current,
                    text = submitted,
                )
            }

            is Mode.Shell -> {
                reduceShell(
                    state = locked,
                    rawText = submitted,
                )
            }

            is Mode.Application -> {
                reduceApplication(
                    state = locked,
                    current = current,
                    text = submitted,
                )
            }

            is Mode.Notes -> {
                reduceNotes(
                    state = locked,
                    current = current,
                )
            }

            // Cake/bosskey's any-key handling goes through reduceAdvanced/toggleCakeBosskey, not here.
            Mode.Cake,
            Mode.BossKey,
            -> {
                Reduction(locked)
            }
        }
    }

    /**
     * [Mode.Login] - login / job-application flow (processInput0 + switchPage case 0).
     */
    private fun reduceLogin(
        state: EngineState,
        current: Mode.Login,
        text: String,
    ): Reduction {
        var advance = false
        var next = state
        when (current) {
            Mode.Login.Initial -> {
                advance = text == Command.LOGON || text == Command.LOGIN || text == Command.USER
                if (advance) {
                    next =
                        next.copy(
                            mode = Mode.Login.Username,
                        )
                }

                if (text == Command.HELP || text == Command.QUESTION_MARK) {
                    advance = true
                    next =
                        next.copy(
                            mode = Mode.Login.Help,
                        )
                }
            }

            // Reading help then typing anything follows the same rules as the initial prompt.
            Mode.Login.Help -> {
                advance = text == Command.LOGON || text == Command.LOGIN || text == Command.USER
                if (advance) {
                    next =
                        next.copy(
                            mode = Mode.Login.Username,
                        )
                }

                if (text == Command.HELP || text == Command.QUESTION_MARK) {
                    advance = true
                    next =
                        next.copy(
                            mode = Mode.Login.Help,
                        )
                }
            }

            Mode.Login.Username -> {
                advance = text.length > 2
                next =
                    next.copy(
                        isAdmin = text == Command.CJOHNSON,
                    )

                if (advance) {
                    next =
                        next.copy(
                            mode =
                                Mode.Login.Password(
                                    isRetry = false,
                                ),
                        )
                }
            }

            is Mode.Login.Password -> {
                if (next.isAdmin) {
                    advance = text == Command.TIER3
                    next =
                        next.copy(
                            isAdmin = advance,
                        )
                } else {
                    advance = text == Command.PORTAL || text == Command.PORTALS
                }

                // Always advances - a wrong password redisplays with an error, not a block.
                next =
                    next.copy(
                        mode =
                            if (advance) {
                                Mode.Shell()
                            } else {
                                advance = true
                                Mode.Login.Password(
                                    isRetry = true,
                                )
                            },
                    )
            }

            Mode.Login.ApplicationIntro -> {
                if (text == Command.CONTINUE) {
                    advance = true
                    next =
                        next.copy(
                            mode = Mode.Login.ApplicationUidDisplay,
                        )
                }
                if (text == Command.QUIT) {
                    advance = true
                    next =
                        next.copy(
                            mode = Mode.Shell(),
                        )
                }
            }

            Mode.Login.ApplicationUidDisplay -> {
                if (text == Command.CONTINUE) {
                    advance = true
                    next =
                        next.copy(
                            mode =
                                Mode.Application(
                                    questionNumber = 1,
                                ),
                        )
                }
                if (text == Command.QUIT) {
                    advance = true
                    next =
                        next.copy(
                            mode = Mode.Shell(),
                        )
                }
            }

            Mode.Login.UinEntry -> {
                next =
                    next.copy(
                        mode =
                            if (text == Command.THECAKEISALIE) {
                                Mode.Cake
                            } else {
                                Mode.Login.Terminal
                            },
                    )
                advance = true
            }

            // Dead end - there is no way back from here.
            Mode.Login.Terminal -> {
                advance = false
            }
        }

        return finishTurn(next, advance)
    }

    /**
     * [Mode.Shell] - GLaDOS shell (processInput1).
     */
    private fun reduceShell(
        state: EngineState,
        rawText: String,
    ): Reduction {
        val text = rawText.trimStart()
        if (text.isEmpty()) {
            // Matches the original: returns immediately without even clearing the field.
            return Reduction(
                state.copy(
                    isLocked = false,
                ),
            )
        }

        val args: List<String> = text.split(" ")
        var message = ""
        var next = state
        when (args[0]) {
            Command.THECAKEISALIE -> {
                next =
                    state.copy(
                        mode = Mode.Cake,
                    )
            }

            Command.DIR,
            Command.CATALOG,
            Command.DIRECTORY,
            Command.LIST,
            Command.LS,
            Command.CAT,
            -> {
                message =
                    if (state.isAdmin) {
                        """
                        
                        
                        DISK VOLUME 255 [WORKSTATION CJOHNSON]
                        
                             I  019  APPLY.EXE
                             I  004  NOTES.EXE
                        
                        2 FILE(S) IN 23 BLOCKS
                        
                        """.trimIndent()
                    } else {
                        """
                        
                        
                        DISK VOLUME 255 [NEW EMPLOYEE WORKSTATION]
                        
                             I  019  APPLY.EXE
                        
                        1 FILE(S) IN 19 BLOCKS
                        
                        """.trimIndent()
                    }
            }

            Command.IP -> {
                message =
                    buildString {
                        appendLine()
                        appendLine()
                        append(" uid:")
                        append(state.uid)
                        appendLine()
                    }
            }

            Command.HELP,
            Command.LIB,
            Command.QUESTION_MARK,
            -> {
                message =
                    if (state.isAdmin) {
                        """
                        
                        
                        LIB
                             NOTES
                             APPEND
                             ATTRIB
                             COPY
                             DIR
                             ERASE
                             FORMAT
                             INTERROGATE
                             LIB
                             PLAY
                             RENAME
                             TAPEDISK
                        """.trimIndent()
                    } else {
                        """
                        
                        
                        LIB
                             APPEND
                             ATTRIB
                             COPY
                             DIR
                             ERASE
                             FORMAT
                             INTERROGATE
                             LIB
                             PLAY
                             RENAME
                             TAPEDISK
                        """.trimIndent()
                    }
            }

            Command.LOGOUT,
            Command.BYE,
            Command.LOGOFF,
            Command.VALVE,
            -> {
                return farewell(
                    state = state,
                    content = STORE_URL,
                    easterEgg = EasterEgg.STORE,
                )
            }

            Command.APPEND,
            Command.ATTRIB,
            Command.COPY,
            Command.FORMAT,
            Command.ERASE,
            Command.RENAME,
            -> {
                message =
                    buildString {
                        appendLine()
                        appendLine()
                        append("ERROR 15 [Disk is write protected]")
                    }
            }

            Command.PLAY -> {
                when {
                    args.size == 1 -> {
                        message =
                            buildString {
                                appendLine()
                                appendLine()
                                append("ERROR 03 [What would you like to play?]")
                            }
                    }

                    args.getOrNull(1) == Command.PORTAL -> {
                        return farewell(
                            state = state,
                            content = "[ERROR: TRAILER NOT FOUND]",
                            easterEgg = EasterEgg.TRAILER,
                        )
                    }
                }
            }

            Command.INTERROGATE -> {
                message =
                    buildString {
                        appendLine()
                        appendLine()
                        append(
                            when {
                                args.size == 1 -> {
                                    "ERROR 02 [Command requires at least one parameter]"
                                }

                                state.isAdmin -> {
                                    "ERROR 07 [Unknown Employee]"
                                }

                                else -> {
                                    "ERROR 01 [Illegal attempt to initiate disciplinary action]"
                                }
                            },
                        )
                    }
            }

            Command.TAPEDISK -> {
                message =
                    buildString {
                        appendLine()
                        appendLine()
                        append("ERROR 18 [User not authorized to transfer system tapes]")
                    }
            }

            Command.NOTES,
            Command.NOTES_EXE,
            -> {
                if (state.isAdmin) {
                    next =
                        state.copy(
                            mode = Mode.Notes(page = 1),
                        )
                } else {
                    message =
                        buildString {
                            appendLine()
                            appendLine()
                            append("ERROR 24 [File '")
                            append(args[0])
                            append("' not found]")
                        }
                }
            }

            Command.APPLY,
            Command.APPLY_EXE,
            -> {
                next =
                    state.copy(
                        mode = Mode.Login.ApplicationIntro,
                    )
            }

            else -> {
                message =
                    buildString {
                        appendLine()
                        appendLine()
                        append("ERROR 24 [File '")
                        append(args[0])
                        append("' not found]")
                    }
            }
        }

        // Skip if a branch above already switched screens (Cake/Notes/Apply).
        if (next.mode is Mode.Shell) {
            next =
                next.copy(
                    mode = Mode.Shell(message = message),
                )
        }

        return showNextPage(next)
    }

    /**
     * [Mode.Application] - job application questionnaire (processInput2).
     */
    private fun reduceApplication(
        state: EngineState,
        current: Mode.Application,
        text: String,
    ): Reduction {
        // Off-by-one: reaching the question count ends the form without validating the last answer.
        if (current.questionNumber >= TerminalData.questions.size) {
            return showNextPage(
                state.copy(
                    mode = Mode.Login.UinEntry,
                ),
            )
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

        val nextMode =
            if (text == Command.QUIT) {
                advance = true
                Mode.Shell()
            } else if (advance) {
                current.copy(
                    questionNumber = current.questionNumber + 1,
                )
            } else {
                current
            }

        return finishTurn(
            state =
                state.copy(
                    mode = nextMode,
                ),
            advance = advance,
        )
    }

    /**
     * [Mode.Notes] - NOTES.EXE reader (processInput5).
     */
    private fun reduceNotes(
        state: EngineState,
        current: Mode.Notes,
    ): Reduction {
        val nextPage = current.page + 1

        return showNextPage(
            state.copy(
                mode =
                    if (nextPage > MAX_NOTES_PAGE) {
                        Mode.Shell()
                    } else {
                        Mode.Notes(page = nextPage)
                    },
            ),
        )
    }

    private fun toggleCakeBosskey(state: EngineState): Reduction =
        showNextPage(
            state.copy(
                mode =
                    if (state.mode == Mode.Cake) {
                        Mode.BossKey
                    } else {
                        Mode.Cake
                    },
            ),
        )

    /**
     * Ends the session in place of the original's browser navigation (LOGOUT/PLAY PORTAL), which
     * a terminal can't perform - reveals [content] tagged with [easterEgg] so a frontend that
     * *can* act on it (e.g. `ui-web`) can tell which one fired. Callers decide the exact wording
     * themselves (a bracketed in-universe error for TRAILER, a bare real URL for STORE) - this
     * function only owns the reveal/tag/exit mechanics, not the content.
     */
    private fun farewell(
        state: EngineState,
        content: String,
        easterEgg: EasterEgg,
    ): Reduction {
        val cleared =
            state.copy(
                pageContent = "",
                annotations = emptyList(),
                input = "",
            )

        return Reduction(
            state = cleared,
            effects =
                listOf(
                    revealEffect(
                        state = cleared,
                        text =
                            buildString {
                                appendLine()
                                append(taggedSpan(easterEgg, content))
                                appendLine()
                            },
                        delayMs = GLADOS_SPEED,
                        unlock = true,
                    ),
                    Effect.Wait(
                        ms = 400,
                        thenDispatch = Intent.ExitRequested,
                    ),
                ),
        )
    }

    /**
     * Accept/reject branch at the bottom of reduceLogin/reduceApplication.
     */
    private fun finishTurn(
        state: EngineState,
        advance: Boolean,
    ): Reduction =
        if (advance) {
            showNextPage(state)
        } else {
            Reduction(
                state.copy(
                    input = "",
                    isLocked = false,
                ),
            )
        }

    /**
     * Clears and redraws for [state]'s mode - already the exact target screen by the time this
     * runs, so there's no index math left to do here (mirrors switchPage()).
     */
    private fun showNextPage(state: EngineState): Reduction {
        val cleared =
            state.copy(
                pageContent = "",
                annotations = emptyList(),
                input = "",
            )

        val effects: List<Effect> =
            when (val current = cleared.mode) {
                is Mode.Login -> {
                    listOf(
                        revealEffect(
                            state = cleared,
                            text = TerminalData.loginFlowScreens[current.loginFlowScreenIndex],
                            delayMs = TerminalData.loginFlowScreenDelays[current.loginFlowScreenIndex],
                            unlock = true,
                        ),
                    )
                }

                is Mode.Shell -> {
                    listOf(
                        revealEffect(
                            state = cleared,
                            text =
                                buildString {
                                    append(gladosHeader(cleared))
                                    append(current.message)
                                    append(gladosPrompt(cleared))
                                },
                            delayMs = GLADOS_SPEED,
                            unlock = true,
                        ),
                    )
                }

                is Mode.Application -> {
                    return showQuestion(cleared)
                }

                Mode.BossKey -> {
                    listOf(
                        revealEffect(
                            state = cleared,
                            text = BOSSKEY_SPREADSHEET,
                            delayMs = 0,
                            unlock = true,
                        ),
                    )
                }

                Mode.Cake -> {
                    listOf(
                        revealEffect(
                            state = cleared,
                            text = CAKE_MONOLOGUE_1,
                            delayMs = 0,
                            unlock = false,
                        ),
                        Effect.Wait(2000),
                        revealEffect(
                            state = cleared,
                            text = CAKE_MONOLOGUE_2,
                            delayMs = 0,
                            unlock = true,
                        ),
                    )
                }

                // notesHistoryPages already ends in "[MORE]"/"[END]" - nothing appended here.
                is Mode.Notes -> {
                    listOf(
                        revealEffect(
                            state = cleared,
                            text = TerminalData.notesHistoryPages[current.page - 1],
                            delayMs = NOTES_SPEED,
                            unlock = true,
                        ),
                    )
                }
            }

        return Reduction(
            state = cleared,
            effects = effects,
        )
    }

    private fun showQuestion(state: EngineState): Reduction {
        val current = state.mode as Mode.Application
        val question = TerminalData.questions[current.questionNumber - 1]
        val header =
            buildString {
                append("Form FORMS-EN-2873-FORM - Page ")
                append(current.questionNumber)
                appendLine()
                appendLine()
                append(question.text)
                appendLine()
                appendLine()
            }

        if (question.type == QuestionType.TEXT) {
            return Reduction(
                state,
                listOf(
                    revealEffect(
                        state = state,
                        text = "$header> ",
                        delayMs = 25,
                        unlock = true,
                    ),
                ),
            )
        }

        val padWidth = (question.choices.size + 1).toString().length
        val body =
            buildString {
                question.choices.forEachIndexed { index, choice ->
                    append(
                        (index + 1)
                            .toString()
                            .padStart(padWidth, '0'),
                    )
                    append("] ")
                    append(choice)
                    appendLine()
                }
            }

        val prompt = "> "

        return Reduction(
            state,
            listOf(
                revealEffect(
                    state = state,
                    text = header,
                    delayMs = 15,
                    unlock = false,
                ),
                revealEffect(
                    state = state,
                    text = body + prompt,
                    delayMs = 0,
                    unlock = true,
                ),
            ),
        )
    }

    private fun revealEffect(
        state: EngineState,
        text: String,
        delayMs: Int,
        unlock: Boolean,
    ): Effect.RevealCharacters =
        Effect.RevealCharacters(
            chars =
                buildRevealChars(
                    text = text,
                    uid = state.uid,
                    wrapWidth = state.wrapWidth,
                ),
            delayMs = delayMs,
            thenDispatch =
                if (unlock) {
                    Intent.Unlocked
                } else {
                    null
                },
        )

    /**
     * Derived from [EngineState.isAdmin], not stored - a regular login never reassigns this
     * or [gladosPrompt].
     */
    private fun gladosHeader(state: EngineState): String =
        if (state.isAdmin) {
            "GLaDOS v1.07a (c) 1982 Aperture Science, Inc."
        } else {
            "GLaDOS v1.07 (c) 1982 Aperture Science, Inc."
        }

    private fun gladosPrompt(state: EngineState): String =
        if (state.isAdmin) {
            "^^ADMIN> "
        } else {
            "^^B:\\> "
        }

    /**
     * Which `loginFlowScreens`/`loginFlowScreenDelays` entry each [Mode.Login] state reveals -
     * a rendering detail local to [showNextPage].
     */
    private val Mode.Login.loginFlowScreenIndex: Int
        get() =
            when (this) {
                is Mode.Login.Initial -> 0
                is Mode.Login.Username -> 1
                is Mode.Login.Password -> if (isRetry) 3 else 2
                is Mode.Login.ApplicationIntro -> 4
                is Mode.Login.ApplicationUidDisplay -> 5
                is Mode.Login.Help -> 8
                is Mode.Login.UinEntry -> 9
                is Mode.Login.Terminal -> 10
            }

    /**
     * Every keyword [reduceLogin]/[reduceShell]/[reduceApplication] recognize, so aliases of the
     * same command (e.g. [Command.DIR]/[Command.CATALOG]/[Command.DIRECTORY]) are visibly related, not scattered literals.
     */
    private object Command {
        const val LOGON = "LOGON"
        const val LOGIN = "LOGIN"
        const val USER = "USER"
        const val HELP = "HELP"
        const val QUESTION_MARK = "?"
        const val LIB = "LIB"
        const val CJOHNSON = "CJOHNSON"
        const val TIER3 = "TIER3"
        const val PORTAL = "PORTAL"
        const val PORTALS = "PORTALS"
        const val CONTINUE = "CONTINUE"
        const val QUIT = "QUIT"
        const val THECAKEISALIE = "THECAKEISALIE"
        const val DIR = "DIR"
        const val CATALOG = "CATALOG"
        const val DIRECTORY = "DIRECTORY"
        const val LIST = "LIST"
        const val LS = "LS"
        const val CAT = "CAT"
        const val IP = "IP"
        const val LOGOUT = "LOGOUT"
        const val BYE = "BYE"
        const val LOGOFF = "LOGOFF"
        const val VALVE = "VALVE"
        const val APPEND = "APPEND"
        const val ATTRIB = "ATTRIB"
        const val COPY = "COPY"
        const val FORMAT = "FORMAT"
        const val ERASE = "ERASE"
        const val RENAME = "RENAME"
        const val PLAY = "PLAY"
        const val INTERROGATE = "INTERROGATE"
        const val TAPEDISK = "TAPEDISK"
        const val NOTES = "NOTES"
        const val NOTES_EXE = "NOTES.EXE"
        const val APPLY = "APPLY"
        const val APPLY_EXE = "APPLY.EXE"
    }

    /**
     * Reconstructed as real sentences/paragraphs, not the original's per-line array split (an
     * artifact of the Flash canvas' narrower width, not separate thoughts).
     */
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
            taggedSpan(EasterEgg.SECURITY_VIDEO, "[ERROR: SECURITY02.FLV NOT FOUND]"),
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
