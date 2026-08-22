package com.aperturescience.terminal

/**
 * The engine's entire input surface. Every state change goes through
 * `TerminalReducer.reduce(state, intent)` for some [Intent] - no exceptions.
 */
sealed interface Intent {
    //region Host-facing (public API)

    /**
     * Boots a freshly-constructed engine, revealing the bare login prompt.
     */
    data object Boot : Intent

    /**
     * A single raw key, as reported by the host platform.
     */
    data class KeyPressed(
        val key: Key,
    ) : Intent

    /**
     * Batch equivalent of typing [line] character-by-character then pressing Enter.
     */
    data class LineSubmitted(
        val line: String,
    ) : Intent

    /**
     * Batch equivalent of "any accepted key" (NOTES.EXE paging, CAKE/BOSSKEY toggling).
     */
    data object Advanced : Intent

    /**
     * Reported viewport width, in columns, that future reveals should wrap to.
     */
    data class ViewportResized(
        val columns: Int,
    ) : Intent
    //endregion

    //region Runner-only - dispatched exclusively by TerminalEngine's effect runner

    /**
     * One already-word-wrapped, marker-substituted character from a reveal in progress.
     */
    data class CharacterRevealed(
        val char: Char,
    ) : Intent

    /**
     * Dispatched once a reveal effect that requested unlocking finishes.
     */
    data object Unlocked : Intent

    /**
     * Dispatched once the post-farewell pause finishes.
     */
    data object ExitRequested : Intent
    //endregion
}
