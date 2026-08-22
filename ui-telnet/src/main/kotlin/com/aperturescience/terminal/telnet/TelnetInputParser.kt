package com.aperturescience.terminal.telnet

import com.aperturescience.terminal.Key

private const val ESC = 0x1B
private const val CSI_OPEN = '['.code
private const val CR = 0x0D
private const val LF = 0x0A
private const val NUL = 0x00
private const val BACKSPACE = 0x08
private const val DEL = 0x7F
private const val ETX = 0x03 // Ctrl+C
private const val MAX_CSI_LENGTH = 8

sealed interface TelnetInputEvent {
    data class KeyPressed(
        val key: Key,
    ) : TelnetInputEvent

    data class Resize(
        val columns: Int,
    ) : TelnetInputEvent

    data object Disconnect : TelnetInputEvent
}

/**
 * Stateful byte-stream decoder for one telnet connection: strips/interprets IAC negotiation
 * (including NAWS window-size subnegotiation) and decodes the ANSI escape sequences terminal
 * clients send for arrow/PgUp/PgDn keys, since raw telnet gives undecoded bytes for both - unlike
 * Mosaic's already-decoded events on the `ui-terminal` side (see `KeyEventMapping.kt`).
 */
class TelnetInputParser {
    private enum class Mode { NORMAL, AFTER_CR, ESC, CSI, IAC, IAC_OPTION, SUBNEG, SUBNEG_IAC }

    private var mode = Mode.NORMAL
    private val csi = StringBuilder()
    private val subneg = mutableListOf<Int>()

    /** Feeds one raw byte (0..255) from the socket and returns any events it completed. */
    fun accept(byte: Int): List<TelnetInputEvent> {
        val events = mutableListOf<TelnetInputEvent>()
        when (mode) {
            Mode.NORMAL -> acceptNormal(byte, events)
            Mode.AFTER_CR -> {
                mode = Mode.NORMAL
                // NVT ASCII sends CR LF or CR NUL for a newline - only swallow those, replay anything else.
                if (byte != LF && byte != NUL) acceptNormal(byte, events)
            }
            Mode.ESC -> {
                if (byte == CSI_OPEN) {
                    mode = Mode.CSI
                    csi.clear()
                } else {
                    mode = Mode.NORMAL
                    acceptNormal(byte, events)
                }
            }
            Mode.CSI -> acceptCsi(byte, events)
            Mode.IAC -> acceptIac(byte, events)
            Mode.IAC_OPTION -> {
                // WILL/WONT/DO/DONT option byte - nothing to reply, we already declared our own
                // stance up front; the only option we actively act on is NAWS, via SB below.
                mode = Mode.NORMAL
            }
            Mode.SUBNEG -> acceptSubneg(byte)
            Mode.SUBNEG_IAC -> acceptSubnegIac(byte, events)
        }
        return events
    }

    private fun acceptNormal(
        byte: Int,
        events: MutableList<TelnetInputEvent>,
    ) {
        when {
            byte == TelnetCommand.IAC -> mode = Mode.IAC
            byte == ESC -> mode = Mode.ESC
            byte == CR -> {
                events += TelnetInputEvent.KeyPressed(Key.Named.ENTER)
                mode = Mode.AFTER_CR
            }
            byte == LF -> events += TelnetInputEvent.KeyPressed(Key.Named.ENTER)
            byte == BACKSPACE || byte == DEL -> events += TelnetInputEvent.KeyPressed(Key.Named.BACKSPACE)
            byte == ETX -> events += TelnetInputEvent.Disconnect
            byte in 32..126 -> events += TelnetInputEvent.KeyPressed(Key.RawChar(byte.toChar()))
            else -> {} // unrecognized control byte - dropped, same as ui-terminal drops unmapped codepoints
        }
    }

    private fun acceptCsi(
        byte: Int,
        events: MutableList<TelnetInputEvent>,
    ) {
        if (byte in 0x30..0x3F || byte in 0x20..0x2F) {
            if (csi.length < MAX_CSI_LENGTH) csi.append(byte.toChar())
            return
        }
        if (byte in 0x40..0x7E) {
            events += csiKey(byte.toChar(), csi.toString())
            mode = Mode.NORMAL
            return
        }
        // Malformed/oversized sequence - bail out rather than buffering forever.
        mode = Mode.NORMAL
    }

    private fun csiKey(
        finalByte: Char,
        params: String,
    ): TelnetInputEvent =
        when (finalByte) {
            'D' -> TelnetInputEvent.KeyPressed(Key.Named.ARROW_LEFT)
            '~' ->
                when (params) {
                    "5" -> TelnetInputEvent.KeyPressed(Key.Named.PAGE_UP)
                    "6" -> TelnetInputEvent.KeyPressed(Key.Named.PAGE_DOWN)
                    else -> TelnetInputEvent.KeyPressed(Key.Other)
                }
            else -> TelnetInputEvent.KeyPressed(Key.Other)
        }

    private fun acceptIac(
        byte: Int,
        events: MutableList<TelnetInputEvent>,
    ) {
        when (byte) {
            TelnetCommand.IAC -> {
                // Escaped literal 0xFF data byte.
                mode = Mode.NORMAL
                acceptNormal(byte, events)
            }
            TelnetCommand.WILL, TelnetCommand.WONT, TelnetCommand.DO, TelnetCommand.DONT -> {
                mode = Mode.IAC_OPTION
            }
            TelnetCommand.SB -> {
                subneg.clear()
                mode = Mode.SUBNEG
            }
            else -> mode = Mode.NORMAL // single-byte IAC commands (NOP, AYT, ...) - nothing to do
        }
    }

    private fun acceptSubneg(byte: Int) {
        if (byte == TelnetCommand.IAC) {
            mode = Mode.SUBNEG_IAC
        } else {
            subneg += byte
        }
    }

    private fun acceptSubnegIac(
        byte: Int,
        events: MutableList<TelnetInputEvent>,
    ) {
        when (byte) {
            TelnetCommand.IAC -> {
                subneg += byte // escaped literal 0xFF within the subnegotiation payload
                mode = Mode.SUBNEG
            }
            TelnetCommand.SE -> {
                if (subneg.getOrNull(0) == TelnetOption.NAWS && subneg.size >= 5) {
                    val columns = (subneg[1] shl 8) or subneg[2]
                    events += TelnetInputEvent.Resize(columns)
                }
                mode = Mode.NORMAL
            }
            else -> mode = Mode.NORMAL // malformed - bail out
        }
    }
}
