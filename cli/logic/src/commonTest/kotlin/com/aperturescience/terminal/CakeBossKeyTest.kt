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
            val output = engine.liveLine.value
            assertTrue(output.contains("When was the last time you left the building?"))
            assertTrue(output.contains("If a supervisor walks by, press return!"))
        }

    @Test
    fun `any accepted key toggles from the cake monologue to the bosskey spreadsheet`() =
        runTest {
            val engine = loginToShell()
            submit(engine, "THECAKEISALIE")
            val cakeScreen = engine.liveLine.value

            pressKey(engine, "X")
            val bosskeyScreen = engine.liveLine.value
            assertNotEquals(cakeScreen, bosskeyScreen)
            assertTrue(bosskeyScreen.contains("TOTAL"))
            assertTrue(bosskeyScreen.contains("976,076.49"))
        }

    @Test
    fun `toggling again from bosskey returns to the cake monologue`() =
        runTest {
            val engine = loginToShell()
            submit(engine, "THECAKEISALIE")
            val cakeScreen = engine.liveLine.value

            pressKey(engine, "X")
            pressKey(engine, "Y")
            assertEquals(cakeScreen, engine.liveLine.value)
        }

    @Test
    fun `the toggle keeps working indefinitely`() =
        runTest {
            val engine = loginToShell()
            submit(engine, "THECAKEISALIE")
            val cakeScreen = engine.liveLine.value

            repeat(10) { pressKey(engine, "Q") }
            // an even number of toggles lands back on the cake screen
            assertEquals(cakeScreen, engine.liveLine.value)
        }

    @Test
    fun `an unaccepted key does not toggle the cake-bosskey loop`() =
        runTest {
            val engine = loginToShell()
            submit(engine, "THECAKEISALIE")
            val before = engine.liveLine.value

            pressKey(engine, "Escape")
            assertEquals(before, engine.liveLine.value)
        }

    @Test
    fun `the cake easter egg never sets exitRequested`() =
        runTest {
            val engine = loginToShell()
            submit(engine, "THECAKEISALIE")
            pressKey(engine, "X")
            pressKey(engine, "Y")
            assertTrue(!engine.exitRequested.value)
        }
}
