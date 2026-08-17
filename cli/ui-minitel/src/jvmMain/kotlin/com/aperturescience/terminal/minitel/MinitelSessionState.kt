package com.aperturescience.terminal.minitel

import com.aperturescience.terminal.EngineState
import kotlinx.serialization.Serializable

/**
 * The `TState` minipavi-kotlin's `minitelService<MinitelSessionState>(...)` round-trips through
 * the MiniPavi gateway on every call - the only place in this module that touches
 * `kotlinx.serialization`, mirroring [EngineState] field-for-field plus two fields with no
 * `EngineState` equivalent: [chunkIndex] (this adapter's own screen-height pagination cursor
 * within the last turn's output, see [ScreenChunker]) and [pendingDisconnect] (farewell shown,
 * waiting for the next keypress to actually disconnect - see [TurnHandler]).
 */
@Serializable
data class MinitelSessionState(
    val entryMode: Int,
    val qon: Int,
    val isCj: Boolean,
    val notesPage: Int,
    val pageOffset: Int,
    val gladosHeader: String,
    val gladosPrompt: String,
    val gladosMessage: String,
    val uid: String,
    val pageContent: String,
    val input: String,
    val wrapWidth: Int,
    val isLocked: Boolean,
    val chunkIndex: Int = 0,
    val pendingDisconnect: Boolean = false,
) {
    fun toEngineState(): EngineState =
        EngineState(
            entryMode = entryMode,
            qon = qon,
            isCj = isCj,
            notesPage = notesPage,
            pageOffset = pageOffset,
            gladosHeader = gladosHeader,
            gladosPrompt = gladosPrompt,
            gladosMessage = gladosMessage,
            uid = uid,
            pageContent = pageContent,
            input = input,
            wrapWidth = wrapWidth,
            isLocked = isLocked,
        )

    companion object {
        fun from(
            engineState: EngineState,
            chunkIndex: Int = 0,
            pendingDisconnect: Boolean = false,
        ): MinitelSessionState =
            with(engineState) {
                MinitelSessionState(
                    entryMode = entryMode,
                    qon = qon,
                    isCj = isCj,
                    notesPage = notesPage,
                    pageOffset = pageOffset,
                    gladosHeader = gladosHeader,
                    gladosPrompt = gladosPrompt,
                    gladosMessage = gladosMessage,
                    uid = uid,
                    pageContent = pageContent,
                    input = input,
                    wrapWidth = wrapWidth,
                    isLocked = isLocked,
                    chunkIndex = chunkIndex,
                    pendingDisconnect = pendingDisconnect,
                )
            }

        /**
         * Placeholder state for `minitelService`'s `initialState` parameter. Never actually
         * rendered: [TurnHandler] special-cases `GatewayRequest.Event.Connection` (every brand
         * new Minitel session) to construct a fresh `TerminalEngine(instantReveal = true)` with
         * no `initialState` at all, so `uid` is genuinely randomly synthesized rather than
         * picking up this placeholder's empty one - see `TerminalEngine`'s
         * `instantReveal`/`initialState` constructor doc.
         */
        fun initial(): MinitelSessionState =
            MinitelSessionState(
                entryMode = 0,
                qon = 0,
                isCj = false,
                notesPage = 0,
                pageOffset = 0,
                gladosHeader = "",
                gladosPrompt = "",
                gladosMessage = "",
                uid = "",
                pageContent = "",
                input = "",
                wrapWidth = ScreenChunker.COLUMNS,
                isLocked = true,
            )
    }
}
