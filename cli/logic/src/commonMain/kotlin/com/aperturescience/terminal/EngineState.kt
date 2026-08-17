package com.aperturescience.terminal

/**
 * A snapshot of every mutable field [TerminalEngine] carries between turns. Plain data class, no
 * `kotlinx.serialization` or other framework annotation - `:logic` stays free of any such
 * dependency (see AGENTS.md). Hosts that need to persist a session across calls that don't keep a
 * [TerminalEngine] instance alive in memory (e.g. a stateless HTTP request/response cycle) define
 * their own serializable mirror of this shape and convert at the boundary; see
 * [TerminalEngine.captureState] and the `initialState` constructor parameter.
 */
data class EngineState(
    val mode: Mode,
    val isAdmin: Boolean,
    val uid: String,
    val pageContent: String,
    val input: String,
    val wrapWidth: Int,
    val isLocked: Boolean,
)
