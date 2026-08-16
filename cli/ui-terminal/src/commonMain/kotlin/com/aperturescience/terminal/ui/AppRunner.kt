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

/**
 * Shared entry point for every target's `main()`: enters the alternate screen buffer, drives
 * Mosaic/[TerminalEngine] to completion, and leaves the alternate screen buffer again - via
 * [installTerminationHandler] rather than only a plain `finally`, since that also has to cover
 * exits that bypass normal Kotlin control flow entirely (SIGTERM/SIGHUP from outside the
 * process). Nothing here ever calls `exitProcess()`/`System.exit()`: every exit path is a normal,
 * cooperative coroutine completion, so a platform's `main()` returning is always enough.
 */
fun runTerminalApp() {
    print(ENTER_ALT_SCREEN)
    flushStdout()

    installTerminationHandler {
        print(LEAVE_ALT_SCREEN)
        flushStdout()
    }

    runBlocking {
        val engine = TerminalEngine()

        // Ctrl+C is handled entirely within Mosaic's own frame loop (App.kt's onKeyEvent
        // deliberately leaves it unhandled so Mosaic's built-in handling cancels its own
        // composition job and runMosaic() returns normally). LOGOUT/PLAY PORTAL have no such
        // built-in signal to hook into, so this watches TerminalEngine.exitRequested and cancels
        // the composition ourselves when it fires - the same graceful, cooperative shutdown, just
        // triggered from our side instead of Mosaic's.
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
