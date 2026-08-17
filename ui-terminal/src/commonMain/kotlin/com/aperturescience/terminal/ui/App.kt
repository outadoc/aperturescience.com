package com.aperturescience.terminal.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import com.aperturescience.terminal.TerminalEngine
import com.jakewharton.mosaic.LocalTerminalState
import com.jakewharton.mosaic.layout.onKeyEvent
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.Text

@Composable
fun App(engine: TerminalEngine) {
    val scope = rememberCoroutineScope()

    // Mosaic's Text never soft-wraps on its own, so feed it the real column count (recomposes on
    // resize), capped at WRAP_WIDTH to match the original's own pixel-wrap threshold.
    val columns = LocalTerminalState.current.size.columns
    SideEffect { engine.setViewportWidth(minOf(columns, TerminalEngine.WRAP_WIDTH)) }

    LaunchedEffect(Unit) {
        engine.boot(scope)
    }

    val liveLine by engine.liveLine.collectAsState()

    Text(
        value = liveLine,
        modifier =
            Modifier.onKeyEvent { event ->
                // Returning false (unhandled) lets Mosaic's own Ctrl+C handling cancel the
                // composition and return normally - our own escape hatch, not an in-story command.
                if (event.ctrl && event.key == "c") {
                    return@onKeyEvent false
                }
                engine.onKeyEvent(event.key)
            },
    )
}
