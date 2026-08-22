package com.aperturescience.terminal.minitel

import fr.outadoc.minipavi.core.model.FunctionKey
import fr.outadoc.minipavi.core.model.GatewayRequest
import fr.outadoc.minipavi.core.model.GatewayRequest.Event
import kotlinx.coroutines.runBlocking
import kotlinx.io.bytestring.indexOf
import kotlin.test.Test
import kotlin.test.assertTrue

class StoreUnderlineProbeTest {
    @Test
    fun `probe LOGOUT underline bytes`() {
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

        fun turn(
            state: MinitelSessionState,
            key: FunctionKey,
            userInput: List<String> = emptyList(),
        ) = runBlocking {
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
        val usernamePrompt = turn(connected.state, FunctionKey.Envoi, listOf("LOGON"))
        val passwordPrompt = turn(usernamePrompt.state, FunctionKey.Envoi, listOf("TESTER"))
        val shellPrompt = turn(passwordPrompt.state, FunctionKey.Envoi, listOf("PORTAL"))
        val logoutScreen = turn(shellPrompt.state, FunctionKey.Envoi, listOf("LOGOUT"))

        println("=== bytes (hex) ===")
        println(logoutScreen.content.toByteArray().joinToString(" ") { "%02x".format(it) })
        println("=== decoded (best-effort latin1) ===")
        println(logoutScreen.content.toByteArray().toString(Charsets.ISO_8859_1))

        val underlineStart = logoutScreen.content.indexOf(byteArrayOf(0x1B, 0x5A))
        val underlineEnd = logoutScreen.content.indexOf(byteArrayOf(0x1B, 0x59))
        println("underlineStart=$underlineStart underlineEnd=$underlineEnd")
        assertTrue(underlineStart >= 0, "expected VDT_STARTUNDERLINE (ESC Z) in LOGOUT screen")
        assertTrue(underlineEnd > underlineStart, "expected VDT_STOPUNDERLINE (ESC Y) to follow")
    }
}
