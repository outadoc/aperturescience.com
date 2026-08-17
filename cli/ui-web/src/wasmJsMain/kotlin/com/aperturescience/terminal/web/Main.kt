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

/** Web counterpart to `ui-terminal`'s `App.kt`/`AppRunner.kt`: no shared UI layer (Mosaic has no
 * Wasm target), so this drives [TerminalEngine] directly against the DOM instead. */
fun main() {
    val engine = TerminalEngine()
    val scope = CoroutineScope(Job())

    val screen = document.getElementById(TERMINAL_ELEMENT_ID) as HTMLPreElement

    scope.launch {
        engine.liveLine.collectLatest { screen.textContent = it }
    }

    // No process to exit here - just stop reacting to keystrokes once the session ends.
    var acceptingInput = true
    scope.launch {
        engine.exitRequested.first { it }
        acceptingInput = false
    }

    // No hard-wrapping needed - CSS's `white-space: pre-wrap` handles it, and unlike a fixed
    // character count, responds to window resizes.
    engine.setViewportWidth(UNWRAPPED_WIDTH)

    // Explicitly-typed value, not an inline lambda, to sidestep overload ambiguity between
    // addEventListener's EventListener and (Event) -> Unit overloads.
    val onKeyDown: (Event) -> Unit = { event ->
        if (acceptingInput) handleKeyDown(event as KeyboardEvent, engine)
    }
    window.addEventListener("keydown", onKeyDown)

    engine.boot(scope)
}

private const val TERMINAL_ELEMENT_ID = "terminal"

// Larger than any real line - just enough to keep TerminalEngine.wordWrap from ever triggering.
private const val UNWRAPPED_WIDTH = Int.MAX_VALUE

private val NAMED_KEYS = setOf("Enter", "Backspace", "PageUp", "PageDown")

/** Forwards plain keystrokes to [TerminalEngine.onKeyEvent]. Ctrl/Cmd/Alt combos are left alone
 * so browser/OS shortcuts (copy, devtools, refresh) keep working. */
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
