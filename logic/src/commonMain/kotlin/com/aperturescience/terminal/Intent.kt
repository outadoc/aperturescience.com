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
     * A single raw key, as reported by the host platform (e.g. `KeyboardEvent.key`).
     */
    data class KeyPressed(
        val key: String,
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
     * Batch equivalent of PageUp ([delta] negative)/PageDown ([delta] positive) on Q21.
     */
    data class Paged(
        val delta: Int,
    ) : Intent

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
