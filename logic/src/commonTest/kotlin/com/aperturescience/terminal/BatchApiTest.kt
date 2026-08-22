package com.aperturescience.terminal

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Covers `instantReveal = true` for stateless hosts: `dispatch` resolves without suspending,
 * and a session resumes cleanly via [TerminalEngine.state].
 */
class BatchApiTest {
    @Test
    fun `Boot shows the bare login prompt - matching the animated path`() =
        runTest {
            val instant = TerminalEngine(instantReveal = true)
            instant.dispatch(Intent.Boot)
            assertEquals("> ", instant.state.value.displayText)
        }

    @Test
    fun `LineSubmitted drives login through to the shell prompt`() =
        runTest {
            val engine = TerminalEngine(instantReveal = true)
            engine.dispatch(Intent.Boot)
            engine.dispatch(Intent.LineSubmitted("LOGON"))
            engine.dispatch(Intent.LineSubmitted("TESTER"))
            engine.dispatch(Intent.LineSubmitted("PORTAL"))
            val result = engine.state.value.displayText

            assertTrue(result.contains("B:\\>"))
            assertTrue(result.contains("GLaDOS v1.07 "))
        }

    @Test
    fun `LineSubmitted rejects invalid input just like KeyPressed does`() =
        runTest {
            val engine = TerminalEngine(instantReveal = true)
            engine.dispatch(Intent.Boot)
            engine.dispatch(Intent.LineSubmitted("LOGON"))
            engine.dispatch(Intent.LineSubmitted("AB")) // too short, must be rejected and redisplayed
            val result = engine.state.value.displayText

            assertTrue(result.contains("Username>"))
            assertFalse(result.contains("Password>"))
        }

    @Test
    fun `LineSubmitted content matches the streaming path exactly for the same inputs`() =
        runTest {
            val fixedUid = "FIXEDUID0001"
            val streamingState = TerminalEngine().state.value.copy(uid = fixedUid)
            val instantState = TerminalEngine(instantReveal = true).state.value.copy(uid = fixedUid)

            val streaming = loginToShell(TerminalEngine(initialState = streamingState))

            val instant = TerminalEngine(instantReveal = true, initialState = instantState)
            instant.dispatch(Intent.Boot)
            instant.dispatch(Intent.LineSubmitted("LOGON"))
            instant.dispatch(Intent.LineSubmitted("TESTER"))
            instant.dispatch(Intent.LineSubmitted("PORTAL"))

            assertEquals(streaming.state.value.displayText, instant.state.value.displayText)
        }

    @Test
    fun `Advanced toggles the cake-bosskey loop just like KeyPressed does`() =
        runTest {
            val engine = TerminalEngine(instantReveal = true)
            engine.dispatch(Intent.Boot)
            engine.dispatch(Intent.LineSubmitted("LOGON"))
            engine.dispatch(Intent.LineSubmitted("TESTER"))
            engine.dispatch(Intent.LineSubmitted("PORTAL"))
            engine.dispatch(Intent.LineSubmitted("THECAKEISALIE"))
            val cakeScreen = engine.state.value.displayText
            assertTrue(cakeScreen.contains("left the building"))

            engine.dispatch(Intent.Advanced)
            val bosskeyScreen = engine.state.value.displayText
            assertNotEquals(cakeScreen, bosskeyScreen)
            assertTrue(bosskeyScreen.contains("TOTAL"))

            engine.dispatch(Intent.Advanced)
            assertEquals(cakeScreen, engine.state.value.displayText)
        }

    @Test
    fun `Advanced pages through NOTES-EXE all four history entries back to the admin shell`() =
        runTest {
            val engine = TerminalEngine(instantReveal = true)
            engine.dispatch(Intent.Boot)
            engine.dispatch(Intent.LineSubmitted("LOGON"))
            engine.dispatch(Intent.LineSubmitted("CJOHNSON"))
            engine.dispatch(Intent.LineSubmitted("TIER3"))
            engine.dispatch(Intent.LineSubmitted("NOTES"))
            assertTrue(
                engine.state.value.displayText
                    .contains("1953"),
            )

            engine.dispatch(Intent.Advanced)
            assertTrue(
                engine.state.value.displayText
                    .contains("1979"),
            )
            engine.dispatch(Intent.Advanced) // page 3
            engine.dispatch(Intent.Advanced)
            assertTrue(
                engine.state.value.displayText
                    .contains("[END]"),
            )

            engine.dispatch(Intent.Advanced)
            assertTrue(
                engine.state.value.displayText
                    .contains("ADMIN>"),
            )
        }

    @Test
    fun `LineSubmitted sets exitRequested on LOGOUT - with the farewell message in the content`() =
        runTest {
            val engine = TerminalEngine(instantReveal = true)
            engine.dispatch(Intent.Boot)
            engine.dispatch(Intent.LineSubmitted("LOGON"))
            engine.dispatch(Intent.LineSubmitted("TESTER"))
            engine.dispatch(Intent.LineSubmitted("PORTAL"))
            engine.dispatch(Intent.LineSubmitted("LOGOUT"))

            assertTrue(engine.state.value.exitRequested)
            assertTrue(
                engine.state.value.displayText
                    .contains("https://store.steampowered.com/app/400/Portal"),
            )
            assertTrue(
                engine.state.value.annotations
                    .any { it.tag == EasterEgg.STORE.tag },
            )
        }

    @Test
    fun `state and restoring on a new instance resumes with no observable difference`() =
        runTest {
            val original = TerminalEngine(instantReveal = true)
            original.dispatch(Intent.Boot)
            original.dispatch(Intent.LineSubmitted("LOGON"))
            original.dispatch(Intent.LineSubmitted("TESTER"))

            // "Pause" here, as a stateless host would between two HTTP calls.
            val snapshot = original.state.value
            val resumed = TerminalEngine(instantReveal = true, initialState = snapshot)

            original.dispatch(Intent.LineSubmitted("PORTAL"))
            resumed.dispatch(Intent.LineSubmitted("PORTAL"))

            assertEquals(original.state.value.displayText, resumed.state.value.displayText)
            assertTrue(
                resumed.state.value.displayText
                    .contains("B:\\>"),
            )
        }

    @Test
    fun `a captured mid-form snapshot round-trips through EngineState fully intact`() =
        runTest {
            val engine = TerminalEngine(instantReveal = true)
            engine.dispatch(Intent.Boot)
            engine.dispatch(Intent.LineSubmitted("LOGON"))
            engine.dispatch(Intent.LineSubmitted("TESTER"))
            engine.dispatch(Intent.LineSubmitted("PORTAL"))
            engine.dispatch(Intent.LineSubmitted("APPLY"))
            engine.dispatch(Intent.LineSubmitted("CONTINUE"))
            engine.dispatch(Intent.LineSubmitted("CONTINUE"))
            engine.dispatch(Intent.LineSubmitted("AN ANSWER")) // -> question 2

            val snapshot = engine.state.value
            val resumed = TerminalEngine(instantReveal = true, initialState = snapshot)

            assertEquals(engine.state.value.displayText, resumed.state.value.displayText)
            assertEquals(snapshot, resumed.state.value)
        }

    @Test
    fun `a captured UID-screen snapshot round-trips its blink annotation through EngineState`() =
        runTest {
            val engine = TerminalEngine(instantReveal = true)
            engine.dispatch(Intent.Boot)
            engine.dispatch(Intent.LineSubmitted("LOGON"))
            engine.dispatch(Intent.LineSubmitted("TESTER"))
            engine.dispatch(Intent.LineSubmitted("PORTAL"))
            engine.dispatch(Intent.LineSubmitted("APPLY"))
            engine.dispatch(Intent.LineSubmitted("CONTINUE")) // -> UID display screen, the one blinking screen

            val snapshot = engine.state.value
            assertTrue(snapshot.annotations.any { it.tag == BLINK_TAG })

            val resumed = TerminalEngine(instantReveal = true, initialState = snapshot)
            assertEquals(snapshot, resumed.state.value)
            assertEquals(snapshot.annotations, resumed.state.value.annotations)
        }
}
