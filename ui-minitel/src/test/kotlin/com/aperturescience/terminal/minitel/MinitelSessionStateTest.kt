package com.aperturescience.terminal.minitel

import com.aperturescience.terminal.BLINK_TAG
import com.aperturescience.terminal.EngineState
import com.aperturescience.terminal.Mode
import com.aperturescience.terminal.TextAnnotation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MinitelSessionStateTest {
    private val sampleEngineState =
        EngineState(
            mode = Mode.Application(questionNumber = 21, pageOffset = 104),
            isAdmin = true,
            uid = "ABCDEF012345",
            pageContent = "Form FORMS-EN-2873-FORM - Page 21\n\nsome question text\n\n001] a\n002] b\n> ",
            input = "42",
            wrapWidth = 40,
            isLocked = false,
        )

    @Test
    fun `toEngineState round-trips every field unchanged`() {
        val minitelState = MinitelSessionState.from(sampleEngineState, chunkIndex = 3, pendingDisconnect = true)
        assertEquals(sampleEngineState, minitelState.toEngineState())
    }

    @Test
    fun `from carries adapter-only fields alongside the mirrored EngineState fields`() {
        val minitelState = MinitelSessionState.from(sampleEngineState, chunkIndex = 5, pendingDisconnect = true)
        assertEquals(5, minitelState.chunkIndex)
        assertEquals(true, minitelState.pendingDisconnect)
    }

    @Test
    fun `from defaults chunkIndex and pendingDisconnect when not specified`() {
        val minitelState = MinitelSessionState.from(sampleEngineState)
        assertEquals(0, minitelState.chunkIndex)
        assertEquals(false, minitelState.pendingDisconnect)
    }

    @Test
    fun `initial is a placeholder with a fresh chunk index and no pending disconnect`() {
        val initial = MinitelSessionState.initial()
        assertEquals(0, initial.chunkIndex)
        assertEquals(false, initial.pendingDisconnect)
        assertEquals(true, initial.isLocked)
    }

    @Test
    fun `toEngineState round-trips a non-empty annotations list unchanged`() {
        val withBlink = sampleEngineState.copy(annotations = listOf(TextAnnotation(BLINK_TAG, 5 until 12)))
        val minitelState = MinitelSessionState.from(withBlink)
        assertEquals(withBlink, minitelState.toEngineState())
    }

    @Test
    fun `annotations defaults to empty when not specified`() {
        val minitelState = MinitelSessionState.from(sampleEngineState)
        assertTrue(minitelState.annotations.isEmpty())
    }
}
