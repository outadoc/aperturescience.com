package com.aperturescience.terminal

import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InputHandlingTest {
    @Test
    fun `input is swallowed entirely while the engine is locked mid-animation`() =
        runTest {
            val engine = TerminalEngine()
            engine.boot(this)
            // Deliberately not advancing virtual time: the boot-time typewriter reveal is still
            // "in flight", so the engine is locked and every key must be a no-op.
            engine.onKeyEvent("L")
            engine.onKeyEvent("O")
            engine.onKeyEvent("Enter")
            assertEquals("", engine.liveLine.value)

            advanceUntilIdle()
            assertEquals("> ", engine.liveLine.value)
        }

    @Test
    fun `digits letters space and question mark are all accepted`() =
        runTest {
            val engine = bootAndSettle(TerminalEngine())
            for (key in listOf("A", "z", "5", " ", "?")) {
                engine.onKeyEvent(key)
            }
            advanceUntilIdle()
            assertEquals("> AZ5 ?", engine.liveLine.value)
        }

    @Test
    fun `lowercase letters are uppercased as they are typed`() =
        runTest {
            val engine = bootAndSettle(TerminalEngine())
            for (c in "logon") engine.onKeyEvent(c.toString())
            advanceUntilIdle()
            assertEquals("> LOGON", engine.liveLine.value)
        }

    @Test
    fun `punctuation other than question mark is rejected outright`() =
        runTest {
            val engine = bootAndSettle(TerminalEngine())
            for (key in listOf(".", ",", "!", "@", "#", "-", "_", "/", "'", "\"")) {
                engine.onKeyEvent(key)
            }
            advanceUntilIdle()
            assertEquals("> ", engine.liveLine.value, "no punctuation besides '?' should ever be accepted")
        }

    @Test
    fun `special key names other than Enter Backspace PageUp PageDown are ignored as text`() =
        runTest {
            val engine = bootAndSettle(TerminalEngine())
            for (key in listOf("F1", "Insert", "Delete", "Home", "End", "ArrowUp", "Tab", "Escape")) {
                engine.onKeyEvent(key)
            }
            advanceUntilIdle()
            assertEquals("> ", engine.liveLine.value)
        }

    @Test
    fun `Backspace removes the last typed character`() =
        runTest {
            val engine = bootAndSettle(TerminalEngine())
            for (c in "AB") engine.onKeyEvent(c.toString())
            advanceUntilIdle()
            assertEquals("> AB", engine.liveLine.value)

            pressKey(engine, "Backspace")
            assertEquals("> A", engine.liveLine.value)
        }

    @Test
    fun `Backspace on empty input does nothing`() =
        runTest {
            val engine = bootAndSettle(TerminalEngine())
            pressKey(engine, "Backspace")
            assertEquals("> ", engine.liveLine.value)
        }

    @Test
    fun `input is capped at sixty-five characters`() =
        runTest {
            val engine = bootAndSettle(TerminalEngine())
            repeat(70) { engine.onKeyEvent("A") }
            advanceUntilIdle()
            assertEquals("> " + "A".repeat(65), engine.liveLine.value)
        }

    @Test
    fun `onKeyEvent always reports the key as handled`() =
        runTest {
            val engine = bootAndSettle(TerminalEngine())
            assertTrue(engine.onKeyEvent("A"))
            assertTrue(engine.onKeyEvent("."))
            assertTrue(engine.onKeyEvent("F1"))
            assertTrue(engine.onKeyEvent("Enter"))
        }
}
