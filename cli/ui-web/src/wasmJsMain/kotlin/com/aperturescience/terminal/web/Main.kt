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

    // Suppresses TerminalEngine's own hard-wrapping entirely (see setViewportWidth's doc): there
    // is no Mosaic-style redraw-desync risk here to guard against in the first place (this
    // renders by replacing textContent wholesale, no cursor/diffing involved), so the only job
    // left for reveal()'s wordWrap is picking *where* long lines break - a job CSS's own
    // `white-space: pre-wrap` (styles.css) already does correctly and, unlike a fixed character
    // count, responsively as the window resizes. Running both at once was tried first and looked
    // broken even when nominally "agreeing" (both around 100 characters): TerminalEngine's own
    // wordWrap uses a hardcoded character count with no knowledge of the actual rendered font
    // metrics or available width, so its break points don't move as the window resizes and can
    // land well short of - or occasionally past - where the real text would naturally wrap,
    // which reads as randomly-short, "already hard-wrapped" lines. Letting exactly one layer (the
    // browser's) own all wrapping decisions is what actually fixes that, not tuning the number.
    engine.setViewportWidth(UNWRAPPED_WIDTH)

    // Explicitly-typed (Event) -> Unit value, not an inline lambda literal, to sidestep overload
    // ambiguity between addEventListener's `EventListener` (SAM-convertible external interface)
    // and `(Event) -> Unit` overloads - a bare lambda literal argument is ambiguous between the
    // two, a value with an already-resolved static type isn't.
    val onKeyDown: (Event) -> Unit = { event ->
        if (acceptingInput) handleKeyDown(event as KeyboardEvent, engine)
    }
    window.addEventListener("keydown", onKeyDown)

    engine.boot(scope)
}

private const val TERMINAL_ELEMENT_ID = "terminal"

// Larger than any real line TerminalData contains, which is all TerminalEngine.wordWrap needs to
// never trigger - see the comment at the setViewportWidth() call site for why "unwrapped" (CSS
// does it instead) rather than some other specific number.
private const val UNWRAPPED_WIDTH = Int.MAX_VALUE

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
