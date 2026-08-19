package com.aperturescience.terminal.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.aperturescience.terminal.BLINK_TAG
import com.aperturescience.terminal.TerminalEngine
import com.jakewharton.mosaic.LocalTerminalState
import com.jakewharton.mosaic.layout.onKeyEvent
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.text.SpanStyle
import com.jakewharton.mosaic.text.buildAnnotatedString
import com.jakewharton.mosaic.ui.Text
import com.jakewharton.mosaic.ui.TextStyle
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

private const val BLINK_INTERVAL_MS = 500L

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
    val annotations by engine.annotations.collectAsState()
    val blinkRange = annotations.firstOrNull { it.tag == BLINK_TAG }?.range

    // Mosaic's TextStyle has no true blink attribute - fake it by toggling Invert on the marked
    // span on a timer instead.
    var blinkOn by remember { mutableStateOf(true) }
    LaunchedEffect(blinkRange != null) {
        if (blinkRange == null) return@LaunchedEffect
        while (true) {
            delay(BLINK_INTERVAL_MS.milliseconds)
            blinkOn = !blinkOn
        }
    }

    val modifier =
        Modifier.onKeyEvent { event ->
            // Returning false (unhandled) lets Mosaic's own Ctrl+C handling cancel the
            // composition and return normally - our own escape hatch, not an in-story command.
            if (event.ctrl && event.key == "c") {
                return@onKeyEvent false
            }
            engine.onKeyEvent(event.key)
        }

    if (blinkRange != null && blinkRange.last < liveLine.length) {
        val annotatedLine =
            buildAnnotatedString {
                append(liveLine)
                if (blinkOn) addStyle(SpanStyle(textStyle = TextStyle.Invert), blinkRange.first, blinkRange.last + 1)
            }
        Text(value = annotatedLine, modifier = modifier)
    } else {
        Text(value = liveLine, modifier = modifier)
    }
}
