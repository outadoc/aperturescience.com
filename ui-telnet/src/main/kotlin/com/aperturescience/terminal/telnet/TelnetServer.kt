package com.aperturescience.terminal.telnet

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.aperturescience.terminal.telnet")

/**
 * Accepts telnet connections and runs one [TelnetSession] per client under a supervisor scope, so
 * one client's failure/disconnect can't take down the server or any other session - the
 * multi-user posture `ui-minitel` has (unlike `ui-terminal`, which binds to its own process's
 * single controlling TTY).
 */
class TelnetServer(
    private val port: Int,
) {
    suspend fun run() {
        val selectorManager = SelectorManager(Dispatchers.IO)
        val serverSocket = aSocket(selectorManager).tcp().bind(InetSocketAddress("0.0.0.0", port))
        logger.info("Starting Aperture Science Telnet service on port {}", port)

        val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        while (true) {
            val socket = serverSocket.accept()
            logger.info("Accepted connection from {}", socket.remoteAddress)
            sessionScope.launch {
                runCatching { TelnetSession(socket).run() }
                    .onFailure { logger.warn("Session ended with error", it) }
            }
        }
    }
}
