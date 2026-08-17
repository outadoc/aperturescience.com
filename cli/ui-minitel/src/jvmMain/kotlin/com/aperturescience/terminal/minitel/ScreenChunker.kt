package com.aperturescience.terminal.minitel

/**
 * Minitel screens are a fixed 40x24 grid (line 0 is the status line minipavi-kotlin's
 * `VideotexBuilder` reserves separately) with no native scrolling, and minipavi-kotlin gives
 * exactly one Vidéotex frame per HTTP call - so unlike `ui-terminal`'s TTY or `ui-web`'s scrolling
 * `<pre>`, this adapter has to slice a `TerminalEngine` turn's output into screen-fuls itself,
 * advancing on the next user action ([TurnHandler] owns when that "next action" arrives). This is
 * a separate, adapter-only concern from `TerminalEngine`'s own Q21-specific >104-choice pagination
 * (`TerminalEngine.page`/`handlePaging`) - one full engine-level page can itself be many chunks
 * tall here.
 */
object ScreenChunker {
    /** Matches `VideotexBuilder.screenWidth` - also what this adapter passes to
     * `TerminalEngine.setViewportWidth` so `reveal()`'s own word-wrap never exceeds it. */
    const val COLUMNS = 40

    /** Matches `VideotexBuilder.screenHeight - 1` (line 0 is the status line). */
    const val ROWS_PER_SCREEN = 24

    /** Splits [fullText] into ≤[rowsPerScreen]-line chunks. Always returns at least one chunk
     * (possibly empty), so callers never need to special-case blank content. */
    fun chunk(
        fullText: String,
        rowsPerScreen: Int = ROWS_PER_SCREEN,
    ): List<List<String>> {
        val lines = fullText.split("\n")
        val chunks = lines.chunked(rowsPerScreen)
        return chunks.ifEmpty { listOf(emptyList()) }
    }
}
