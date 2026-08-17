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
    /** Matches `VideotexBuilder.screenWidth` - the real physical column count, used for
     * column/length math (input-zone position and length). NOT what gets passed to
     * `TerminalEngine.setViewportWidth` - see [WRAP_WIDTH]. */
    const val COLUMNS = 40

    /** What this adapter actually passes to `TerminalEngine.setViewportWidth`, one column short
     * of [COLUMNS]. A Minitel auto-wraps as soon as a line fills all the way to column 40 -
     * *in addition* to (not instead of) the explicit CRLF `render()` sends after every line - so a
     * wrapped line exactly [COLUMNS] characters wide advances the cursor by two rows instead of
     * one. That silently desyncs every row-count this adapter relies on: content further down
     * the same chunk lands one row lower than expected (the tail can overdraw the status line at
     * the top, wrapping past row 24), and an open input zone's computed `line` - based on how
     * many rows the content is assumed to occupy - points one row too high. Wrapping one column
     * short means no line ever reaches the edge, so the terminal never auto-wraps and our own
     * CRLF stays the only thing moving the cursor down a row. */
    const val WRAP_WIDTH = COLUMNS - 1

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
