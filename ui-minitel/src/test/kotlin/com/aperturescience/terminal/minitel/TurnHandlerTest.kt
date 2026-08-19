package com.aperturescience.terminal.minitel

import fr.outadoc.minipavi.core.model.FunctionKey
import fr.outadoc.minipavi.core.model.GatewayRequest
import fr.outadoc.minipavi.core.model.GatewayRequest.Event
import fr.outadoc.minipavi.core.model.ServiceResponse
import kotlinx.coroutines.runBlocking
import kotlinx.io.bytestring.indexOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TurnHandlerTest {
    // logged into the admin shell, with no open input zone shown yet - equivalent to what a real
    // session would look like right before typing "NOTES".
    private val adminShellState =
        MinitelSessionState(
            mode = MinitelMode.Shell(),
            isAdmin = true,
            uid = "TESTUID0001",
            pageContent = "",
            input = "",
            wrapWidth = ScreenChunker.WRAP_WIDTH,
            isLocked = false,
            chunkIndex = 0,
        )

    private fun turn(
        state: MinitelSessionState,
        key: FunctionKey,
        userInput: List<String> = emptyList(),
    ): ServiceResponse<MinitelSessionState> =
        runBlocking {
            handleTurn(
                GatewayRequest(
                    gatewayVersion = "1.0",
                    minitelVersion = "???",
                    userId = "test",
                    remoteAddress = "127.0.0.1",
                    socketType = GatewayRequest.SocketType.WebSocket,
                    state = state,
                    event = Event.KeyboardInput(key),
                    userInput = userInput,
                ),
            )
        }

    /**
     * NOTES page 1 spills past one screen - Suite reveals the rest, Envoi does nothing until
     * caught up, then advances like any accepted key.
     */
    @Test
    fun `a NOTES-EXE page spanning multiple screens advances on Suite - then Envoi once caught up`() {
        // "NOTES" typed at the shell prompt, submitted with Envoi.
        val page1Head = turn(adminShellState, FunctionKey.Envoi, userInput = listOf("NOTES"))
        assertEquals(MinitelMode.Notes(page = 1), page1Head.state.mode)
        assertEquals(0, page1Head.state.chunkIndex)

        // Envoi does nothing while there's more of this same page queued up - only Suite reveals
        // it, consistent with every other multi-screen page (Q21 pagination included).
        val envoiMidPage = turn(page1Head.state, FunctionKey.Envoi)
        assertEquals(page1Head.content, envoiMidPage.content)
        assertEquals(page1Head.state.chunkIndex, envoiMidPage.state.chunkIndex)

        val page1Tail = turn(page1Head.state, FunctionKey.Suite)
        assertNotEquals(page1Head.content, page1Tail.content)
        assertEquals(page1Head.state.mode, page1Tail.state.mode)
        assertEquals(page1Head.state.chunkIndex + 1, page1Tail.state.chunkIndex)

        // Now truly caught up with page 1 - the next Envoi must move on to NOTES page 2.
        val page2Head = turn(page1Tail.state, FunctionKey.Envoi)
        assertEquals(MinitelMode.Notes(page = 2), page2Head.state.mode)
        assertEquals(0, page2Head.state.chunkIndex)
    }

    /**
     * Walks all four two-screen NOTES pages (Suite then Envoi each), landing back on the shell.
     */
    @Test
    fun `paging through every NOTES-EXE page returns to the shell afterwards`() {
        var response = turn(adminShellState, FunctionKey.Envoi, userInput = listOf("NOTES")) // page 1, chunk 0
        repeat(3) {
            response = turn(response.state, FunctionKey.Suite) // page N, chunk 1
            response = turn(response.state, FunctionKey.Envoi) // page N+1, chunk 0
        }
        response = turn(response.state, FunctionKey.Suite) // page 4, chunk 1
        assertEquals(MinitelMode.Notes(page = 4), response.state.mode)
        assertEquals(1, response.state.chunkIndex)

        val backToShell = turn(response.state, FunctionKey.Envoi)
        assertEquals(MinitelMode.Shell(), backToShell.state.mode)
    }

    /**
     * Regression test: a full-width wrapped line used to double-advance the cursor, landing the
     * input zone one row above "ADMIN>" instead of on it.
     */
    @Test
    fun `the admin shell input zone lines up with the ADMIN prompt it's shown on`() {
        val connected =
            runBlocking {
                handleTurn(
                    GatewayRequest(
                        gatewayVersion = "1.0",
                        minitelVersion = "???",
                        userId = "test",
                        remoteAddress = "127.0.0.1",
                        socketType = GatewayRequest.SocketType.WebSocket,
                        state = MinitelSessionState.initial(),
                        event = Event.Connection,
                        userInput = emptyList(),
                    ),
                )
            }
        val usernamePrompt = turn(connected.state, FunctionKey.Envoi, userInput = listOf("LOGON"))
        val passwordPrompt = turn(usernamePrompt.state, FunctionKey.Envoi, userInput = listOf("CJOHNSON"))
        val shellPrompt = turn(passwordPrompt.state, FunctionKey.Envoi, userInput = listOf("TIER3"))

        val command = shellPrompt.command
        check(command is ServiceResponse.Command.InputText)
        // Header wraps to 2 lines + a blank line + "ADMIN> " = row 4, where the input zone belongs.
        assertEquals(4, command.line)
    }

    /**
     * The UID display screen's bracketed UID must blink - reached via a regular (non-admin)
     * login, "APPLY", then "CONTINUE".
     */
    @Test
    fun `the UID display screen wraps the bracketed UID in VideotexBuilder's withBlink`() {
        val connected =
            runBlocking {
                handleTurn(
                    GatewayRequest(
                        gatewayVersion = "1.0",
                        minitelVersion = "???",
                        userId = "test",
                        remoteAddress = "127.0.0.1",
                        socketType = GatewayRequest.SocketType.WebSocket,
                        state = MinitelSessionState.initial(),
                        event = Event.Connection,
                        userInput = emptyList(),
                    ),
                )
            }
        val usernamePrompt = turn(connected.state, FunctionKey.Envoi, userInput = listOf("LOGON"))
        val passwordPrompt = turn(usernamePrompt.state, FunctionKey.Envoi, userInput = listOf("TESTER"))
        val shellPrompt = turn(passwordPrompt.state, FunctionKey.Envoi, userInput = listOf("PORTAL"))
        // The intro paragraph spans more than one 40x24 Minitel screen - page through with Suite
        // until the input zone opens, same as a real session would.
        var introScreen = turn(shellPrompt.state, FunctionKey.Envoi, userInput = listOf("APPLY"))
        while (introScreen.command !is ServiceResponse.Command.InputText) {
            introScreen = turn(introScreen.state, FunctionKey.Suite)
        }
        val uidScreen = turn(introScreen.state, FunctionKey.Envoi, userInput = listOf("CONTINUE"))

        // VDT_BLINK = ESC H (0x1B, 0x48), VDT_FIXED = ESC I (0x1B, 0x49) - see minipavi-kotlin's
        // (internal) VdtConstants.
        val blinkStart = uidScreen.content.indexOf(byteArrayOf(0x1B, 0x48))
        val blinkEnd = uidScreen.content.indexOf(byteArrayOf(0x1B, 0x49))
        assertTrue(blinkStart >= 0, "expected a VDT_BLINK sequence in the UID display screen")
        assertTrue(blinkEnd > blinkStart, "expected VDT_FIXED to follow VDT_BLINK")
    }
}
