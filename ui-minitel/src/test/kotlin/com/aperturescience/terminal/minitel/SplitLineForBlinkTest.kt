package com.aperturescience.terminal.minitel

import kotlin.test.Test
import kotlin.test.assertEquals

class SplitLineForBlinkTest {
    @Test
    fun `returns the whole line unsplit when blinkRange is null`() {
        val split = splitLineForBlink("hello world", lineStart = 0, blinkRange = null)
        assertEquals(LineBlinkSplit("hello world", "", ""), split)
    }

    @Test
    fun `isolates the intersecting substring`() {
        val split = splitLineForBlink("hello world", lineStart = 10, blinkRange = 16..20)
        assertEquals(LineBlinkSplit("hello ", "world", ""), split)
    }

    @Test
    fun `returns the whole line unsplit when the range doesn't intersect it`() {
        val split = splitLineForBlink("hello world", lineStart = 100, blinkRange = 0..5)
        assertEquals(LineBlinkSplit("hello world", "", ""), split)
    }

    @Test
    fun `blinks the whole line when blinkRange fully covers it and spills past both ends`() {
        // blinkRange spans multiple physical lines - this one is fully inside it.
        val split = splitLineForBlink("BCDEF", lineStart = 20, blinkRange = 15..30)
        assertEquals(LineBlinkSplit("", "BCDEF", ""), split)
    }

    @Test
    fun `handles a range that only partially overlaps a wrapped line's head`() {
        val split = splitLineForBlink("ABCDE", lineStart = 20, blinkRange = 22..30)
        assertEquals(LineBlinkSplit("AB", "CDE", ""), split)
    }
}
