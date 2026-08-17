package com.aperturescience.terminal

/** A snapshot of every mutable field [TerminalEngine] carries between turns. Plain data class -
 * `:logic` has no serialization dependency; hosts define their own mirror (see AGENTS.md). */
data class EngineState(
    val mode: Mode,
    val isAdmin: Boolean,
    val uid: String,
    val pageContent: String,
    val input: String,
    val wrapWidth: Int,
    val isLocked: Boolean,
)
