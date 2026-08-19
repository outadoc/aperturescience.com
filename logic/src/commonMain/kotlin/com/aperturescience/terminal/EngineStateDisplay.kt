package com.aperturescience.terminal

/**
 * What's actually on screen right now: [EngineState.pageContent] plus the not-yet-submitted
 * input line, echoed as asterisks during [Mode.Login.Password].
 */
val EngineState.displayText: String
    get() {
        val echoed = if (mode is Mode.Login.Password) "*".repeat(input.length) else input
        return pageContent + echoed
    }
