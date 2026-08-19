package com.aperturescience.terminal

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle

//region Test-only TerminalEngine driver helpers

/**
 * Sends a single raw key (as [TerminalEngine.onKeyEvent] expects it) and settles - each helper in
 * this file advances virtual time so callers never need real-time waits for the typewriter
 * animation.
 */
fun TestScope.pressKey(
    engine: TerminalEngine,
    key: String,
) {
    engine.onKeyEvent(key)
    advanceUntilIdle()
}

/**
 * Types [text] one character at a time via [TerminalEngine.onKeyEvent], then presses Enter and
 * settles.
 */
fun TestScope.submit(
    engine: TerminalEngine,
    text: String,
) {
    for (c in text) {
        engine.onKeyEvent(c.toString())
    }
    engine.onKeyEvent("Enter")
    advanceUntilIdle()
}

/**
 * Boots [engine] and settles once the initial `"> "` prompt has fully revealed.
 */
fun TestScope.bootAndSettle(engine: TerminalEngine): TerminalEngine {
    engine.boot(this)
    advanceUntilIdle()
    return engine
}

/**
 * Boots and logs in as a regular (non-admin) user, ending at the `B:\>` shell prompt.
 */
fun TestScope.loginToShell(
    engine: TerminalEngine = TerminalEngine(),
    username: String = "TESTER",
    password: String = "PORTAL",
): TerminalEngine {
    bootAndSettle(engine)
    submit(engine, "LOGON")
    submit(engine, username)
    submit(engine, password)
    return engine
}

/**
 * Boots and logs in as the `CJOHNSON` admin user, ending at the `ADMIN>` shell prompt.
 */
fun TestScope.loginAsAdmin(engine: TerminalEngine = TerminalEngine()): TerminalEngine {
    bootAndSettle(engine)
    submit(engine, "LOGON")
    submit(engine, "CJOHNSON")
    submit(engine, "TIER3")
    return engine
}
//endregion
