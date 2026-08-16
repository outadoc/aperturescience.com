package com.aperturescience.terminal.ui

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.staticCFunction
import platform.posix.SIGHUP
import platform.posix.SIGTERM
import platform.posix.atexit
import platform.posix.exit
import platform.posix.fflush
import platform.posix.signal

// staticCFunction bodies can't capture local state, only top-level/global vars - so the action
// passed to installTerminationHandler is stashed here and invoked from a plain top-level
// reference to it.
private var terminationAction: (() -> Unit)? = null

// atexit alone covers a normal return from main() (the C runtime always runs atexit handlers
// then), but not SIGTERM/SIGHUP delivered from outside the process (closing the terminal window,
// `kill <pid>`) - those terminate the process without ever reaching atexit unless something
// catches the signal and calls exit() itself, which is what the signal() handler below does. This
// mirrors the JVM actual: a shutdown hook there is likewise run for both normal exit and external
// signals, by the JVM itself.
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
