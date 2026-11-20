package com.aperturescience.terminal.minitel

import fr.outadoc.minipavi.core.ktor.minitelService
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
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
            version = SERVICE_VERSION,
            initialState = { MinitelSessionState.initial() },
        ) { request ->
            handleTurn(request)
        }
    }.start(wait = true)
}
