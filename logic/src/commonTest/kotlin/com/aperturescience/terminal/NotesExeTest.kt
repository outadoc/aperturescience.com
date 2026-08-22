package com.aperturescience.terminal

import com.aperturescience.terminal.data.TerminalData
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotesExeTest {
    @Test
    fun `NOTES-EXE has exactly four history pages available`() {
        assertEquals(4, TerminalData.notesHistoryPages.size)
    }

    @Test
    fun `opening NOTES shows the first history page`() =
        runTest {
            val engine = loginAsAdmin()
            submit(engine, "NOTES")
            assertTrue(
                engine.state.value.displayText
                    .contains("1953"),
            )
            assertTrue(
                engine.state.value.displayText
                    .contains("[MORE]"),
            )
        }

    @Test
    fun `Enter pages through all four history entries`() =
        runTest {
            val engine = loginAsAdmin()
            submit(engine, "NOTES")

            assertTrue(
                engine.state.value.displayText
                    .contains("1953"),
            )
            pressKey(engine, Key.Named.ENTER)
            assertTrue(
                engine.state.value.displayText
                    .contains("1979"),
            )
            pressKey(engine, Key.Named.ENTER)
            pressKey(engine, Key.Named.ENTER)
            assertTrue(
                engine.state.value.displayText
                    .contains("[END]"),
            )
        }

    @Test
    fun `any accepted key advances a page - not only Enter`() =
        runTest {
            val engine = loginAsAdmin()
            submit(engine, "NOTES")
            val firstPage = engine.state.value.displayText

            pressKey(engine, Key.RawChar('A'))
            assertTrue(engine.state.value.displayText != firstPage)
        }

    @Test
    fun `PageUp PageDown and ArrowLeft also advance a page`() =
        runTest {
            for (key in listOf(Key.Named.PAGE_UP, Key.Named.PAGE_DOWN, Key.Named.ARROW_LEFT)) {
                val engine = loginAsAdmin()
                submit(engine, "NOTES")
                val firstPage = engine.state.value.displayText

                pressKey(engine, key)
                assertTrue(engine.state.value.displayText != firstPage, "expected '$key' to advance a page")
            }
        }

    @Test
    fun `an unaccepted key does nothing while reading NOTES-EXE`() =
        runTest {
            val engine = loginAsAdmin()
            submit(engine, "NOTES")
            val before = engine.state.value.displayText

            pressKey(engine, Key.Other)
            assertEquals(before, engine.state.value.displayText)
        }

    @Test
    fun `paging past the last history entry returns to the admin shell`() =
        runTest {
            val engine = loginAsAdmin()
            submit(engine, "NOTES")

            repeat(4) { pressKey(engine, Key.Named.ENTER) }

            assertTrue(
                engine.state.value.displayText
                    .contains("ADMIN>"),
            )
        }
}
