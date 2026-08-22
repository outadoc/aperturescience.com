package com.aperturescience.terminal.telnet

import com.aperturescience.terminal.Intent
import com.aperturescience.terminal.TerminalEngine
import com.aperturescience.terminal.WRAP_WIDTH
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.aperturescience.terminal.telnet")

private const val DEFAULT_COLUMNS = 80

/**
 * Drives one telnet connection's [TerminalEngine] for its whole lifetime - the streaming session
 * model `ui-terminal` uses (one long-lived engine, `dispatch` per keystroke/event), just over a
 * TCP socket instead of a real TTY. Structurally mirrors `AppRunner.kt`'s `runTerminalApp`: a
 * render job collecting `engine.state`, a read job dispatching intents, and a watcher that stops
 * the read job once `exitRequested` fires (LOGOUT/PLAY PORTAL have no in-band signal otherwise).
 */
class TelnetSession(
    private val socket: Socket,
) {
    suspend fun run() =
        coroutineScope {
            val readChannel = socket.openReadChannel()
            val writeChannel = socket.openWriteChannel(autoFlush = true)

            writeChannel.writeFully(TELNET_INITIAL_NEGOTIATION)

            val parser = TelnetInputParser()
            val engine = TerminalEngine()
            engine.dispatch(Intent.ViewportResized(DEFAULT_COLUMNS))
            engine.dispatch(Intent.Boot)

            val renderJob =
                launch {
                    engine.state.collect { state ->
                        writeChannel.writeStringUtf8(renderFrame(state))
                    }
                }

            // Ctrl+C is our own escape hatch, delivered in-band as a plain data byte (0x03) -
            // TelnetInputParser turns it into TelnetInputEvent.Disconnect.
            val readJob =
                launch {
                    while (isActive) {
                        val byte =
                            try {
                                readChannel.readByte()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                null
                            } ?: break

                        for (event in parser.accept(byte.toInt() and 0xFF)) {
                            when (event) {
                                is TelnetInputEvent.KeyPressed -> engine.dispatch(Intent.KeyPressed(event.key))
                                is TelnetInputEvent.Resize ->
                                    engine.dispatch(Intent.ViewportResized(minOf(event.columns, WRAP_WIDTH)))
                                TelnetInputEvent.Disconnect -> return@launch
                            }
                        }
                    }
                }

            // LOGOUT/PLAY PORTAL have no in-band signal, so this watches exitRequested instead.
            val watcherJob =
                launch {
                    engine.state.first { it.exitRequested }
                    readJob.cancel()
                }

            readJob.join()
            watcherJob.cancel()
            renderJob.cancel()

            runCatching { socket.close() }
            logger.info("Session ended for {}", socket.remoteAddress)
        }
}
