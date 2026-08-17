package com.aperturescience.terminal.minitel

import fr.outadoc.minipavi.core.model.FunctionKey
import fr.outadoc.minipavi.core.model.GatewayRequest
import fr.outadoc.minipavi.core.model.GatewayRequest.Event
import fr.outadoc.minipavi.core.model.ServiceResponse
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

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

    /** NOTES.EXE's first history page is long enough to spill past one 24-line Minitel screen (27
     * wrapped lines) - Suite is the consistent "more of this screen" key everywhere (same as Q21
     * pagination), so it must reveal the rest of the page; Envoi has no effect until the page is
     * fully caught up, at which point it (like any accepted key) moves on to the next NOTES
     * page. */
    @Test
    fun `a NOTES-EXE page spanning multiple screens advances on Suite, then Envoi once caught up`() {
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

    /** All four NOTES.EXE pages wrap to two screens each at [ScreenChunker.WRAP_WIDTH] - walks
     * all the way through (Suite for the extra screen, Envoi for the actual page turn), landing
     * back on the admin shell once page 4 (the one ending in "[END]") is fully read. */
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

    /** Regression test for a related word-wrap bug: a wrapped line exactly [ScreenChunker.COLUMNS]
     * characters wide fills the Minitel's row completely, so the terminal auto-wraps to the next
     * row on its own - and then `render()`'s own explicit CRLF after every line advances the
     * cursor a *second* time, one row further than this adapter's row-counting expects. That
     * showed up as the admin shell's input zone (positioned using the assumed row count) landing
     * one row above the "ADMIN>" prompt it belongs on, because the header
     * ("GLaDOS v1.07a (c) 1982 Aperture Science, Inc.") happens to wrap with a first line exactly
     * 40 characters wide at the old [ScreenChunker.COLUMNS]-width wrap. */
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
        // "GLaDOS v1.07a (c) 1982 Aperture Science, Inc." wraps to 2 lines at WRAP_WIDTH (neither
        // reaching the full 40-column width), followed by a blank line and "ADMIN> " - 4 physical
        // rows total, with the input zone on that same 4th row, right after the prompt text.
        assertEquals(4, command.line)
    }
}
