package com.aperturescience.terminal.ui

// A shutdown hook (rather than only a try/finally in AppRunner.kt) is kept as a fallback for
// exits that bypass normal Kotlin control flow entirely - e.g. SIGTERM/SIGHUP from outside the
// process (closing the terminal window, `kill <pid>`). The JVM runs shutdown hooks on every exit
// path, including a normal return from main(), so this alone covers both cases.
actual fun installTerminationHandler(action: () -> Unit) {
    Runtime.getRuntime().addShutdownHook(Thread(action))
}

actual fun flushStdout() {
    System.out.flush()
}
