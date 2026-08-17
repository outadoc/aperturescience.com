package com.aperturescience.terminal.minitel

import fr.outadoc.minipavi.core.ktor.minitelService
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking

/**
 * Entry point for the Minitel/Vidéotex frontend: an embedded Ktor server the MiniPavi gateway
 * calls once per user action, each call producing exactly one Vidéotex frame - see [handleTurn]
 * for how a `TerminalEngine` turn is bridged onto that request/response model.
 */
fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        minitelService<MinitelSessionState>(
            path = "/",
            version = "0.1.0",
            initialState = { MinitelSessionState.initial() },
        ) { request ->
            // minitelService's block isn't itself a suspend function, so a turn - always
            // instantReveal = true, hence no real delay() anywhere in it - is run to completion
            // here rather than fired off asynchronously.
            runBlocking { handleTurn(request) }
        }
    }.start(wait = true)
}
