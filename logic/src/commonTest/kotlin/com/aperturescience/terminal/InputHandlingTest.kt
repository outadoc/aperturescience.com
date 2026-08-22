package com.aperturescience.terminal

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class InputHandlingTest {
    @Test
    fun `input is swallowed entirely while the engine is locked mid-animation`() =
        runTest {
            val engine = TerminalEngine()
            launch { engine.dispatch(Intent.Boot) }
            // Deliberately not advancing virtual time: the boot-time typewriter reveal is still
            // "in flight", so the engine is locked and every key must be a no-op.
            launch { engine.dispatch(Intent.KeyPressed(Key.RawChar('L'))) }
            launch { engine.dispatch(Intent.KeyPressed(Key.RawChar('O'))) }
            launch { engine.dispatch(Intent.KeyPressed(Key.Named.ENTER)) }
            assertEquals("", engine.state.value.displayText)

            advanceUntilIdle()
            assertEquals("> ", engine.state.value.displayText)
        }

    @Test
    fun `digits letters space and question mark are all accepted`() =
        runTest {
            val engine = bootAndSettle(TerminalEngine())
            for (c in listOf('A', 'z', '5', ' ', '?')) {
                launch { engine.dispatch(Intent.KeyPressed(Key.RawChar(c))) }
            }
            advanceUntilIdle()
            assertEquals("> AZ5 ?", engine.state.value.displayText)
        }

    @Test
    fun `lowercase letters are uppercased as they are typed`() =
        runTest {
            val engine = bootAndSettle(TerminalEngine())
            for (c in "logon") launch { engine.dispatch(Intent.KeyPressed(Key.RawChar(c))) }
            advanceUntilIdle()
            assertEquals("> LOGON", engine.state.value.displayText)
        }

    @Test
    fun `punctuation other than question mark is rejected outright`() =
        runTest {
            val engine = bootAndSettle(TerminalEngine())
            for (c in listOf(',', '!', '@', '#', '-', '_', '/', '\'', '"')) {
                launch { engine.dispatch(Intent.KeyPressed(Key.RawChar(c))) }
            }
            advanceUntilIdle()
            assertEquals(
                "> ",
                engine.state.value.displayText,
                "no punctuation besides '?' and '.' should ever be accepted",
            )
        }

    @Test
    fun `a period is accepted so file names like NOTES-EXE can be typed`() =
        runTest {
            val engine = bootAndSettle(TerminalEngine())
            for (c in "NOTES.EXE") launch { engine.dispatch(Intent.KeyPressed(Key.RawChar(c))) }
            advanceUntilIdle()
            assertEquals("> NOTES.EXE", engine.state.value.displayText)
        }

    @Test
    fun `keys with no special meaning are ignored as text`() =
        runTest {
            val engine = bootAndSettle(TerminalEngine())
            launch { engine.dispatch(Intent.KeyPressed(Key.Other)) }
            advanceUntilIdle()
            assertEquals("> ", engine.state.value.displayText)
        }

    @Test
    fun `Backspace removes the last typed character`() =
        runTest {
            val engine = bootAndSettle(TerminalEngine())
            for (c in "AB") launch { engine.dispatch(Intent.KeyPressed(Key.RawChar(c))) }
            advanceUntilIdle()
            assertEquals("> AB", engine.state.value.displayText)

            pressKey(engine, Key.Named.BACKSPACE)
            assertEquals("> A", engine.state.value.displayText)
        }

    @Test
    fun `Backspace on empty input does nothing`() =
        runTest {
            val engine = bootAndSettle(TerminalEngine())
            pressKey(engine, Key.Named.BACKSPACE)
            assertEquals("> ", engine.state.value.displayText)
        }

    @Test
    fun `input is capped at sixty-five characters`() =
        runTest {
            val engine = bootAndSettle(TerminalEngine())
            repeat(70) { launch { engine.dispatch(Intent.KeyPressed(Key.RawChar('A'))) } }
            advanceUntilIdle()
            assertEquals("> " + "A".repeat(65), engine.state.value.displayText)
        }
}
