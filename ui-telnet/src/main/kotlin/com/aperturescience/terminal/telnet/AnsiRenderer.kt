package com.aperturescience.terminal.telnet

import com.aperturescience.terminal.BLINK_TAG
import com.aperturescience.terminal.EasterEgg
import com.aperturescience.terminal.EngineState
import com.aperturescience.terminal.displayText

private const val ESC = ""
private const val CSI = "$ESC["
private const val CURSOR_HIDE = "$CSI?25l"
private const val CURSOR_SHOW = "$CSI?25h"
private const val CURSOR_HOME = "${CSI}H"
private const val ERASE_TO_END = "${CSI}0J"
private const val BLINK_ON = "${CSI}5m"
private const val BLINK_OFF = "${CSI}25m"
private const val UNDERLINE_ON = "${CSI}4m"
private const val UNDERLINE_OFF = "${CSI}24m"

/**
 * Renders one full frame for [state] as a plain ANSI string: clears the previous frame, writes
 * [EngineState.displayText], then places the real cursor at the end of the text. Ported from
 * `ui-terminal`'s `AnsiRenderer.kt` - the algorithm has no Mosaic dependency, so each frontend
 * owns its own copy rather than sharing one, same as `ui-minitel`'s Vidéotex renderer.
 */
fun renderFrame(state: EngineState): String {
    val text = state.displayText
    val lines = text.split("\n")
    val blinkRange = state.annotations.firstOrNull { it.tag == BLINK_TAG }?.range
    val storeRange = state.annotations.firstOrNull { it.tag == EasterEgg.STORE.tag }?.range

    return buildString {
        append(CURSOR_HIDE)
        append(CURSOR_HOME)
        append(ERASE_TO_END)

        var offset = 0
        var blinkOn = false
        var underlineOn = false
        lines.forEachIndexed { lineIndex, line ->
            if (lineIndex > 0) append("\r\n")
            for (char in line) {
                val wantBlink = blinkRange != null && offset in blinkRange
                val wantUnderline = storeRange != null && offset in storeRange
                if (wantBlink != blinkOn) {
                    append(if (wantBlink) BLINK_ON else BLINK_OFF)
                    blinkOn = wantBlink
                }
                if (wantUnderline != underlineOn) {
                    append(if (wantUnderline) UNDERLINE_ON else UNDERLINE_OFF)
                    underlineOn = wantUnderline
                }
                append(char)
                offset++
            }
            offset++ // account for the '\n' consumed by split()
        }
        if (blinkOn) append(BLINK_OFF)
        if (underlineOn) append(UNDERLINE_OFF)

        // The cursor always sits at the very end of `text` (input is only ever appended).
        val cursorRow = lines.size
        val cursorCol = lines.last().length + 1
        append(CSI)
            .append(cursorRow)
            .append(';')
            .append(cursorCol)
            .append('H')
        append(CURSOR_SHOW)
    }
}
