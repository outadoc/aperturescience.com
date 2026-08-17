package com.aperturescience.terminal.minitel

import com.aperturescience.terminal.EngineState
import kotlin.test.Test
import kotlin.test.assertEquals

class MinitelSessionStateTest {
    private val sampleEngineState =
        EngineState(
            entryMode = 2,
            qon = 21,
            isCj = true,
            notesPage = 3,
            pageOffset = 104,
            gladosHeader = "GLaDOS v1.07a (c) 1982 Aperture Science, Inc.",
            gladosPrompt = "^^ADMIN> ",
            gladosMessage = "\n\nERROR 24 [File 'X' not found]",
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
}
