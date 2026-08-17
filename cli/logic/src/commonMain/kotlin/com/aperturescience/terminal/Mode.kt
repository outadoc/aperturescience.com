package com.aperturescience.terminal

import com.aperturescience.terminal.data.TerminalData

/**
 * "Where the user currently is" in a [TerminalEngine] session, replacing the original AS2 port's
 * `entryMode`/`qon` int pair - `entryMode` picked one of six screens, and `qon` meant something
 * different (and sometimes nothing at all - entering NOTES.EXE used to set it to a sentinel `50`
 * purely because the original AS2 did, and nothing ever read it back) depending on which screen
 * was active. That made illegal combinations representable (nothing stopped `qon` from holding a
 * question number while `entryMode == MODE_LOGIN`) and pushed the original's "set qon to N, let
 * `switchPage()`'s `qon++` land on the actually-displayed N+1" indirection into every login
 * transition. Here, each state carries only the data it needs, and a transition just assigns the
 * literal target state directly.
 *
 * Public and deliberately *not* `@Serializable` - `logic` stays free of any serialization
 * framework dependency (see `EngineState`'s doc and AGENTS.md). A host that needs to persist a
 * [Mode] across calls that don't keep a live [TerminalEngine] around (e.g. `ui-minitel`'s
 * stateless HTTP request/response cycle) defines its own serializable mirror and converts at the
 * boundary - see `ui-minitel`'s `MinitelMode`.
 */
sealed interface Mode {
    /** The login / job-application intro flow (processInput0 + switchPage case 0). */
    sealed interface Login : Mode {
        /** The bare `"> "` prompt - also what a HELP/`?` detour returns to. */
        data object Initial : Login

        /** `"Username> "`. */
        data object Username : Login

        /** `"Password> "` (or, if [isRetry], the `"ERROR 07 [Incorrect Password]"` variant of
         * it) - looping here forever is the expected outcome of repeatedly typing the wrong
         * password. */
        data class Password(
            val isRetry: Boolean,
        ) : Login

        /** The "crisis response team" joke message, reached from [Initial] via HELP/`?`.
         * Whatever you press next is processed exactly like [Initial]. */
        data object Help : Login

        /** Job-application intro banner, reached via the shell's APPLY command. CONTINUE ->
         * [ApplicationUidDisplay], QUIT -> [Shell]. */
        data object ApplicationIntro : Login

        /** Shows the synthesized UID, CONTINUE -> [Application] (question 1), QUIT ->
         * [Shell]. */
        data object ApplicationUidDisplay : Login

        /** "Enter your 64 digit UIN(+L)" prompt after the last question. THECAKEISALIE ->
         * [Cake], anything else -> [Terminal]. */
        data object UinEntry : Login

        /** "The entered UIN(+L) does not match" dead end - no input does anything from here. */
        data object Terminal : Login
    }

    /** The GLaDOS shell (processInput1) - `"B:\>"`/`"ADMIN>"` depending on `isAdmin`. [message] is
     * the last command's output/error text, shown between the header and prompt - reset to `""`
     * at the start of every new command. */
    data class Shell(
        val message: String = "",
    ) : Mode

    /** The 50-question job application wizard (processInput2). [questionNumber] is 1-indexed,
     * matching [TerminalData.questions]; [pageOffset] is Q21's own >104-choice pagination
     * cursor, always reset to 0 on a new question. */
    data class Application(
        val questionNumber: Int,
        val pageOffset: Int = 0,
    ) : Mode

    /** NOTES.EXE (processInput5), admin-only - [page] is 1-indexed, matching
     * [TerminalData.cjHistory]. */
    data class Notes(
        val page: Int,
    ) : Mode

    /** THECAKEISALIE easter egg - toggles with [BossKey] on any key, forever; no scripted way
     * back to [Shell]. */
    data object Cake : Mode

    /** The disguised fake-spreadsheet half of the CAKE/BOSSKEY toggle. */
    data object BossKey : Mode
}
