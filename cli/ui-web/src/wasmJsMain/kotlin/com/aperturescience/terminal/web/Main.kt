package com.aperturescience.terminal.web

import com.aperturescience.terminal.TerminalEngine
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLPreElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent

/**
 * Web counterpart to `ui-terminal`'s `App.kt`/`AppRunner.kt`: Mosaic has no Kotlin/Wasm target,
 * so there's no shared UI layer between the CLI and the browser - this drives [TerminalEngine]
 * directly against the DOM instead. [TerminalEngine] itself needed zero changes to run here: it
 * was already UI-agnostic (`liveLine`/`exitRequested` are plain `StateFlow`s, `onKeyEvent` takes
 * a plain key name), the same contract `App.kt` binds to Mosaic's `Text()`/`onKeyEvent` with.
 */
fun main() {
    val engine = TerminalEngine()
    val scope = CoroutineScope(Job())

    val screen = document.getElementById(TERMINAL_ELEMENT_ID) as HTMLPreElement

    scope.launch {
        engine.liveLine.collectLatest { screen.textContent = it }
    }

    // Mirrors farewell()'s effect in the CLI (process exit): there's no process to exit here, so
    // this just stops the terminal from reacting to further keystrokes once LOGOUT/PLAY PORTAL
    // ends the session - the final message reveal() already wrote is left on screen.
    var acceptingInput = true
    scope.launch {
        engine.exitRequested.first { it }
        acceptingInput = false
    }

    // Explicitly-typed (Event) -> Unit values, not inline lambda literals, to sidestep overload
    // ambiguity between addEventListener's `EventListener` (SAM-convertible external interface)
    // and `(Event) -> Unit` overloads - a bare lambda literal argument is ambiguous between the
    // two, a value with an already-resolved static type isn't.
    val onResize: (Event) -> Unit = { engine.setViewportWidth(columnsForViewportWidth()) }
    window.addEventListener("resize", onResize)
    engine.setViewportWidth(columnsForViewportWidth())

    val onKeyDown: (Event) -> Unit = { event ->
        if (acceptingInput) handleKeyDown(event as KeyboardEvent, engine)
    }
    window.addEventListener("keydown", onKeyDown)

    engine.boot(scope)
}

private const val TERMINAL_ELEMENT_ID = "terminal"

// Matches the `ch`-unit max width the terminal element is styled with in CSS (see styles.css):
// this reverse-engineers the same column count from the viewport instead of hardcoding it twice.
// See TerminalEngine.setViewportWidth's doc for why this needs to happen at all - unlike the
// CLI's real terminal columns, a browser window has no character grid, so this is this
// frontend's own approximation of one.
private fun columnsForViewportWidth(): Int {
    val approxCharWidthPx = 9
    val horizontalPaddingPx = 48
    val usablePx = (window.innerWidth - horizontalPaddingPx).coerceAtLeast(approxCharWidthPx * 20)
    return usablePx / approxCharWidthPx
}

private val NAMED_KEYS = setOf("Enter", "Backspace", "PageUp", "PageDown")

/**
 * Forwards plain keystrokes to [TerminalEngine.onKeyEvent], mirroring `App.kt`'s `onKeyEvent`
 * modifier. Ctrl/Cmd/Alt combos are deliberately left alone (not forwarded, not prevented) so
 * browser/OS shortcuts - copy, devtools, tab switching, refresh - keep working; that's this
 * frontend's equivalent of `App.kt` returning `false` (unhandled) for Ctrl+C so Mosaic's own
 * root-level handling takes over instead of the key being swallowed as text input.
 */
private fun handleKeyDown(
    event: KeyboardEvent,
    engine: TerminalEngine,
) {
    if (event.ctrlKey || event.metaKey || event.altKey) return
    val key = event.key
    if (key in NAMED_KEYS || key.length == 1) {
        event.preventDefault()
        engine.onKeyEvent(key)
    }
}
