package com.aperturescience.terminal.minitel

import kotlin.test.Test
import kotlin.test.assertEquals

class ScreenChunkerTest {
    @Test
    fun `empty text produces a single empty chunk`() {
        val chunks = ScreenChunker.chunk("")
        assertEquals(listOf(listOf("")), chunks)
    }

    @Test
    fun `text shorter than one screen produces a single chunk`() {
        val text = (1..10).joinToString("\n") { "line $it" }
        val chunks = ScreenChunker.chunk(text, rowsPerScreen = 24)
        assertEquals(1, chunks.size)
        assertEquals(10, chunks[0].size)
    }

    @Test
    fun `text of exactly one screen's worth of lines produces a single chunk`() {
        val text = (1..24).joinToString("\n") { "line $it" }
        val chunks = ScreenChunker.chunk(text, rowsPerScreen = 24)
        assertEquals(1, chunks.size)
        assertEquals(24, chunks[0].size)
    }

    @Test
    fun `one line past a full screen spills into a second chunk`() {
        val text = (1..25).joinToString("\n") { "line $it" }
        val chunks = ScreenChunker.chunk(text, rowsPerScreen = 24)
        assertEquals(2, chunks.size)
        assertEquals(24, chunks[0].size)
        assertEquals(listOf("line 25"), chunks[1])
    }

    @Test
    fun `a 104-line question-21-sized page spans exactly ceil(104-24) chunks`() {
        val text = (1..104).joinToString("\n") { "choice $it" }
        val chunks = ScreenChunker.chunk(text, rowsPerScreen = 24)
        assertEquals(5, chunks.size) // 24*4 = 96, +8 remaining
        assertEquals(24, chunks[0].size)
        assertEquals(8, chunks[4].size)
        assertEquals(chunks.flatten(), text.split("\n"))
    }

    @Test
    fun `chunking never drops or reorders lines`() {
        val text = (1..77).joinToString("\n") { "line $it" }
        val chunks = ScreenChunker.chunk(text, rowsPerScreen = 24)
        assertEquals(text.split("\n"), chunks.flatten())
    }

    @Test
    fun `default rows-per-screen is 24`() {
        val text = (1..25).joinToString("\n") { "x" }
        assertEquals(ScreenChunker.chunk(text), ScreenChunker.chunk(text, rowsPerScreen = 24))
    }

    @Test
    fun `chunkStartOffsets lines up 1-to-1 with chunk's own lines`() {
        val text = (1..30).joinToString("\n") { "line $it" }
        val chunks = ScreenChunker.chunk(text, rowsPerScreen = 24)
        val offsets = ScreenChunker.chunkStartOffsets(text, rowsPerScreen = 24)
        assertEquals(chunks.map { it.size }, offsets.map { it.size })
        for ((c, offsetsInChunk) in offsets.withIndex()) {
            for ((i, offset) in offsetsInChunk.withIndex()) {
                assertEquals(chunks[c][i], text.substring(offset, offset + chunks[c][i].length))
            }
        }
    }

    @Test
    fun `empty text produces a single offset of zero`() {
        assertEquals(listOf(listOf(0)), ScreenChunker.chunkStartOffsets(""))
    }
}
