package com.aperturescience.terminal.ui

// The JVM runs shutdown hooks on every exit path (normal return or SIGTERM/SIGHUP), so this alone
// covers both cases.
actual fun installTerminationHandler(action: () -> Unit) {
    Runtime.getRuntime().addShutdownHook(Thread(action))
}

actual fun flushStdout() {
    System.out.flush()
}
