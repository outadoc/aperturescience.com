package com.aperturescience.terminal.minitel

import kotlin.test.Test
import kotlin.test.assertEquals

class SplitLineForRangeTest {
    @Test
    fun `returns the whole line unsplit when range is null`() {
        val split = splitLineForRange("hello world", lineStart = 0, range = null)
        assertEquals(LineRangeSplit("hello world", "", ""), split)
    }

    @Test
    fun `isolates the intersecting substring`() {
        val split = splitLineForRange("hello world", lineStart = 10, range = 16..20)
        assertEquals(LineRangeSplit("hello ", "world", ""), split)
    }

    @Test
    fun `returns the whole line unsplit when the range doesn't intersect it`() {
        val split = splitLineForRange("hello world", lineStart = 100, range = 0..5)
        assertEquals(LineRangeSplit("hello world", "", ""), split)
    }

    @Test
    fun `marks the whole line when range fully covers it and spills past both ends`() {
        // range spans multiple physical lines - this one is fully inside it.
        val split = splitLineForRange("BCDEF", lineStart = 20, range = 15..30)
        assertEquals(LineRangeSplit("", "BCDEF", ""), split)
    }

    @Test
    fun `handles a range that only partially overlaps a wrapped line's head`() {
        val split = splitLineForRange("ABCDE", lineStart = 20, range = 22..30)
        assertEquals(LineRangeSplit("AB", "CDE", ""), split)
    }
}
