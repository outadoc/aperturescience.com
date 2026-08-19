package com.aperturescience.terminal

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShellCommandsTest {
    @Test
    fun `DIR as a regular user shows only APPLY-EXE`() =
        runTest {
            val engine = loginToShell()
            submit(engine, "DIR")
            val output = engine.liveLine.value
            assertTrue(output.contains("DISK VOLUME 255 [NEW EMPLOYEE WORKSTATION]"))
            assertTrue(output.contains("APPLY.EXE"))
            assertTrue(output.contains("1 FILE(S) IN 19 BLOCKS"))
            assertFalse(output.contains("NOTES.EXE"))
        }

    @Test
    fun `DIR as admin also shows NOTES-EXE`() =
        runTest {
            val engine = loginAsAdmin()
            submit(engine, "DIR")
            val output = engine.liveLine.value
            assertTrue(output.contains("DISK VOLUME 255 [WORKSTATION CJOHNSON]"))
            assertTrue(output.contains("APPLY.EXE"))
            assertTrue(output.contains("NOTES.EXE"))
            assertTrue(output.contains("2 FILE(S) IN 23 BLOCKS"))
        }

    /**
     * Matches the original: a blank line pads both above "DISK VOLUME" and below the block
     * count, same as every other shell error message (e.g. `ERROR 15 [Disk is write protected]`).
     */
    @Test
    fun `DIR pads the listing with a blank line above and below`() =
        runTest {
            val engine = loginToShell()
            submit(engine, "DIR")
            val output = engine.liveLine.value
            assertTrue(output.contains("Inc.\n\nDISK VOLUME 255 [NEW EMPLOYEE WORKSTATION]"))
            assertTrue(output.contains("1 FILE(S) IN 19 BLOCKS\n\n"))
        }

    @Test
    fun `CATALOG DIRECTORY LIST LS and CAT are all aliases for DIR`() =
        runTest {
            for (alias in listOf("CATALOG", "DIRECTORY", "LIST", "LS", "CAT")) {
                val engine = loginToShell()
                submit(engine, alias)
                assertTrue(
                    engine.liveLine.value.contains("APPLY.EXE"),
                    "expected DIR-like output for '$alias', got: ${engine.liveLine.value}",
                )
            }
        }

    @Test
    fun `IP prints a uid`() =
        runTest {
            val engine = loginToShell()
            submit(engine, "IP")
            assertTrue(engine.liveLine.value.contains("uid:"))
        }

    @Test
    fun `HELP omits NOTES for a regular user`() =
        runTest {
            val engine = loginToShell()
            submit(engine, "HELP")
            val output = engine.liveLine.value
            assertTrue(output.contains("APPEND"))
            assertTrue(output.contains("TAPEDISK"))
            assertFalse(output.contains("NOTES"))
        }

    @Test
    fun `HELP LIB and question mark are all aliases and include NOTES for admin`() =
        runTest {
            for (alias in listOf("HELP", "LIB", "?")) {
                val engine = loginAsAdmin()
                submit(engine, alias)
                assertTrue(
                    engine.liveLine.value.contains("NOTES"),
                    "expected NOTES in admin help output for '$alias', got: ${engine.liveLine.value}",
                )
            }
        }

    @Test
    fun `an unknown command reports the exact file-not-found error`() =
        runTest {
            val engine = loginToShell()
            submit(engine, "FOOBAR")
            assertTrue(engine.liveLine.value.contains("ERROR 24 [File 'FOOBAR' not found]"))
        }

    @Test
    fun `write commands all report disk write protected`() =
        runTest {
            for (cmd in listOf("APPEND", "ATTRIB", "COPY", "FORMAT", "ERASE", "RENAME")) {
                val engine = loginToShell()
                submit(engine, cmd)
                assertTrue(
                    engine.liveLine.value.contains("ERROR 15 [Disk is write protected]"),
                    "expected write-protected error for '$cmd', got: ${engine.liveLine.value}",
                )
            }
        }

    @Test
    fun `PLAY with no argument asks what to play`() =
        runTest {
            val engine = loginToShell()
            submit(engine, "PLAY")
            assertTrue(engine.liveLine.value.contains("ERROR 03 [What would you like to play?]"))
        }

    @Test
    fun `PLAY with an unrecognized argument produces no error and no crash`() =
        runTest {
            val engine = loginToShell()
            submit(engine, "PLAY CHESS")
            assertFalse(engine.liveLine.value.contains("ERROR"))
            assertTrue(engine.liveLine.value.contains("B:\\>"))
            assertFalse(engine.exitRequested.value)
        }

    @Test
    fun `INTERROGATE with no argument requires a parameter`() =
        runTest {
            val engine = loginToShell()
            submit(engine, "INTERROGATE")
            assertTrue(engine.liveLine.value.contains("ERROR 02 [Command requires at least one parameter]"))
        }

    @Test
    fun `INTERROGATE as admin reports unknown employee`() =
        runTest {
            val engine = loginAsAdmin()
            submit(engine, "INTERROGATE SOMEONE")
            assertTrue(engine.liveLine.value.contains("ERROR 07 [Unknown Employee]"))
        }

    @Test
    fun `INTERROGATE as a regular user is an illegal disciplinary action`() =
        runTest {
            val engine = loginToShell()
            submit(engine, "INTERROGATE SOMEONE")
            assertTrue(engine.liveLine.value.contains("ERROR 01 [Illegal attempt to initiate disciplinary action]"))
        }

    @Test
    fun `TAPEDISK reports unauthorized tape transfer`() =
        runTest {
            val engine = loginToShell()
            submit(engine, "TAPEDISK")
            assertTrue(engine.liveLine.value.contains("ERROR 18 [User not authorized to transfer system tapes]"))
        }

    @Test
    fun `NOTES is not found for a regular user`() =
        runTest {
            val engine = loginToShell()
            submit(engine, "NOTES")
            assertTrue(engine.liveLine.value.contains("ERROR 24 [File 'NOTES' not found]"))
        }

    @Test
    fun `a period cannot be typed at all - so NOTES-EXE degrades to NOTESEXE`() =
        runTest {
            // "." isn't an accepted character, so "NOTES.EXE" submits as "NOTESEXE" instead.
            val engine = loginToShell()
            submit(engine, "NOTES.EXE")
            assertTrue(engine.liveLine.value.contains("ERROR 24 [File 'NOTESEXE' not found]"))
        }

    @Test
    fun `NOTES opens the reader for admin`() =
        runTest {
            val engine = loginAsAdmin()
            submit(engine, "NOTES")
            assertTrue(engine.liveLine.value.contains("1953"))
        }

    @Test
    fun `blank input does nothing at all`() =
        runTest {
            val engine = loginToShell()
            val before = engine.liveLine.value
            pressKey(engine, "Enter")
            assertEquals(before, engine.liveLine.value)
        }

    @Test
    fun `APPLY starts the job application`() =
        runTest {
            val engine = loginToShell()
            submit(engine, "APPLY")
            assertTrue(engine.liveLine.value.contains("ENRICHMENT CENTER TEST SUBJECT APPLICATION"))
        }

    @Test
    fun `a period cannot be typed at all - so APPLY-EXE degrades to APPLYEXE`() =
        runTest {
            // Same period-is-unreachable quirk as NOTES.EXE - "APPLY.EXE" can never actually be typed.
            val engine = loginToShell()
            submit(engine, "APPLY.EXE")
            assertTrue(engine.liveLine.value.contains("ERROR 24 [File 'APPLYEXE' not found]"))
        }

    @Test
    fun `LOGOUT BYE LOGOFF and VALVE all end the session`() =
        runTest {
            for (cmd in listOf("LOGOUT", "BYE", "LOGOFF", "VALVE")) {
                val engine = loginToShell()
                submit(engine, cmd)
                assertTrue(engine.exitRequested.value, "expected exitRequested after '$cmd'")
                assertTrue(engine.liveLine.value.contains("[ERROR: STORE NOT FOUND]"))
            }
        }

    @Test
    fun `PLAY PORTAL also ends the session - pointing at the trailer`() =
        runTest {
            val engine = loginToShell()
            submit(engine, "PLAY PORTAL")
            assertTrue(engine.exitRequested.value)
            assertTrue(engine.liveLine.value.contains("[ERROR: TRAILER NOT FOUND]"))
        }

    @Test
    fun `THECAKEISALIE from the shell enters the cake easter egg`() =
        runTest {
            val engine = loginToShell()
            submit(engine, "THECAKEISALIE")
            assertTrue(engine.liveLine.value.contains("left the building"))
        }
}
