package com.aperturescience.terminal.telnet

import kotlinx.coroutines.runBlocking

private const val PORT = 2323

fun main() =
    runBlocking {
        TelnetServer(PORT).run()
    }
