package com.aperturescience.terminal

/**
 * Never appear in real content (control chars) - mark the start/end of a revealed span that
 * should carry a [TextAnnotation], stripped from [EngineState.pageContent] itself.
 */
internal const val BLINK_START = '' // STX
internal const val BLINK_END = '' // ETX

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
 * [BLINK_START]/[BLINK_END] carry no visible width - wrap decisions go by what actually
 * occupies a column, not raw [CharSequence.length].
 */
internal fun visibleLength(s: CharSequence): Int {
    var count = 0
    for (c in s) if (c != BLINK_START && c != BLINK_END) count++
    return count
}

/**
 * [wordWrap]'s hard-cut path: splits [s] at exactly [width] visible characters, keeping any
 * [BLINK_START]/[BLINK_END] markers attached to the head rather than left dangling alone.
 */
internal fun splitAtVisibleWidth(
    s: String,
    width: Int,
): Pair<String, String> {
    var visible = 0
    var i = 0
    while (i < s.length && visible < width) {
        if (s[i] != BLINK_START && s[i] != BLINK_END) visible++
        i++
    }
    while (i < s.length && (s[i] == BLINK_START || s[i] == BLINK_END)) i++
    return s.substring(0, i) to s.substring(i)
}
