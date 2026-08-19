package com.aperturescience.terminal.minitel

import com.aperturescience.terminal.EngineState
import kotlinx.serialization.Serializable

/** The `TState` MiniPavi round-trips every call. Mirrors [EngineState] field-for-field, plus
 * [chunkIndex] (see [ScreenChunker]) and [pendingDisconnect] (see [TurnHandler]). */
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

        /** Placeholder for `minitelService`'s `initialState` param - never actually rendered,
         * since [TurnHandler] builds a fresh `TerminalEngine` for every new session instead. */
        fun initial(): MinitelSessionState =
            MinitelSessionState(
                mode = MinitelMode.Login.Initial,
                isAdmin = false,
                uid = "",
                pageContent = "",
                input = "",
                wrapWidth = ScreenChunker.WRAP_WIDTH,
                isLocked = true,
            )
    }
}
