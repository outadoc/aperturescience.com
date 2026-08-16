package com.aperturescience.terminal.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import com.aperturescience.terminal.TerminalEngine
import com.jakewharton.mosaic.layout.onKeyEvent
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.Text

@Composable
fun App(engine: TerminalEngine) {
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        engine.boot(scope)
    }

    val liveLine by engine.liveLine.collectAsState()

    Text(
        value = liveLine,
        modifier =
            Modifier.onKeyEvent { event ->
                // Ctrl+C is our own UI-level escape hatch, not part of the original terminal's
                // modeled behavior (unlike LOGOUT/PLAY PORTAL, which are faithfully-ported in-game
                // commands that also end the session, handled via TerminalEngine.exitRequested and
                // watched for in Main.kt). Returning false here means we did NOT handle it, so it
                // falls through to Mosaic's own root-level Ctrl+C handling, which cancels the
                // composition's job and lets runMosaic() return normally - no exitProcess()/
                // System.exit() call anywhere, which would be fatal if this were ever run inside a
                // test suite or a server. Always works, regardless of engine state, even from the
                // cake/bosskey loop which has no in-story escape in the original.
                if (event.ctrl && event.key == "c") {
                    return@onKeyEvent false
                }
                engine.onKeyEvent(event.key)
            },
    )
}
