package com.aperturescience.terminal

import com.aperturescience.terminal.data.TerminalData
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotesExeTest {
    @Test
    fun `NOTES-EXE has exactly four history pages available`() {
        assertEquals(4, TerminalData.cjHistory.size)
    }

    @Test
    fun `opening NOTES shows the first history page`() =
        runTest {
            val engine = loginAsAdmin()
            submit(engine, "NOTES")
            assertTrue(engine.liveLine.value.contains("1953"))
            assertTrue(engine.liveLine.value.contains("[MORE]"))
        }

    @Test
    fun `Enter pages through all four history entries`() =
        runTest {
            val engine = loginAsAdmin()
            submit(engine, "NOTES")

            assertTrue(engine.liveLine.value.contains("1953"))
            pressKey(engine, "Enter")
            assertTrue(engine.liveLine.value.contains("1979"))
            pressKey(engine, "Enter")
            pressKey(engine, "Enter")
            assertTrue(engine.liveLine.value.contains("[END]"))
        }

    @Test
    fun `any accepted key advances a page - not only Enter`() =
        runTest {
            val engine = loginAsAdmin()
            submit(engine, "NOTES")
            val firstPage = engine.liveLine.value

            pressKey(engine, "A")
            assertTrue(engine.liveLine.value != firstPage)
        }

    @Test
    fun `PageUp PageDown and ArrowLeft also advance a page`() =
        runTest {
            for (key in listOf("PageUp", "PageDown", "ArrowLeft")) {
                val engine = loginAsAdmin()
                submit(engine, "NOTES")
                val firstPage = engine.liveLine.value

                pressKey(engine, key)
                assertTrue(engine.liveLine.value != firstPage, "expected '$key' to advance a page")
            }
        }

    @Test
    fun `an unaccepted key does nothing while reading NOTES-EXE`() =
        runTest {
            val engine = loginAsAdmin()
            submit(engine, "NOTES")
            val before = engine.liveLine.value

            pressKey(engine, "Escape")
            assertEquals(before, engine.liveLine.value)
        }

    @Test
    fun `paging past the last history entry returns to the admin shell`() =
        runTest {
            val engine = loginAsAdmin()
            submit(engine, "NOTES")

            repeat(4) { pressKey(engine, "Enter") }

            assertTrue(engine.liveLine.value.contains("ADMIN>"))
        }
}
