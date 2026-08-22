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

/**
 * Every stubbed easter egg `logic` can't fully implement itself (browser navigation, video
 * playback) - each carries the [TextAnnotation.tag] string a frontend matches on to tell them
 * apart and render (or not) however fits its medium. `logic` still never interprets [tag] beyond
 * that: it's this enum, not real URLs/media, that `logic` knows about.
 */
enum class EasterEgg(
    val tag: String,
) {
    STORE("easter-egg-store"),
    TRAILER("easter-egg-trailer"),
    SECURITY_VIDEO("easter-egg-security-video"),
}
