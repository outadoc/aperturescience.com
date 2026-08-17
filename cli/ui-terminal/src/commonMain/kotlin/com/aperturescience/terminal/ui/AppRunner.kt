package com.aperturescience.terminal.ui

import com.aperturescience.terminal.TerminalEngine
import com.jakewharton.mosaic.runMosaic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

// Mosaic has no built-in alt-screen support - this makes the program take over the whole
// terminal like vim/htop, restoring whatever was on screen before launch on exit.
private const val ENTER_ALT_SCREEN = "[?1049h[H"
private const val LEAVE_ALT_SCREEN = "[?1049l"

/** Shared entry point for every target's `main()`: enters/leaves the alt screen buffer around
 * driving Mosaic/[TerminalEngine], via [installTerminationHandler] to also cover SIGTERM/SIGHUP. */
fun runTerminalApp() {
    print(ENTER_ALT_SCREEN)
    flushStdout()

    installTerminationHandler {
        print(LEAVE_ALT_SCREEN)
        flushStdout()
    }

    runBlocking {
        val engine = TerminalEngine()

        // Ctrl+C is handled inside Mosaic's own frame loop; LOGOUT/PLAY PORTAL have no such
        // signal, so this watches exitRequested and cancels the composition itself.
        val mosaicJob = launch { runMosaic { App(engine) } }
        val watcherJob =
            launch {
                engine.exitRequested.first { it }
                mosaicJob.cancel()
            }
        mosaicJob.join()
        watcherJob.cancel()
    }
}
