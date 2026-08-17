package com.aperturescience.terminal

import com.aperturescience.terminal.data.QuestionType
import com.aperturescience.terminal.data.TerminalData
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Covers the synchronous "batch" API (`instantReveal = true`) for stateless hosts: content
 * matches the animated streaming path, and a session resumes cleanly via [TerminalEngine.captureState]. */
class BatchApiTest {
    @Test
    fun `bootTurn shows the bare login prompt - matching boot`() =
        runTest {
            val instant = TerminalEngine(instantReveal = true)
            assertEquals("> ", instant.bootTurn())
        }

    @Test
    fun `submitLine drives login through to the shell prompt`() =
        runTest {
            val engine = TerminalEngine(instantReveal = true)
            engine.bootTurn()
            engine.submitLine("LOGON")
            engine.submitLine("TESTER")
            val result = engine.submitLine("PORTAL")

            assertTrue(result.contains("B:\\>"))
            assertTrue(result.contains("GLaDOS v1.07 "))
        }

    @Test
    fun `submitLine rejects invalid input just like onKeyEvent does`() =
        runTest {
            val engine = TerminalEngine(instantReveal = true)
            engine.bootTurn()
            engine.submitLine("LOGON")
            val result = engine.submitLine("AB") // too short, must be rejected and redisplayed

            assertTrue(result.contains("Username>"))
            assertFalse(result.contains("Password>"))
        }

    @Test
    fun `submitLine content matches the streaming path exactly for the same inputs`() =
        runTest {
            val fixedUid = "FIXEDUID0001"
            val streamingState = TerminalEngine().captureState().copy(uid = fixedUid)
            val instantState = TerminalEngine(instantReveal = true).captureState().copy(uid = fixedUid)

            val streaming = loginToShell(TerminalEngine(initialState = streamingState))

            val instant = TerminalEngine(instantReveal = true, initialState = instantState)
            instant.bootTurn()
            instant.submitLine("LOGON")
            instant.submitLine("TESTER")
            val instantResult = instant.submitLine("PORTAL")

            assertEquals(streaming.liveLine.value, instantResult)
        }

    @Test
    fun `advance toggles the cake-bosskey loop just like onKeyEvent does`() =
        runTest {
            val engine = TerminalEngine(instantReveal = true)
            engine.bootTurn()
            engine.submitLine("LOGON")
            engine.submitLine("TESTER")
            engine.submitLine("PORTAL")
            val cakeScreen = engine.submitLine("THECAKEISALIE")
            assertTrue(cakeScreen.contains("left the building"))

            val bosskeyScreen = engine.advance()
            assertNotEquals(cakeScreen, bosskeyScreen)
            assertTrue(bosskeyScreen.contains("TOTAL"))

            val backToCake = engine.advance()
            assertEquals(cakeScreen, backToCake)
        }

    @Test
    fun `advance pages through NOTES-EXE all four history entries back to the admin shell`() =
        runTest {
            val engine = TerminalEngine(instantReveal = true)
            engine.bootTurn()
            engine.submitLine("LOGON")
            engine.submitLine("CJOHNSON")
            engine.submitLine("TIER3")
            val page1 = engine.submitLine("NOTES")
            assertTrue(page1.contains("1953"))

            val page2 = engine.advance()
            assertTrue(page2.contains("1979"))
            engine.advance() // page 3
            val page4 = engine.advance()
            assertTrue(page4.contains("[END]"))

            val backToShell = engine.advance()
            assertTrue(backToShell.contains("ADMIN>"))
        }

    @Test
    fun `page paginates question 21's choices and back without touching the input line`() =
        runTest {
            val engine = TerminalEngine(instantReveal = true)
            engine.bootTurn()
            engine.submitLine("LOGON")
            engine.submitLine("TESTER")
            engine.submitLine("PORTAL")
            engine.submitLine("APPLY")
            engine.submitLine("CONTINUE")
            engine.submitLine("CONTINUE")
            for (question in TerminalData.questions.take(20)) {
                engine.submitLine(if (question.type == QuestionType.TEXT) "AN ANSWER" else "1")
            }

            val firstPage = engine.liveLine.value
            assertTrue(firstPage.contains("total choices"))

            val secondPage = engine.page(104)
            assertNotEquals(firstPage, secondPage)

            val backToFirst = engine.page(-104)
            assertEquals(firstPage, backToFirst)
        }

    @Test
    fun `submitLine sets exitRequested on LOGOUT - with the farewell message in the content`() =
        runTest {
            val engine = TerminalEngine(instantReveal = true)
            engine.bootTurn()
            engine.submitLine("LOGON")
            engine.submitLine("TESTER")
            engine.submitLine("PORTAL")
            val result = engine.submitLine("LOGOUT")

            assertTrue(engine.exitRequested.value)
            assertTrue(result.contains("ERROR: STORE NOT FOUND"))
        }

    @Test
    fun `captureState and restoring on a new instance resumes with no observable difference`() =
        runTest {
            val original = TerminalEngine(instantReveal = true)
            original.bootTurn()
            original.submitLine("LOGON")
            original.submitLine("TESTER")

            // "Pause" here, as a stateless host would between two HTTP calls.
            val snapshot = original.captureState()
            val resumed = TerminalEngine(instantReveal = true, initialState = snapshot)

            val originalResult = original.submitLine("PORTAL")
            val resumedResult = resumed.submitLine("PORTAL")

            assertEquals(originalResult, resumedResult)
            assertTrue(resumedResult.contains("B:\\>"))
        }

    @Test
    fun `a captured mid-form snapshot round-trips through EngineState fully intact`() =
        runTest {
            val engine = TerminalEngine(instantReveal = true)
            engine.bootTurn()
            engine.submitLine("LOGON")
            engine.submitLine("TESTER")
            engine.submitLine("PORTAL")
            engine.submitLine("APPLY")
            engine.submitLine("CONTINUE")
            engine.submitLine("CONTINUE")
            engine.submitLine("AN ANSWER") // -> question 2

            val snapshot = engine.captureState()
            val resumed = TerminalEngine(instantReveal = true, initialState = snapshot)

            assertEquals(engine.liveLine.value, resumed.liveLine.value)
            assertEquals(snapshot, resumed.captureState())
        }
}
