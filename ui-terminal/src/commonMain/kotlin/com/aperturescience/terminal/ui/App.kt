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
import com.aperturescience.terminal.EasterEgg
import com.aperturescience.terminal.Intent
import com.aperturescience.terminal.TerminalEngine
import com.aperturescience.terminal.WRAP_WIDTH
import com.aperturescience.terminal.displayText
import com.jakewharton.mosaic.LocalTerminalState
import com.jakewharton.mosaic.layout.onKeyEvent
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.text.SpanStyle
import com.jakewharton.mosaic.text.buildAnnotatedString
import com.jakewharton.mosaic.ui.Text
import com.jakewharton.mosaic.ui.TextStyle
import com.jakewharton.mosaic.ui.UnderlineStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private const val BLINK_INTERVAL_MS = 500L

@Composable
fun App(engine: TerminalEngine) {
    val scope = rememberCoroutineScope()

    // Mosaic's Text never soft-wraps on its own, so feed it the real column count (recomposes on
    // resize), capped at WRAP_WIDTH to match the original's own pixel-wrap threshold.
    val columns = LocalTerminalState.current.size.columns
    SideEffect { scope.launch { engine.dispatch(Intent.ViewportResized(minOf(columns, WRAP_WIDTH))) } }

    LaunchedEffect(Unit) {
        engine.dispatch(Intent.Boot)
    }

    val state by engine.state.collectAsState()
    val liveLine = state.displayText
    val blinkRange = state.annotations.firstOrNull { it.tag == BLINK_TAG }?.range
    val storeRange = state.annotations.firstOrNull { it.tag == EasterEgg.STORE.tag }?.range

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
            scope.launch { engine.dispatch(Intent.KeyPressed(event.key)) }
            true
        }

    val isBlinkVisible = blinkRange != null && blinkRange.last < liveLine.length
    val isStoreLinkVisible = storeRange != null && storeRange.last < liveLine.length
    if (isBlinkVisible || isStoreLinkVisible) {
        val annotatedLine =
            buildAnnotatedString {
                append(liveLine)
                if (blinkRange != null && blinkOn) {
                    addStyle(SpanStyle(textStyle = TextStyle.Invert), blinkRange.first, blinkRange.last + 1)
                }
                if (storeRange != null) {
                    addStyle(SpanStyle(underlineStyle = UnderlineStyle.Straight), storeRange.first, storeRange.last + 1)
                }
            }
        Text(value = annotatedLine, modifier = modifier)
    } else {
        Text(value = liveLine, modifier = modifier)
    }
}
