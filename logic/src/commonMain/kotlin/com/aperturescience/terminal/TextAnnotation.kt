package com.aperturescience.terminal

/**
 * A tagged span over some rendered text - `logic` never interprets [tag] itself; each frontend
 * decides what a given tag means and renders it however fits its medium (blink, color...).
 */
data class TextAnnotation(
    val tag: String,
    val range: IntRange,
)

/**
 * [text] plus zero or more [annotations] over it - a convenience bundle for callers that want
 * both together (see [EngineState.displayText]/[EngineState.annotations]).
 */
data class AnnotatedText(
    val text: String,
    val annotations: List<TextAnnotation> = emptyList(),
)

/**
 * Tag for the one blinking span in use today - the UID display screen, see [TerminalEngine].
 */
const val BLINK_TAG = "blink"
