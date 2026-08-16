package com.aperturescience.terminal

import com.jakewharton.mosaic.runMosaicBlocking

// Mosaic has no built-in alternate-screen-buffer support, so it's driven directly here. This is
// what makes the program take over the whole terminal like a fullscreen app (vim, htop, ...):
// whatever was on screen (and in scrollback) before launch is hidden while it runs, and restored
// exactly as it was on exit - the terminal, not just our own drawn region, "clears" on startup.
private const val ENTER_ALT_SCREEN = "[?1049h[H"
private const val LEAVE_ALT_SCREEN = "[?1049l"

fun main() {
    print(ENTER_ALT_SCREEN)
    System.out.flush()

    // A shutdown hook (rather than a try/finally around runMosaicBlocking) is required: the
    // engine exits via exitProcess() from inside a coroutine on Ctrl+C/LOGOUT/PLAY PORTAL, which
    // never unwinds back through main()'s call stack. System.exit() still runs shutdown hooks, so
    // this is the one place that reliably fires on every exit path.
    Runtime.getRuntime().addShutdownHook(
        Thread {
            print(LEAVE_ALT_SCREEN)
            System.out.flush()
        },
    )

    runMosaicBlocking {
        App()
    }
}
