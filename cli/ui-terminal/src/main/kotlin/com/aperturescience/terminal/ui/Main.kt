package com.aperturescience.terminal.ui

import com.aperturescience.terminal.TerminalEngine
import com.jakewharton.mosaic.runMosaic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

// Mosaic has no built-in alternate-screen-buffer support, so it's driven directly here. This is
// what makes the program take over the whole terminal like a fullscreen app (vim, htop, ...):
// whatever was on screen (and in scrollback) before launch is hidden while it runs, and restored
// exactly as it was on exit - the terminal, not just our own drawn region, "clears" on startup.
private const val ENTER_ALT_SCREEN = "[?1049h[H"
private const val LEAVE_ALT_SCREEN = "[?1049l"

fun main() {
    print(ENTER_ALT_SCREEN)
    System.out.flush()

    // A shutdown hook (rather than only a try/finally below) is kept as a fallback for exits
    // that bypass normal Kotlin control flow entirely - e.g. SIGTERM/SIGHUP from outside the
    // process (closing the terminal window, `kill <pid>`). Neither this program nor
    // TerminalEngine ever calls exitProcess()/System.exit() itself (that would be fatal to embed
    // in a test suite or a server) - every exit path below is a normal, cooperative coroutine
    // completion, so this hook is purely a safety net for signals it can't otherwise observe.
    Runtime.getRuntime().addShutdownHook(
        Thread {
            print(LEAVE_ALT_SCREEN)
            System.out.flush()
        },
    )

    runBlocking {
        val engine = TerminalEngine()

        // Ctrl+C is handled entirely within Mosaic's own frame loop (App.kt's onKeyEvent
        // deliberately leaves it unhandled so Mosaic's built-in handling cancels its own
        // composition job and runMosaic() returns normally). LOGOUT/PLAY PORTAL have no such
        // built-in signal to hook into, so this watches TerminalEngine.exitRequested and cancels
        // the composition ourselves when it fires - the same graceful, cooperative shutdown, just
        // triggered from our side instead of Mosaic's.
        val mosaicJob = launch { runMosaic { App(engine) } }
        val watcherJob = launch {
            engine.exitRequested.first { it }
            mosaicJob.cancel()
        }
        mosaicJob.join()
        watcherJob.cancel()
    }
}
