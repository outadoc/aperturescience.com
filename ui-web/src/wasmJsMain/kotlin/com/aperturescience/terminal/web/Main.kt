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
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLPreElement
import org.w3c.dom.HTMLVideoElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.InputEvent
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
    val securityVideo = document.getElementById(SECURITY_VIDEO_ELEMENT_ID) as HTMLVideoElement
    val mobileInput = document.getElementById(MOBILE_INPUT_ELEMENT_ID) as HTMLInputElement

    scope.launch {
        engine.state.collectLatest { state ->
            val blinkRange = state.annotations.firstOrNull { it.tag == BLINK_TAG }?.range
            val storeRange = state.annotations.firstOrNull { it.tag == EasterEgg.STORE.tag }?.range
            val securityVideoRange = state.annotations.firstOrNull { it.tag == EasterEgg.SECURITY_VIDEO.tag }?.range
            renderScreen(
                screen = screen,
                line = state.displayText,
                blinkRange = blinkRange,
                storeRange = storeRange,
                securityVideo = securityVideo,
                securityVideoRange = securityVideoRange,
            )

            val isTrailerFired = state.annotations.any { it.tag == EasterEgg.TRAILER.tag }
            showTrailer(
                trailer = trailer,
                show = isTrailerFired,
            )
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

    var previousMobileInputValue = ""

    // Explicitly-typed value, not an inline lambda, to sidestep overload ambiguity between
    // addEventListener's EventListener and (Event) -> Unit overloads.
    val onKeyDown: (Event) -> Unit = { event ->
        if (acceptingInput) {
            val keyboardEvent = event as KeyboardEvent
            handleKeyDown(
                event = keyboardEvent,
                engine = engine,
                scope = scope,
            )
            // Mirrors the reducer's own input-buffer reset on submit, so the diffing in
            // handleInput doesn't try to "undo" text that Enter already committed.
            val isPlainEnter =
                keyboardEvent.key == "Enter" &&
                    !keyboardEvent.ctrlKey &&
                    !keyboardEvent.metaKey &&
                    !keyboardEvent.altKey
            if (isPlainEnter) {
                mobileInput.value = ""
                previousMobileInputValue = ""
            }
        }
    }
    window.addEventListener("keydown", onKeyDown)

    // Mobile keyboards don't reliably report characters via keydown, so those go through this
    // event instead - see handleInput for why it diffs mobileInput's value rather than trusting
    // InputEvent.data/inputType.
    val onInput: (Event) -> Unit = { event ->
        val inputEvent = event as InputEvent
        if (acceptingInput && !inputEvent.isComposing) {
            previousMobileInputValue =
                handleInput(
                    mobileInput = mobileInput,
                    previousValue = previousMobileInputValue,
                    engine = engine,
                    scope = scope,
                )
        }
    }
    mobileInput.addEventListener("input", onInput)

    // Focuses mobileInput so tapping the terminal summons the on-screen keyboard on mobile;
    // desktop already works without this since mobileInput is focused at boot below.
    val onTerminalTap: (Event) -> Unit = {
        if (acceptingInput) {
            mobileInput.focus()
        }
    }
    screen.addEventListener("click", onTerminalTap)
    screen.addEventListener("touchend", onTerminalTap)

    mobileInput.focus()

    scope.launch {
        engine.dispatch(Intent.Boot)
    }
}

/**
 * Plain [line] when there's nothing special to splice in; otherwise rebuilds [screen]'s children
 * around whichever of [blinkRange]/[storeRange]/[securityVideoRange] applies (never more than one
 * at once - they cover unrelated screens). [securityVideoRange] takes priority since it
 * *replaces* that span of text outright, not just decorates it: [securityVideo] is moved in as a
 * real child of [screen] at that exact position, in place of `logic`'s in-universe "[ERROR:
 * SECURITY02.FLV NOT FOUND]" fallback text, so it reads as an actual embedded clip in the
 * transcript rather than an overlay (contrast `showTrailer`, which layers #trailer on top of the
 * whole page via CSS instead). [storeRange] is next - `logic` reveals the real Steam URL as
 * plain text for this one (see `TerminalReducer.STORE_URL`), so it's wrapped in a real `<a>`
 * rather than replaced. Blink is the lighter-weight case - a `<span class="blink-text">`
 * wrapping [blinkRange]. Phase-locked to the cursor's own blink entirely via CSS (both read the
 * same inherited `--blink-opacity`, see styles.css) - a span created mid-session is in sync from
 * its very first frame, no JS timing math needed.
 */
private fun renderScreen(
    screen: HTMLPreElement,
    line: String,
    blinkRange: IntRange?,
    storeRange: IntRange?,
    securityVideo: HTMLVideoElement,
    securityVideoRange: IntRange?,
) {
    val embedRange = securityVideoRange?.takeIf { it.last < line.length }
    if (embedRange != null) {
        showSecurityVideo(
            securityVideo = securityVideo,
            show = true,
        )
        while (screen.firstChild != null) {
            screen.removeChild(screen.firstChild!!)
        }
        screen.appendChild(document.createTextNode(line.substring(0, embedRange.first)))
        screen.appendChild(securityVideo)
        screen.appendChild(document.createTextNode(line.substring(startIndex = embedRange.last + 1)))
        return
    }
    showSecurityVideo(
        securityVideo = securityVideo,
        show = false,
    )

    val linkRange = storeRange?.takeIf { it.last < line.length }
    if (linkRange != null) {
        while (screen.firstChild != null) {
            screen.removeChild(screen.firstChild!!)
        }
        screen.appendChild(document.createTextNode(line.substring(0, linkRange.first)))
        val url = line.substring(startIndex = linkRange.first, endIndex = linkRange.last + 1)
        val anchor = document.createElement("a") as HTMLAnchorElement
        anchor.href = url
        anchor.textContent = url
        anchor.target = "_blank"
        anchor.rel = "noopener noreferrer"
        screen.appendChild(anchor)
        screen.appendChild(document.createTextNode(line.substring(startIndex = linkRange.last + 1)))
        return
    }

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
 * Toggles [trailer] in over the whole page once PLAY PORTAL fires - real playback, in place of
 * `logic`'s in-universe "[ERROR: TRAILER NOT FOUND]" fallback text, as a fullscreen CSS overlay
 * (`.visible`, see styles.css) rather than spliced into the transcript like `showSecurityVideo`.
 * Reset (paused, rewound) as soon as [show] goes false, so replaying the easter egg starts from
 * the beginning.
 */
@OptIn(ExperimentalWasmJsInterop::class)
private fun showTrailer(
    trailer: HTMLVideoElement,
    show: Boolean,
) {
    if (show == trailer.classList.contains(VIDEO_VISIBLE_CLASS)) {
        return
    }

    trailer.classList.toggle(
        token = VIDEO_VISIBLE_CLASS,
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

/**
 * Companion to `renderScreen`'s embedding of [securityVideo]: flips the `.visible` class (styles
 * .css relies on it to switch from `display: none` to a normal-flow 320px block) and starts/stops
 * playback. Kept separate from [showTrailer] because unlike the trailer this element's *position*
 * in the DOM is also managed by `renderScreen` - this function only ever touches its class and
 * playback state, never moves it.
 */
@OptIn(ExperimentalWasmJsInterop::class)
private fun showSecurityVideo(
    securityVideo: HTMLVideoElement,
    show: Boolean,
) {
    if (show == securityVideo.classList.contains(VIDEO_VISIBLE_CLASS)) {
        return
    }

    securityVideo.classList.toggle(
        token = VIDEO_VISIBLE_CLASS,
        force = show,
    )

    if (show) {
        securityVideo.play()
    } else {
        securityVideo.pause()
        securityVideo.currentTime = 0.0
    }
}

private const val TERMINAL_ELEMENT_ID = "terminal"
private const val TRAILER_ELEMENT_ID = "trailer"
private const val SECURITY_VIDEO_ELEMENT_ID = "security-video"
private const val MOBILE_INPUT_ELEMENT_ID = "mobile-input"
private const val VIDEO_VISIBLE_CLASS = "visible"

/**
 * Larger than any real line - just enough to keep word-wrapping from ever triggering.
 */
private const val UNWRAPPED_WIDTH = Int.MAX_VALUE

/**
 * Forwards named keys (Enter, PageUp/Down, ArrowLeft) to [TerminalEngine.dispatch] as
 * [Intent.KeyPressed]. Printable characters and Backspace go through [handleInput] instead, since
 * mobile keyboards don't reliably report them via `keydown`. Ctrl/Cmd/Alt combos are left alone so
 * browser/OS shortcuts (copy, devtools, refresh) keep working.
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
    if (key in NAMED_KEYS && key != "Backspace") {
        event.preventDefault()
        scope.launch { engine.dispatch(Intent.KeyPressed(key)) }
    }
}

/**
 * Diffs [mobileInput]'s value against [previousValue] to derive Backspace/character keystrokes -
 * robust across mobile keyboards/IMEs, where `InputEvent.data`/`inputType` aren't reliable (and
 * `data` isn't even exposed as nullable by this project's DOM bindings). Returns the value to
 * pass back in as [previousValue] on the next call.
 */
private fun handleInput(
    mobileInput: HTMLInputElement,
    previousValue: String,
    engine: TerminalEngine,
    scope: CoroutineScope,
): String {
    val newValue = mobileInput.value
    val commonPrefixLength = previousValue.commonPrefixWith(newValue).length
    val backspaces = previousValue.length - commonPrefixLength
    val inserted = newValue.substring(commonPrefixLength)

    scope.launch {
        repeat(backspaces) {
            engine.dispatch(Intent.KeyPressed("Backspace"))
        }
        for (c in inserted) {
            engine.dispatch(Intent.KeyPressed(c.toString()))
        }
    }

    return newValue
}
