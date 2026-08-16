package com.aperturescience.terminal.ui

/**
 * Registers [action] to run once, whenever this process is about to terminate - both on normal
 * return from `main()` and on external signals (SIGTERM/SIGHUP - "closing the terminal window",
 * `kill <pid>`) that bypass normal Kotlin control flow entirely. Used to restore the terminal's
 * alternate screen buffer (see [ENTER_ALT_SCREEN]/[LEAVE_ALT_SCREEN] in AppRunner.kt) no matter
 * how the process ends, since every in-process exit path here is already a normal, cooperative
 * coroutine completion that a plain `finally` would catch on its own.
 */
expect fun installTerminationHandler(action: () -> Unit)

/** Flushes stdout - platform-specific because JVM buffers it and Kotlin/Native doesn't. */
expect fun flushStdout()
