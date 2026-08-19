package com.aperturescience.terminal

/**
 * Max characters accepted into `EngineState.input` before further typing is ignored.
 */
internal const val MAX_INPUT_LENGTH = 65

/**
 * Keys the reducer treats specially, rather than as a single printable character (see
 * [isAcceptedChar]). Maps each platform's raw key-name string to one shared set.
 */
internal enum class NamedKey(
    val keyName: String,
) {
    ENTER("Enter"),
    BACKSPACE("Backspace"),
    PAGE_UP("PageUp"),
    PAGE_DOWN("PageDown"),
    ARROW_LEFT("ArrowLeft"),
    ;

    companion object {
        private val byName = entries.associateBy { it.keyName }

        fun from(key: String): NamedKey? = byName[key]
    }
}

/**
 * Public re-export of [NamedKey]'s recognized key-name strings, for hosts that need to
 * pre-filter which keys are worth dispatching at all (e.g. `ui-web`).
 */
val NAMED_KEYS: Set<String> = NamedKey.entries.map { it.keyName }.toSet()

internal fun isAcceptedChar(c: Char): Boolean = c.isDigit() || c.uppercaseChar() in 'A'..'Z' || c == ' ' || c == '?'

internal fun isAcceptedRawKey(
    namedKey: NamedKey?,
    key: String,
): Boolean = namedKey != null || key == " " || (key.length == 1 && isAcceptedChar(key[0]))
