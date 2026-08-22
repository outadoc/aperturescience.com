package com.aperturescience.terminal

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CakeBossKeyTest {
    @Test
    fun `THECAKEISALIE shows the security-feed monologue`() =
        runTest {
            val engine = loginToShell()
            submit(engine, "THECAKEISALIE")
            val output = engine.state.value.displayText
            assertTrue(output.contains("When was the last time you left the building?"))
            assertTrue(output.contains("If a supervisor walks by, press return!"))
            assertTrue(
                engine.state.value.annotations
                    .any { it.tag == EasterEgg.SECURITY_VIDEO.tag },
            )
        }

    @Test
    fun `any accepted key toggles from the cake monologue to the bosskey spreadsheet`() =
        runTest {
            val engine = loginToShell()
            submit(engine, "THECAKEISALIE")
            val cakeScreen = engine.state.value.displayText

            pressKey(engine, "X")
            val bosskeyScreen = engine.state.value.displayText
            assertNotEquals(cakeScreen, bosskeyScreen)
            assertTrue(bosskeyScreen.contains("TOTAL"))
            assertTrue(bosskeyScreen.contains("976,076.49"))
        }

    @Test
    fun `toggling again from bosskey returns to the cake monologue`() =
        runTest {
            val engine = loginToShell()
            submit(engine, "THECAKEISALIE")
            val cakeScreen = engine.state.value.displayText

            pressKey(engine, "X")
            pressKey(engine, "Y")
            assertEquals(cakeScreen, engine.state.value.displayText)
        }

    @Test
    fun `the toggle keeps working indefinitely`() =
        runTest {
            val engine = loginToShell()
            submit(engine, "THECAKEISALIE")
            val cakeScreen = engine.state.value.displayText

            repeat(10) { pressKey(engine, "Q") }
            // an even number of toggles lands back on the cake screen
            assertEquals(cakeScreen, engine.state.value.displayText)
        }

    @Test
    fun `PageUp PageDown and ArrowLeft also toggle the cake-bosskey loop`() =
        runTest {
            for (key in listOf("PageUp", "PageDown", "ArrowLeft")) {
                val engine = loginToShell()
                submit(engine, "THECAKEISALIE")
                val cakeScreen = engine.state.value.displayText

                pressKey(engine, key)
                assertNotEquals(cakeScreen, engine.state.value.displayText, "expected '$key' to toggle the loop")
            }
        }

    @Test
    fun `an unaccepted key does not toggle the cake-bosskey loop`() =
        runTest {
            val engine = loginToShell()
            submit(engine, "THECAKEISALIE")
            val before = engine.state.value.displayText

            pressKey(engine, "Escape")
            assertEquals(before, engine.state.value.displayText)
        }

    @Test
    fun `the cake easter egg never sets exitRequested`() =
        runTest {
            val engine = loginToShell()
            submit(engine, "THECAKEISALIE")
            pressKey(engine, "X")
            pressKey(engine, "Y")
            assertTrue(!engine.state.value.exitRequested)
        }
}
