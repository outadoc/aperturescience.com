package com.aperturescience.terminal.minitel

import fr.outadoc.minipavi.core.ktor.minitelService
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.aperturescience.terminal.minitel")

private const val PORT = 8080

/**
 * Embedded Ktor server the MiniPavi gateway calls once per user action - see [handleTurn].
 */
fun main() {
    logger.info("Starting Aperture Science Minitel service on port {}", PORT)

    embeddedServer(Netty, port = PORT, host = "0.0.0.0") {
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
