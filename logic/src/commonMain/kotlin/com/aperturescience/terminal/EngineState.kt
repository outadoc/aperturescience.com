package com.aperturescience.terminal

/**
 * A snapshot of every mutable field [TerminalEngine] carries between turns. Plain data class -
 * `:logic` has no serialization dependency; hosts define their own mirror (see AGENTS.md).
 */
data class EngineState(
    val mode: Mode,
    val isAdmin: Boolean,
    val uid: String,
    val pageContent: String,
    val input: String,
    val wrapWidth: Int,
    val isLocked: Boolean,
    val annotations: List<TextAnnotation> = emptyList(),
    /**
     * Set true once the session should end (LOGOUT/PLAY PORTAL) - was a separate
     * `exitRequested` StateFlow; now just another field, since `state` is the only StateFlow.
     */
    val exitRequested: Boolean = false,
    /**
     * Reducer-only bookkeeping: the not-yet-closed annotation's start offset mid-reveal. Always
     * `null` between turns. Paired with [pendingAnnotationTag] - only one annotation is ever
     * open at a time, so a single field is enough, not a stack.
     */
    val pendingAnnotationStart: Int? = null,
    /**
     * Reducer-only bookkeeping: which tag the not-yet-closed annotation will get once its end
     * marker is reached (see `START_CHAR_TO_TAG`). Always `null` between turns - not mirrored in
     * `ui-minitel`'s serializable state for the same reason [pendingAnnotationStart] isn't:
     * minitel always runs with `instantReveal = true`, so state is only ever read after a reveal
     * has fully completed.
     */
    val pendingAnnotationTag: String? = null,
)
