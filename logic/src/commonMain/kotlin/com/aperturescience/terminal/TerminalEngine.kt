package com.aperturescience.terminal

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

/**
 * The impure shell driving [TerminalReducer]: holds [EngineState], applies intents through the
 * reducer, and is the only place that ever calls `delay()`.
 */
class TerminalEngine(
    private val instantReveal: Boolean = false,
    initialState: EngineState? = null,
) {
    private val _state = MutableStateFlow(initialState ?: initialEngineState())

    /**
     * The engine's entire observable state - was three separate StateFlows (`liveLine`,
     * `annotations`, `exitRequested`); now just one, since [EngineState] carries everything.
     */
    val state: StateFlow<EngineState> = _state.asStateFlow()

    /**
     * Applies [intent] through [TerminalReducer.reduce], then runs every resulting effect to
     * completion before returning - a caller never has to separately await [state] settling.
     */
    suspend fun dispatch(intent: Intent) {
        val reduction = TerminalReducer.reduce(_state.value, intent)
        _state.value = reduction.state
        runEffects(reduction.effects)
    }

    private suspend fun runEffects(effects: List<Effect>) {
        for (effect in effects) {
            when (effect) {
                is Effect.RevealCharacters -> {
                    for (char in effect.chars) {
                        if (effect.delayMs > 0) maybeDelay(effect.delayMs.toLong())
                        dispatch(Intent.CharacterRevealed(char))
                    }
                    effect.thenDispatch?.let { dispatch(it) }
                }
                is Effect.Wait -> {
                    maybeDelay(effect.ms)
                    effect.thenDispatch?.let { dispatch(it) }
                }
            }
        }
    }

    /**
     * Every wall-clock suspension goes through here, so [instantReveal] hosts get zero-cost
     * content while interactive hosts keep real timing.
     */
    private suspend fun maybeDelay(ms: Long) {
        if (!instantReveal) {
            delay(ms.milliseconds)
        }
    }
}

private fun initialEngineState(): EngineState =
    EngineState(
        mode = Mode.Login.Initial,
        isAdmin = false,
        uid = synthesizeUid(),
        pageContent = "",
        input = "",
        wrapWidth = WRAP_WIDTH,
        isLocked = true,
    )

private fun synthesizeUid(): String {
    // 64 chars, matching the "64 digit UIN(+L)" prompt text and the original site.
    val chars = "0123456789abcdefghijklmnopqrstuvwxyz"
    return buildString {
        repeat(64) { append(chars[Random.nextInt(chars.length)]) }
    }
}
