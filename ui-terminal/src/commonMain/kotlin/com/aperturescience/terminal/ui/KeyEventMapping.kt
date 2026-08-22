package com.aperturescience.terminal.ui

import com.jakewharton.mosaic.terminal.KeyboardEvent

/**
 * Maps a raw [KeyboardEvent] to the key-name string `Intent.KeyPressed` expects, porting the
 * codepoint table Mosaic's own (Compose-only) `compat.kt` uses internally. Returns null for
 * key-release/repeat events and unrecognized codepoints instead of throwing.
 */
internal fun KeyboardEvent.toKeyNameOrNull(): String? {
    if (eventType != KeyboardEvent.EventTypePress) return null
    return when (val cp = codepoint) {
        9 -> "Tab"
        13 -> "Enter"
        27 -> "Escape"
        in 32..126 -> cp.toChar().toString()
        127 -> "Backspace"
        KeyboardEvent.Left -> "ArrowLeft"
        KeyboardEvent.Right -> "ArrowRight"
        KeyboardEvent.Up -> "ArrowUp"
        KeyboardEvent.Down -> "ArrowDown"
        KeyboardEvent.Insert -> "Insert"
        KeyboardEvent.Delete -> "Delete"
        KeyboardEvent.PageUp -> "PageUp"
        KeyboardEvent.PageDown -> "PageDown"
        KeyboardEvent.Home -> "Home"
        KeyboardEvent.End -> "End"
        in 57364..57398 -> "F" + (cp - 57363)
        else -> null
    }
}
