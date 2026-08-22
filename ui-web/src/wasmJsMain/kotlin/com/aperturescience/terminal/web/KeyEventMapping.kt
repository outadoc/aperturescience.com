package com.aperturescience.terminal.web

import com.aperturescience.terminal.Key
import org.w3c.dom.events.KeyboardEvent

private val namedKeysByBrowserName: Map<String, Key.Named> =
    mapOf(
        "Enter" to Key.Named.ENTER,
        "Backspace" to Key.Named.BACKSPACE,
        "PageUp" to Key.Named.PAGE_UP,
        "PageDown" to Key.Named.PAGE_DOWN,
        "ArrowLeft" to Key.Named.ARROW_LEFT,
    )

/**
 * Maps a raw DOM [KeyboardEvent.key] to the [Key.Named] it corresponds to, or null if this key
 * isn't one the reducer treats specially.
 */
internal fun KeyboardEvent.toNamedKeyOrNull(): Key.Named? = namedKeysByBrowserName[key]
