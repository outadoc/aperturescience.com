package com.aperturescience.terminal.telnet

import com.aperturescience.terminal.Key
import kotlin.test.Test
import kotlin.test.assertEquals

private fun TelnetInputParser.acceptAll(vararg bytes: Int): List<TelnetInputEvent> = bytes.flatMap { accept(it) }

class TelnetInputParserTest {
    @Test
    fun `plain printable bytes map to raw chars`() {
        val events = TelnetInputParser().acceptAll('H'.code, 'I'.code)
        assertEquals(
            listOf(
                TelnetInputEvent.KeyPressed(Key.RawChar('H')),
                TelnetInputEvent.KeyPressed(Key.RawChar('I')),
            ),
            events,
        )
    }

    @Test
    fun `CR alone maps to Enter`() {
        val events = TelnetInputParser().acceptAll(0x0D)
        assertEquals(listOf(TelnetInputEvent.KeyPressed(Key.Named.ENTER)), events)
    }

    @Test
    fun `CR LF maps to a single Enter, swallowing the LF`() {
        val events = TelnetInputParser().acceptAll(0x0D, 0x0A)
        assertEquals(listOf(TelnetInputEvent.KeyPressed(Key.Named.ENTER)), events)
    }

    @Test
    fun `CR NUL maps to a single Enter, swallowing the NUL`() {
        val events = TelnetInputParser().acceptAll(0x0D, 0x00)
        assertEquals(listOf(TelnetInputEvent.KeyPressed(Key.Named.ENTER)), events)
    }

    @Test
    fun `bare LF also maps to Enter`() {
        val events = TelnetInputParser().acceptAll(0x0A)
        assertEquals(listOf(TelnetInputEvent.KeyPressed(Key.Named.ENTER)), events)
    }

    @Test
    fun `DEL and BS both map to Backspace`() {
        assertEquals(
            listOf(TelnetInputEvent.KeyPressed(Key.Named.BACKSPACE)),
            TelnetInputParser().acceptAll(0x7F),
        )
        assertEquals(
            listOf(TelnetInputEvent.KeyPressed(Key.Named.BACKSPACE)),
            TelnetInputParser().acceptAll(0x08),
        )
    }

    @Test
    fun `Ctrl+C maps to Disconnect`() {
        val events = TelnetInputParser().acceptAll(0x03)
        assertEquals(listOf(TelnetInputEvent.Disconnect), events)
    }

    @Test
    fun `CSI D maps to arrow left`() {
        val events = TelnetInputParser().acceptAll(0x1B, '['.code, 'D'.code)
        assertEquals(listOf(TelnetInputEvent.KeyPressed(Key.Named.ARROW_LEFT)), events)
    }

    @Test
    fun `CSI 5 tilde maps to page up`() {
        val events = TelnetInputParser().acceptAll(0x1B, '['.code, '5'.code, '~'.code)
        assertEquals(listOf(TelnetInputEvent.KeyPressed(Key.Named.PAGE_UP)), events)
    }

    @Test
    fun `CSI 6 tilde maps to page down`() {
        val events = TelnetInputParser().acceptAll(0x1B, '['.code, '6'.code, '~'.code)
        assertEquals(listOf(TelnetInputEvent.KeyPressed(Key.Named.PAGE_DOWN)), events)
    }

    @Test
    fun `CSI A (up) maps to Other, not swallowed`() {
        val events = TelnetInputParser().acceptAll(0x1B, '['.code, 'A'.code)
        assertEquals(listOf(TelnetInputEvent.KeyPressed(Key.Other)), events)
    }

    @Test
    fun `IAC WILL ECHO negotiation produces no events`() {
        val events = TelnetInputParser().acceptAll(255, 251, 1)
        assertEquals(emptyList<TelnetInputEvent>(), events)
    }

    @Test
    fun `IAC SB NAWS width height IAC SE reports the width as a resize`() {
        // width = 132 (0x00, 0x84), height = 40 (0x00, 0x28) - height isn't tracked/used.
        val events =
            TelnetInputParser().acceptAll(
                255,
                250,
                31,
                0,
                0x84,
                0,
                0x28,
                255,
                240,
            )
        assertEquals(listOf(TelnetInputEvent.Resize(132)), events)
    }

    @Test
    fun `escaped literal 0xFF inside a NAWS subnegotiation is unescaped, not mistaken for SE`() {
        // width = 255 (0xFF, escaped as IAC IAC), height = 24 (0x00, 0x18).
        val events =
            TelnetInputParser().acceptAll(
                255,
                250,
                31,
                0,
                255,
                255,
                0,
                0x18,
                255,
                240,
            )
        assertEquals(listOf(TelnetInputEvent.Resize(255)), events)
    }

    @Test
    fun `keystrokes typed across separate accept calls still decode correctly`() {
        val parser = TelnetInputParser()
        val events = mutableListOf<TelnetInputEvent>()
        events += parser.accept(0x1B)
        events += parser.accept('['.code)
        events += parser.accept('D'.code)
        assertEquals(listOf(TelnetInputEvent.KeyPressed(Key.Named.ARROW_LEFT)), events.toList())
    }
}
