package com.aperturescience.terminal.ui

/** Registers [action] to run once the process is about to terminate - normal exit or
 * SIGTERM/SIGHUP - so the alt screen buffer (AppRunner.kt) always gets restored. */
expect fun installTerminationHandler(action: () -> Unit)

/** Flushes stdout - platform-specific because JVM buffers it and Kotlin/Native doesn't. */
expect fun flushStdout()
