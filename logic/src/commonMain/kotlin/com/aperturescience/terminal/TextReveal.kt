package com.aperturescience.terminal

/**
 * Never appear in real content (control chars) - mark the start/end of a revealed span that
 * should carry a [TextAnnotation], stripped from [EngineState.pageContent] itself.
 */
internal const val BLINK_START = '' // STX

/**
 * Shared close marker for every tag in [START_CHAR_TO_TAG], not just [BLINK_TAG] - only one
 * annotation is ever open at a time (see [EngineState.pendingAnnotationTag]), so the reducer
 * always knows which tag it's closing without needing a distinct end char per tag.
 */
internal const val BLINK_END = '' // ETX

/**
 * Reserved start-marker char for each [EasterEgg] - internal (not private) so tests can
 * drive [TerminalReducer.reduceCharacterRevealed] directly with a known easter-egg char.
 */
internal val EASTER_EGG_START_CHAR: Map<EasterEgg, Char> =
    mapOf(
        EasterEgg.STORE to '', // EOT
        EasterEgg.TRAILER to '', // ENQ
        EasterEgg.SECURITY_VIDEO to '', // ACK
    )

/**
 * Every reserved start-marker char, mapped to the [TextAnnotation.tag] string it opens -
 * covers [buildRevealChars]' own `@`/[BLINK_TAG] substitution plus every [EasterEgg].
 */
internal val START_CHAR_TO_TAG: Map<Char, String> =
    buildMap {
        put(BLINK_START, BLINK_TAG)
        EASTER_EGG_START_CHAR.forEach { (easterEgg, char) -> put(char, easterEgg.tag) }
    }

/**
 * Every char [visibleLength]/[splitAtVisibleWidth] must treat as zero-width - none of these
 * are ever appended to [EngineState.pageContent].
 */
internal val MARKER_CHARS: Set<Char> = START_CHAR_TO_TAG.keys + BLINK_END

/**
 * Wraps [text] in [easterEgg]'s start marker and the shared end marker, so the reveal stream
 * picks it up as a `TextAnnotation` once revealed - see `TerminalReducer.reduceCharacterRevealed`.
 */
internal fun taggedSpan(
    easterEgg: EasterEgg,
    text: String,
): String = "${EASTER_EGG_START_CHAR.getValue(easterEgg)}$text$BLINK_END"

/**
 * Turns [text] into the flat character stream a reveal effect replays one at a time: substitutes
 * [uid] for `@`, `^` becomes a newline, then word-wraps to [wrapWidth].
 */
internal fun buildRevealChars(
    text: String,
    uid: String,
    wrapWidth: Int,
): List<Char> {
    val uidToken = "$BLINK_START[$uid]$BLINK_END"
    val logicalLines = text.replace("@", uidToken).replace("^", "\n").split("\n")
    val lines = logicalLines.flatMap { wordWrap(it, wrapWidth) }
    return buildList {
        for ((index, line) in lines.withIndex()) {
            addAll(line.toList())
            if (index != lines.lastIndex) add('\n')
        }
    }
}

internal fun wordWrap(
    line: String,
    width: Int,
): List<String> {
    if (visibleLength(line) <= width) return listOf(line)
    val result = mutableListOf<String>()
    val current = StringBuilder()
    for (word in line.split(" ")) {
        var w = word
        while (visibleLength(w) > width) {
            if (current.isNotEmpty()) {
                result.add(current.toString())
                current.clear()
            }
            val (head, tail) = splitAtVisibleWidth(w, width)
            result.add(head)
            w = tail
        }
        val candidateLen = visibleLength(current) + (if (current.isEmpty()) 0 else 1) + visibleLength(w)
        if (candidateLen > width && current.isNotEmpty()) {
            result.add(current.toString())
            current.clear()
        }
        if (current.isNotEmpty()) current.append(' ')
        current.append(w)
    }
    if (current.isNotEmpty() || result.isEmpty()) result.add(current.toString())
    return result
}

/**
 * [MARKER_CHARS] carry no visible width - wrap decisions go by what actually occupies a column,
 * not raw [CharSequence.length].
 */
internal fun visibleLength(s: CharSequence): Int {
    var count = 0
    for (c in s) if (c !in MARKER_CHARS) count++
    return count
}

/**
 * [wordWrap]'s hard-cut path: splits [s] at exactly [width] visible characters, keeping any
 * [MARKER_CHARS] attached to the head rather than left dangling alone.
 */
internal fun splitAtVisibleWidth(
    s: String,
    width: Int,
): Pair<String, String> {
    var visible = 0
    var i = 0
    while (i < s.length && visible < width) {
        if (s[i] !in MARKER_CHARS) visible++
        i++
    }
    while (i < s.length && s[i] in MARKER_CHARS) i++
    return s.substring(0, i) to s.substring(i)
}
