package com.aperturescience.terminal

import com.aperturescience.terminal.data.TerminalData

/**
 * Where the user currently is in a [TerminalEngine] session. Not `@Serializable` - hosts that
 * need to persist it define their own mirror (see `ui-minitel`'s `MinitelMode`).
 */
sealed interface Mode {
    /**
     * The login / job-application intro flow (processInput0 + switchPage case 0).
     */
    sealed interface Login : Mode {
        /**
         * The bare `"> "` prompt - also what a HELP/`?` detour returns to.
         */
        data object Initial : Login

        /**
         * `"Username> "`.
         */
        data object Username : Login

        /**
         * `"Password> "` (or, if [isRetry], the error variant) - looping here forever is
         * expected on repeated wrong passwords.
         */
        data class Password(
            val isRetry: Boolean,
        ) : Login

        /**
         * The "crisis response team" joke message, reached from [Initial] via HELP/`?`.
         * Whatever you press next is processed exactly like [Initial].
         */
        data object Help : Login

        /**
         * Job-application intro banner, reached via the shell's APPLY command. CONTINUE ->
         * [ApplicationUidDisplay], QUIT -> [Shell].
         */
        data object ApplicationIntro : Login

        /**
         * Shows the synthesized UID, CONTINUE -> [Application] (question 1), QUIT ->
         * [Shell].
         */
        data object ApplicationUidDisplay : Login

        /**
         * "Enter your 64 digit UIN(+L)" prompt after the last question. THECAKEISALIE ->
         * [Cake], anything else -> [Terminal].
         */
        data object UinEntry : Login

        /**
         * "The entered UIN(+L) does not match" dead end - no input does anything from here.
         */
        data object Terminal : Login
    }

    /**
     * The GLaDOS shell - `"B:\>"`/`"ADMIN>"` depending on `isAdmin`. [message] is the last
     * command's output, reset to `""` at the start of every new command.
     */
    data class Shell(
        val message: String = "",
    ) : Mode

    /**
     * [questionNumber] is 1-indexed
     */
    data class Application(
        val questionNumber: Int,
    ) : Mode

    /**
     * NOTES.EXE (processInput5), admin-only - [page] is 1-indexed, matching
     * [TerminalData.notesHistoryPages].
     */
    data class Notes(
        val page: Int,
    ) : Mode

    /**
     * THECAKEISALIE easter egg - toggles with [BossKey] on any key, forever; no scripted way
     * back to [Shell].
     */
    data object Cake : Mode

    /**
     * The disguised fake-spreadsheet half of the CAKE/BOSSKEY toggle.
     */
    data object BossKey : Mode
}
