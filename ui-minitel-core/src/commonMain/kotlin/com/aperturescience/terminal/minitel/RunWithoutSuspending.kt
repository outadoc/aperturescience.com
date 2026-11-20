package com.aperturescience.terminal.minitel

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * Runs [block] synchronously on the calling thread and returns its result, for suspending
 * `TerminalEngine` calls that never actually suspend (every engine in this module uses
 * `instantReveal = true`, so `maybeDelay()` never reaches `delay()`).
 *
 * Replaces `runBlocking`, which doesn't exist on Kotlin/JS (Cloudflare Workers). If [block] ever
 * does suspend, this fails loudly instead of returning a half-finished turn.
 */
internal fun <T> runWithoutSuspending(block: suspend () -> T): T {
    var result: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { result = it })
    return checkNotNull(result) {
        "Expected the Minitel turn to complete without suspending - is instantReveal still enabled?"
    }.getOrThrow()
}
