package com.aperturescience.terminal.minitel

/**
 * Minitel screens are a fixed 40x24 grid, no scrolling, one Vidéotex frame per HTTP call - this
 * slices a `TerminalEngine` turn's output into screen-fuls, separate from Q21's own pagination.
 */
object ScreenChunker {
    /**
     * The real physical column count - used for column/length math, not passed to
     * `setViewportWidth` (see [WRAP_WIDTH]).
     */
    const val COLUMNS = 40

    /**
     * Passed to `setViewportWidth`, one column short of [COLUMNS]: a full-width line auto-wraps
     * on the Minitel too, doubling up with our own CRLF and desyncing every row count.
     */
    const val WRAP_WIDTH = COLUMNS - 1

    /**
     * Matches `VideotexBuilder.screenHeight - 1` (line 0 is the status line).
     */
    const val ROWS_PER_SCREEN = 24

    /**
     * Splits [fullText] into ≤[rowsPerScreen]-line chunks. Always returns at least one chunk
     * (possibly empty), so callers never need to special-case blank content.
     */
    fun chunk(
        fullText: String,
        rowsPerScreen: Int = ROWS_PER_SCREEN,
    ): List<List<String>> {
        val lines = fullText.split("\n")
        val chunks = lines.chunked(rowsPerScreen)
        return chunks.ifEmpty { listOf(emptyList()) }
    }

    /**
     * Absolute start offset (in [fullText]) of each line [chunk] returns, chunked identically -
     * `chunkStartOffsets(t, n)[c][i]` is exactly where `chunk(t, n)[c][i]` starts in [fullText].
     * Lets a caller intersect a whole-text offset range against one rendered chunk's lines.
     */
    fun chunkStartOffsets(
        fullText: String,
        rowsPerScreen: Int = ROWS_PER_SCREEN,
    ): List<List<Int>> {
        val offsets = mutableListOf<Int>()
        var pos = 0
        for (line in fullText.split("\n")) {
            offsets.add(pos)
            pos += line.length + 1
        }
        val chunks = offsets.chunked(rowsPerScreen)
        return chunks.ifEmpty { listOf(emptyList()) }
    }
}
