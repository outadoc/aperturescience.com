package com.aperturescience.terminal.telnet

/**
 * RFC 854 command bytes relevant here (out of the full set, most of which this server never
 * needs to send or specially handle).
 */
internal object TelnetCommand {
    const val SE = 240
    const val SB = 250
    const val WILL = 251
    const val WONT = 252
    const val DO = 253
    const val DONT = 254
    const val IAC = 255
}

/**
 * RFC 857/858/1073 option codes this server negotiates.
 */
internal object TelnetOption {
    const val ECHO = 1
    const val SUPPRESS_GO_AHEAD = 3
    const val NAWS = 31
}

/**
 * Sent right after accept: switches the client into character-at-a-time mode with server-side
 * echo (mirroring how `ui-terminal` owns rendering/echo itself via raw-mode TTY), plus a NAWS
 * request so we learn - and keep learning, on resize - the client's terminal width.
 */
internal val TELNET_INITIAL_NEGOTIATION: ByteArray =
    intArrayOf(
        TelnetCommand.IAC,
        TelnetCommand.WILL,
        TelnetOption.ECHO,
        TelnetCommand.IAC,
        TelnetCommand.WILL,
        TelnetOption.SUPPRESS_GO_AHEAD,
        TelnetCommand.IAC,
        TelnetCommand.DO,
        TelnetOption.SUPPRESS_GO_AHEAD,
        TelnetCommand.IAC,
        TelnetCommand.DO,
        TelnetOption.NAWS,
    ).let { ints -> ByteArray(ints.size) { i -> ints[i].toByte() } }
