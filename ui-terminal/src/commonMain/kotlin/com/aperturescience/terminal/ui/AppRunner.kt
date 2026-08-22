package com.aperturescience.terminal.ui

import com.aperturescience.terminal.Intent
import com.aperturescience.terminal.TerminalEngine
import com.aperturescience.terminal.WRAP_WIDTH
import com.jakewharton.mosaic.terminal.KeyboardEvent
import com.jakewharton.mosaic.terminal.ResizeEvent
import com.jakewharton.mosaic.tty.Tty
import com.jakewharton.mosaic.tty.terminal.asTerminalIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val ESC = "\u001B"

/** With [LEAVE_ALT_SCREEN], takes over the whole terminal like vim/htop and restores it on exit. */
private const val ENTER_ALT_SCREEN = "$ESC[?1049h$ESC[H"
private const val LEAVE_ALT_SCREEN = "$ESC[?1049l"

/** Belt-and-braces cursor restore on shutdown, in case a redraw was interrupted mid-frame. */
private const val CURSOR_SHOW = "$ESC[?25h"

/**
 * Shared entry point for every target's `main()`: enters/leaves the alt screen buffer around
 * driving the terminal event loop and renderer.
 */
fun runTerminalApp() {
    print(ENTER_ALT_SCREEN)
    flushStdout()

    installTerminationHandler {
        print(CURSOR_SHOW + LEAVE_ALT_SCREEN)
        flushStdout()
    }

    runBlocking {
        val tty = Tty.tryBind()
        if (tty == null) {
            print("This program requires an interactive terminal.\r\n")
            flushStdout()
            return@runBlocking
        }

        val terminal = tty.asTerminalIn(this)
        terminal.use {
            val engine = TerminalEngine()
            engine.dispatch(Intent.ViewportResized(minOf(terminal.state.size.value.columns, WRAP_WIDTH)))
            engine.dispatch(Intent.Boot)

            val renderJob =
                launch {
                    engine.state.collect { state ->
                        print(renderFrame(state))
                        flushStdout()
                    }
                }

            // Ctrl+C is our own escape hatch - raw mode delivers it in-band as a plain
            // KeyboardEvent(codepoint = 'c', ctrl = true), not as SIGINT.
            val eventJob =
                launch {
                    for (event in terminal.events) {
                        when (event) {
                            is KeyboardEvent -> {
                                val key = event.toKeyNameOrNull() ?: continue
                                if (event.ctrl && key == "c") break
                                engine.dispatch(Intent.KeyPressed(key))
                            }
                            is ResizeEvent -> {
                                engine.dispatch(Intent.ViewportResized(minOf(event.columns, WRAP_WIDTH)))
                            }
                            else -> {}
                        }
                    }
                }

            // LOGOUT/PLAY PORTAL have no in-band signal, so this watches exitRequested instead.
            val watcherJob =
                launch {
                    engine.state.first { it.exitRequested }
                    eventJob.cancel()
                }

            eventJob.join()
            watcherJob.cancel()
            renderJob.cancel()
        }
    }
}
