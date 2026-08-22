package com.aperturescience.terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Direct `TerminalReducer.reduce(state, intent)` assertions - zero coroutines, zero `runTest`.
 * Every state transition is just a plain function call on plain data.
 */
class TerminalReducerTest {
    private val baseState =
        EngineState(
            mode = Mode.Login.Initial,
            isAdmin = false,
            uid = "TESTUID0001",
            pageContent = "",
            input = "",
            wrapWidth = 100,
            isLocked = false,
        )

    @Test
    fun `CharacterRevealed opens a blink annotation on BLINK_START and closes it on BLINK_END`() {
        var state = baseState.copy(pageContent = "abc")
        state = TerminalReducer.reduce(state, Intent.CharacterRevealed(BLINK_START)).state
        assertEquals(3, state.pendingAnnotationStart)

        for (c in "XY") {
            state = TerminalReducer.reduce(state, Intent.CharacterRevealed(c)).state
        }
        state = TerminalReducer.reduce(state, Intent.CharacterRevealed(BLINK_END)).state

        assertEquals(listOf(TextAnnotation(BLINK_TAG, 3 until 5)), state.annotations)
        assertNull(state.pendingAnnotationStart)
    }

    @Test
    fun `CharacterRevealed opens an easter-egg annotation on its start char and closes it on the shared BLINK_END`() {
        var state = baseState.copy(pageContent = "abc")
        val storeStartChar = EASTER_EGG_START_CHAR.getValue(EasterEgg.STORE)
        state = TerminalReducer.reduce(state, Intent.CharacterRevealed(storeStartChar)).state
        assertEquals(3, state.pendingAnnotationStart)
        assertEquals(EasterEgg.STORE.tag, state.pendingAnnotationTag)

        for (c in "XY") {
            state = TerminalReducer.reduce(state, Intent.CharacterRevealed(c)).state
        }
        state = TerminalReducer.reduce(state, Intent.CharacterRevealed(BLINK_END)).state

        assertEquals(listOf(TextAnnotation(EasterEgg.STORE.tag, 3 until 5)), state.annotations)
        assertNull(state.pendingAnnotationStart)
        assertNull(state.pendingAnnotationTag)
    }

    @Test
    fun `LineSubmitted THECAKEISALIE emits a two-part reveal effect sequence with a wait between them`() {
        val shellState = baseState.copy(mode = Mode.Shell())
        val reduction = TerminalReducer.reduce(shellState, Intent.LineSubmitted("THECAKEISALIE"))

        assertEquals(3, reduction.effects.size)
        val (part1, wait, part2) = reduction.effects
        assertIs<Effect.RevealCharacters>(part1)
        assertNull(part1.thenDispatch)
        assertEquals(Effect.Wait(2000), wait)
        assertIs<Effect.RevealCharacters>(part2)
        assertEquals(Intent.Unlocked, part2.thenDispatch)
    }

    @Test
    fun `LineSubmitted LOGOUT reveals the farewell message but does not request exit until the wait finishes`() {
        val shellState = baseState.copy(mode = Mode.Shell())
        val reduction = TerminalReducer.reduce(shellState, Intent.LineSubmitted("LOGOUT"))

        assertFalse(reduction.state.exitRequested)
        val wait = reduction.effects.last()
        assertIs<Effect.Wait>(wait)
        assertEquals(Intent.ExitRequested, wait.thenDispatch)
        assertTrue(TerminalReducer.reduce(reduction.state, Intent.ExitRequested).state.exitRequested)
    }

    @Test
    fun `KeyPressed is a total no-op while locked`() {
        val locked = baseState.copy(isLocked = true)
        assertEquals(Reduction(locked), TerminalReducer.reduce(locked, Intent.KeyPressed("A")))
    }

    @Test
    fun `a too-short username is rejected without producing any reveal effects`() {
        val state = baseState.copy(mode = Mode.Login.Username, input = "AB")
        val reduction = TerminalReducer.reduce(state, Intent.KeyPressed("Enter"))

        assertTrue(reduction.effects.isEmpty())
        assertEquals("", reduction.state.input)
        assertFalse(reduction.state.isLocked)
    }
}
