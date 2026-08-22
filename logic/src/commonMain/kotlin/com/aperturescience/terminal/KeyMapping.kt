package com.aperturescience.terminal

/**
 * Max characters accepted into `EngineState.input` before further typing is ignored.
 */
internal const val MAX_INPUT_LENGTH = 65

/**
 * A single raw key, as reported by a host platform.
 */
sealed interface Key {
    /**
     * A printable character typed as-is.
     */
    data class RawChar(
        val char: Char,
    ) : Key

    /**
     * Any key the platform positively identifies but the reducer never treats specially -
     * always rejected by [isAcceptedKey].
     */
    data object Other : Key

    /**
     * Keys the reducer treats specially, rather than as a single printable character (see
     * [isAcceptedChar]). Each platform adapter maps its own raw key events to this shared set.
     */
    enum class Named : Key {
        ENTER,
        BACKSPACE,
        PAGE_UP,
        PAGE_DOWN,
        ARROW_LEFT,
    }
}

internal fun isAcceptedChar(c: Char): Boolean =
    c.isDigit() || c.uppercaseChar() in 'A'..'Z' || c == ' ' || c == '?' || c == '.'

internal fun isAcceptedKey(key: Key): Boolean =
    when (key) {
        is Key.Named -> true
        is Key.RawChar -> isAcceptedChar(key.char)
        Key.Other -> false
    }
