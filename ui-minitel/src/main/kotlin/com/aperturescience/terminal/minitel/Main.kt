package com.aperturescience.terminal.minitel

import fr.outadoc.minipavi.core.ktor.minitelService
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking

/**
 * Embedded Ktor server the MiniPavi gateway calls once per user action - see [handleTurn].
 */
fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        minitelService<MinitelSessionState>(
            path = "/",
            version = "0.1.0",
            initialState = { MinitelSessionState.initial() },
        ) { request ->
            // minitelService's block isn't suspend, so run the turn to completion here.
            runBlocking { handleTurn(request) }
        }
    }.start(wait = true)
}
