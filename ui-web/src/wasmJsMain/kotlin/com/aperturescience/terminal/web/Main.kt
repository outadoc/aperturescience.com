package com.aperturescience.terminal.web

import com.aperturescience.terminal.BLINK_TAG
import com.aperturescience.terminal.TerminalEngine
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
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
        combine(engine.liveLine, engine.annotations, ::Pair).collectLatest { (line, annotations) ->
            val blinkRange = annotations.firstOrNull { it.tag == BLINK_TAG }?.range
            renderScreen(screen, line, blinkRange)
        }
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

/** Plain [line] when there's nothing to blink; otherwise rebuilds [screen]'s children around a
 * `<span class="blink-text">` wrapping [blinkRange]. Phase-locked to the cursor's own blink
 * entirely via CSS (both read the same inherited `--blink-opacity`, see styles.css) - a span
 * created mid-session is in sync from its very first frame, no JS timing math needed. */
private fun renderScreen(
    screen: HTMLPreElement,
    line: String,
    blinkRange: IntRange?,
) {
    if (blinkRange == null || blinkRange.last >= line.length) {
        screen.textContent = line
        return
    }
    while (screen.firstChild != null) screen.removeChild(screen.firstChild!!)
    screen.appendChild(document.createTextNode(line.substring(0, blinkRange.first)))
    val span = document.createElement("span")
    span.className = "blink-text"
    span.textContent = line.substring(blinkRange.first, blinkRange.last + 1)
    screen.appendChild(span)
    screen.appendChild(document.createTextNode(line.substring(blinkRange.last + 1)))
}

private const val TERMINAL_ELEMENT_ID = "terminal"

// Larger than any real line - just enough to keep TerminalEngine.wordWrap from ever triggering.
private const val UNWRAPPED_WIDTH = Int.MAX_VALUE

private val NAMED_KEYS = setOf("Enter", "Backspace", "PageUp", "PageDown", "ArrowLeft")

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
