package com.aperturescience.terminal.minitel

import com.aperturescience.terminal.EngineState
import com.aperturescience.terminal.Intent
import com.aperturescience.terminal.TerminalEngine
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

/**
 * The `TState` MiniPavi round-trips every call. Mirrors [EngineState] field-for-field, plus
 * [chunkIndex] and [pendingDisconnect] - omits fields that are always default between turns.
 */
@Serializable
data class MinitelSessionState(
    val mode: MinitelMode,
    val isAdmin: Boolean,
    val uid: String,
    val pageContent: String,
    val input: String,
    val wrapWidth: Int,
    val isLocked: Boolean,
    val annotations: List<MinitelTextAnnotation> = emptyList(),
    val chunkIndex: Int = 0,
    val pendingDisconnect: Boolean = false,
) {
    fun toEngineState(): EngineState =
        EngineState(
            mode = mode.toDomain(),
            isAdmin = isAdmin,
            uid = uid,
            pageContent = pageContent,
            input = input,
            wrapWidth = wrapWidth,
            isLocked = isLocked,
            annotations = annotations.map { it.toDomain() },
        )

    companion object {
        fun from(
            engineState: EngineState,
            chunkIndex: Int = 0,
            pendingDisconnect: Boolean = false,
        ): MinitelSessionState =
            with(engineState) {
                MinitelSessionState(
                    mode = mode.toData(),
                    isAdmin = isAdmin,
                    uid = uid,
                    pageContent = pageContent,
                    input = input,
                    wrapWidth = wrapWidth,
                    isLocked = isLocked,
                    annotations = annotations.map { it.toData() },
                    chunkIndex = chunkIndex,
                    pendingDisconnect = pendingDisconnect,
                )
            }

        /**
         * `minitelService`'s `initialState` param - used whenever the gateway sends a request
         * with no (or unparseable) context, which isn't guaranteed to line up with a
         * [fr.outadoc.minipavi.core.model.GatewayRequest.Event.Connection] event, so this has to
         * be a real booted state, not a half-valid placeholder.
         */
        fun initial(): MinitelSessionState =
            from(
                engineState =
                    runBlocking {
                        val engine = TerminalEngine(instantReveal = true)
                        engine.dispatch(Intent.ViewportResized(ScreenChunker.WRAP_WIDTH))
                        engine.dispatch(Intent.Boot)
                        engine.state.value
                    },
            )
    }
}
