package com.aperturescience.terminal

/**
 * Time-based work `TerminalReducer.reduce` hands back for `TerminalEngine` to run - never
 * lives in the reducer itself. Each case can carry an [Intent] to dispatch once it finishes.
 */
sealed interface Effect {
    /**
     * Dispatch [Intent.CharacterRevealed] for each of [chars], pausing [delayMs] ms between each,
     * then dispatch [thenDispatch] if present. [chars] is already fully resolved text.
     */
    data class RevealCharacters(
        val chars: List<Char>,
        val delayMs: Int,
        val thenDispatch: Intent? = null,
    ) : Effect

    /**
     * A pure pacing gap with no reveal of its own.
     */
    data class Wait(
        val ms: Long,
        val thenDispatch: Intent? = null,
    ) : Effect
}

/**
 * What one `reduce` call produces: the next state, plus any effects the runner must carry out.
 */
data class Reduction(
    val state: EngineState,
    val effects: List<Effect> = emptyList(),
)
