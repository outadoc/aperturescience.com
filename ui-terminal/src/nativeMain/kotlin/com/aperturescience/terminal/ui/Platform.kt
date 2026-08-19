package com.aperturescience.terminal.ui

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.staticCFunction
import platform.posix.SIGHUP
import platform.posix.SIGTERM
import platform.posix.atexit
import platform.posix.exit
import platform.posix.fflush
import platform.posix.signal

/**
 * `staticCFunction` bodies can't capture local state, so the action is stashed here instead.
 */
private var terminationAction: (() -> Unit)? = null

/**
 * `atexit` alone covers a normal return from `main()`, not external SIGTERM/SIGHUP - those need
 * their own `signal()` handler to call `exit()` (which then runs the atexit handlers).
 */
@OptIn(ExperimentalForeignApi::class)
actual fun installTerminationHandler(action: () -> Unit) {
    terminationAction = action
    atexit(staticCFunction<Unit> { terminationAction?.invoke() })

    val exitOnSignal = staticCFunction<Int, Unit> { _ -> exit(0) }
    signal(SIGTERM, exitOnSignal)
    signal(SIGHUP, exitOnSignal)
}

@OptIn(ExperimentalForeignApi::class)
actual fun flushStdout() {
    fflush(null)
}
