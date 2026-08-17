package com.aperturescience.terminal

import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoginFlowTest {
    @Test
    fun `boot shows the bare login prompt`() =
        runTest {
            val engine = bootAndSettle(TerminalEngine())
            assertEquals("> ", engine.liveLine.value)
        }

    @Test
    fun `unrecognized input at the login prompt is silently ignored`() =
        runTest {
            val engine = bootAndSettle(TerminalEngine())
            submit(engine, "NONSENSE")
            assertEquals("> ", engine.liveLine.value)
        }

    @Test
    fun `LOGON LOGIN and USER all advance to the username prompt`() =
        runTest {
            for (keyword in listOf("LOGON", "LOGIN", "USER")) {
                val engine = bootAndSettle(TerminalEngine())
                submit(engine, keyword)
                assertTrue(
                    engine.liveLine.value.contains("Username>"),
                    "expected Username prompt after '$keyword', got: ${engine.liveLine.value}",
                )
            }
        }

    @Test
    fun `HELP at the login prompt shows a crisis message then returns to normal login`() =
        runTest {
            val engine = bootAndSettle(TerminalEngine())
            submit(engine, "HELP")
            val crisisMessage = engine.liveLine.value
            assertTrue(crisisMessage.contains("mobilized"))

            // The next screen behaves exactly like the original bare "> " prompt again.
            submit(engine, "LOGON")
            assertTrue(engine.liveLine.value.contains("Username>"))
        }

    @Test
    fun `question mark at the login prompt behaves the same as HELP`() =
        runTest {
            val engine = bootAndSettle(TerminalEngine())
            submit(engine, "?")
            assertTrue(engine.liveLine.value.contains("mobilized"))
        }

    @Test
    fun `usernames of two characters or fewer are rejected`() =
        runTest {
            val engine = bootAndSettle(TerminalEngine())
            submit(engine, "LOGON")
            submit(engine, "AB")
            assertTrue(engine.liveLine.value.contains("Username>"))
            assertFalse(engine.liveLine.value.contains("Password>"))
        }

    @Test
    fun `a username of three or more characters advances to the password prompt`() =
        runTest {
            val engine = bootAndSettle(TerminalEngine())
            submit(engine, "LOGON")
            submit(engine, "ABC")
            assertTrue(engine.liveLine.value.contains("Password>"))
        }

    @Test
    fun `PORTAL and PORTALS both log a regular user into the shell`() =
        runTest {
            for (password in listOf("PORTAL", "PORTALS")) {
                val engine = loginToShell(username = "TESTER", password = password)
                assertTrue(engine.liveLine.value.contains("B:\\>"))
                assertTrue(engine.liveLine.value.contains("GLaDOS v1.07 "))
                assertFalse(engine.liveLine.value.contains("v1.07a"))
            }
        }

    @Test
    fun `wrong password shows an error and lets the user retry`() =
        runTest {
            val engine = bootAndSettle(TerminalEngine())
            submit(engine, "LOGON")
            submit(engine, "TESTER")
            submit(engine, "WRONGPASSWORD")
            assertTrue(engine.liveLine.value.contains("ERROR 07 [Incorrect Password]"))
            assertTrue(engine.liveLine.value.contains("Password>"))

            submit(engine, "PORTAL")
            assertTrue(engine.liveLine.value.contains("B:\\>"))
        }

    @Test
    fun `CJOHNSON with TIER3 logs into the admin shell`() =
        runTest {
            val engine = loginAsAdmin()
            assertTrue(engine.liveLine.value.contains("ADMIN>"))
            assertTrue(engine.liveLine.value.contains("v1.07a"))
        }

    @Test
    fun `CJOHNSON requires TIER3 - not PORTAL - on the first attempt`() =
        runTest {
            val engine = bootAndSettle(TerminalEngine())
            submit(engine, "LOGON")
            submit(engine, "CJOHNSON")
            submit(engine, "PORTAL")
            assertTrue(engine.liveLine.value.contains("ERROR 07 [Incorrect Password]"))
        }

    @Test
    fun `a failed CJOHNSON attempt drops admin status - so PORTAL then works as a regular user`() =
        runTest {
            // This mirrors a real quirk in the original: is_cj is reset to false as soon as a TIER3
            // attempt fails, so the very next attempt is evaluated as a regular (non-admin) login.
            val engine = bootAndSettle(TerminalEngine())
            submit(engine, "LOGON")
            submit(engine, "CJOHNSON")
            submit(engine, "WRONGPASSWORD")
            assertTrue(engine.liveLine.value.contains("ERROR 07 [Incorrect Password]"))

            submit(engine, "PORTAL")
            assertTrue(engine.liveLine.value.contains("B:\\>"))
            assertFalse(engine.liveLine.value.contains("ADMIN>"))
        }

    @Test
    fun `password entry is echoed as asterisks - not the typed characters`() =
        runTest {
            val engine = bootAndSettle(TerminalEngine())
            submit(engine, "LOGON")
            submit(engine, "TESTER")

            engine.onKeyEvent("P")
            advanceUntilIdle()
            assertTrue(engine.liveLine.value.endsWith("*"))
            assertFalse(engine.liveLine.value.endsWith("P"))

            engine.onKeyEvent("O")
            engine.onKeyEvent("R")
            advanceUntilIdle()
            assertTrue(engine.liveLine.value.endsWith("***"))
            assertFalse(engine.liveLine.value.contains("POR"))
        }
}
