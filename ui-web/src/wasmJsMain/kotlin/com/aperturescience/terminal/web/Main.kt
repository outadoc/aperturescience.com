package com.aperturescience.terminal.web

import com.aperturescience.terminal.BLINK_TAG
import com.aperturescience.terminal.EasterEgg
import com.aperturescience.terminal.Intent
import com.aperturescience.terminal.NAMED_KEYS
import com.aperturescience.terminal.TerminalEngine
import com.aperturescience.terminal.displayText
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLPreElement
import org.w3c.dom.HTMLVideoElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent

/**
 * Web counterpart to `ui-terminal`'s `App.kt`/`AppRunner.kt`: no shared UI layer (Mosaic has no
 * Wasm target), so this drives [TerminalEngine] directly against the DOM instead.
 */
fun main() {
    val engine = TerminalEngine()
    val scope = CoroutineScope(Job())

    val screen = document.getElementById(TERMINAL_ELEMENT_ID) as HTMLPreElement
    val trailer = document.getElementById(TRAILER_ELEMENT_ID) as HTMLVideoElement

    scope.launch {
        engine.state.collectLatest { state ->
            val blinkRange = state.annotations.firstOrNull { it.tag == BLINK_TAG }?.range
            renderScreen(
                screen = screen,
                line = state.displayText,
                blinkRange = blinkRange,
            )

            val isTrailerFired = state.annotations.any { it.tag == EasterEgg.TRAILER.tag }
            showTrailer(
                trailer = trailer,
                show = isTrailerFired,
            )

            // TODO: hook these up to a real store link and video embed - logic only tells us
            // *which* easter egg fired, it stays oblivious to real-world URLs/media (see
            // TextAnnotation.kt). For now these are unused - no-op hook points.
            state.annotations.firstOrNull { it.tag == EasterEgg.STORE.tag }
            state.annotations.firstOrNull { it.tag == EasterEgg.SECURITY_VIDEO.tag }
        }
    }

    // No process to exit here - just stop reacting to keystrokes once the session ends.
    var acceptingInput = true
    scope.launch {
        engine.state.first { it.exitRequested }
        acceptingInput = false
    }

    // No hard-wrapping needed - CSS's `white-space: pre-wrap` handles it, and unlike a fixed
    // character count, responds to window resizes.
    scope.launch {
        engine.dispatch(
            Intent.ViewportResized(
                columns = UNWRAPPED_WIDTH,
            ),
        )
    }

    // Explicitly-typed value, not an inline lambda, to sidestep overload ambiguity between
    // addEventListener's EventListener and (Event) -> Unit overloads.
    val onKeyDown: (Event) -> Unit = { event ->
        if (acceptingInput) {
            handleKeyDown(
                event = event as KeyboardEvent,
                engine = engine,
                scope = scope,
            )
        }
    }

    window.addEventListener("keydown", onKeyDown)

    scope.launch {
        engine.dispatch(Intent.Boot)
    }
}

/**
 * Plain [line] when there's nothing to blink; otherwise rebuilds [screen]'s children around a
 * `<span class="blink-text">` wrapping [blinkRange]. Phase-locked to the cursor's own blink
 * entirely via CSS (both read the same inherited `--blink-opacity`, see styles.css) - a span
 * created mid-session is in sync from its very first frame, no JS timing math needed.
 */
private fun renderScreen(
    screen: HTMLPreElement,
    line: String,
    blinkRange: IntRange?,
) {
    if (blinkRange == null || blinkRange.last >= line.length) {
        screen.textContent = line
        return
    }
    while (screen.firstChild != null) {
        screen.removeChild(screen.firstChild!!)
    }

    screen.appendChild(
        document.createTextNode(
            line.substring(0, blinkRange.first),
        ),
    )

    val span = document.createElement("span")
    span.className = "blink-text"
    span.textContent =
        line.substring(
            startIndex = blinkRange.first,
            endIndex = blinkRange.last + 1,
        )
    screen.appendChild(span)
    screen.appendChild(
        document.createTextNode(
            line.substring(
                startIndex = blinkRange.last + 1,
            ),
        ),
    )
}

/**
 * Swaps [trailer] in over the terminal once the PLAY PORTAL easter egg fires - real playback,
 * in place of `logic`'s in-universe "[ERROR: TRAILER NOT FOUND]" fallback text. Reset (paused,
 * rewound) as soon as [show] goes false, so replaying the easter egg starts from the beginning.
 */
@OptIn(ExperimentalWasmJsInterop::class)
private fun showTrailer(
    trailer: HTMLVideoElement,
    show: Boolean,
) {
    if (show == trailer.classList.contains(TRAILER_VISIBLE_CLASS)) {
        return
    }

    trailer.classList.toggle(
        token = TRAILER_VISIBLE_CLASS,
        force = show,
    )

    if (show) {
        // Autoplay this many reveal-effect ticks after the triggering keypress may not count as
        // "user activation" to the browser - if it's blocked, `controls` lets the user hit play.
        trailer.play()
    } else {
        trailer.pause()
        trailer.currentTime = 0.0
    }
}

private const val TERMINAL_ELEMENT_ID = "terminal"
private const val TRAILER_ELEMENT_ID = "trailer"
private const val TRAILER_VISIBLE_CLASS = "visible"

/**
 * Larger than any real line - just enough to keep word-wrapping from ever triggering.
 */
private const val UNWRAPPED_WIDTH = Int.MAX_VALUE

/**
 * Forwards plain keystrokes to [TerminalEngine.dispatch] as [Intent.KeyPressed]. Ctrl/Cmd/Alt
 * combos are left alone so browser/OS shortcuts (copy, devtools, refresh) keep working.
 */
private fun handleKeyDown(
    event: KeyboardEvent,
    engine: TerminalEngine,
    scope: CoroutineScope,
) {
    if (event.ctrlKey || event.metaKey || event.altKey) {
        return
    }

    val key = event.key
    if (key in NAMED_KEYS || key.length == 1) {
        event.preventDefault()
        scope.launch { engine.dispatch(Intent.KeyPressed(key)) }
    }
}
