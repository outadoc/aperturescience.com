package com.aperturescience.terminal.ui

import com.aperturescience.terminal.Key
import com.jakewharton.mosaic.terminal.KeyboardEvent

/**
 * Maps a raw [KeyboardEvent] to the [Key] `Intent.KeyPressed` expects, porting the codepoint
 * table Mosaic's own (Compose-only) `compat.kt` uses internally. Recognized keys with no special
 * meaning to the reducer (Tab, Escape, arrows other than left, Insert/Delete/Home/End, F-keys)
 * map to [Key.Other]. Returns null for key-release/repeat events and unrecognized codepoints
 * instead of throwing.
 */
internal fun KeyboardEvent.toKeyOrNull(): Key? {
    if (eventType != KeyboardEvent.EventTypePress) return null
    return when (val cp = codepoint) {
        9 -> Key.Other
        13 -> Key.Named.ENTER
        27 -> Key.Other
        in 32..126 -> Key.RawChar(cp.toChar())
        127 -> Key.Named.BACKSPACE
        KeyboardEvent.Left -> Key.Named.ARROW_LEFT
        KeyboardEvent.Right -> Key.Other
        KeyboardEvent.Up -> Key.Other
        KeyboardEvent.Down -> Key.Other
        KeyboardEvent.Insert -> Key.Other
        KeyboardEvent.Delete -> Key.Other
        KeyboardEvent.PageUp -> Key.Named.PAGE_UP
        KeyboardEvent.PageDown -> Key.Named.PAGE_DOWN
        KeyboardEvent.Home -> Key.Other
        KeyboardEvent.End -> Key.Other
        in 57364..57398 -> Key.Other
        else -> null
    }
}
