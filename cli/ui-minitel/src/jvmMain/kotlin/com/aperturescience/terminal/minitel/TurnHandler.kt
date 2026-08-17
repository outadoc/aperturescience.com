package com.aperturescience.terminal.minitel

import com.aperturescience.terminal.TerminalEngine
import com.aperturescience.terminal.data.TerminalData
import fr.outadoc.minipavi.core.model.FunctionKey
import fr.outadoc.minipavi.core.model.GatewayRequest
import fr.outadoc.minipavi.core.model.ServiceResponse
import fr.outadoc.minipavi.videotex.buildVideotex

/**
 * Drives one MiniPavi gateway call to completion. See `TerminalEngine`'s "batch API" doc and
 * [ScreenChunker] for the two mismatches this bridges: minipavi-kotlin gives us one full,
 * already-validated line of input (or a bare function-key press) and expects exactly one
 * Vidéotex frame back, synchronously, with no wall-clock delay and nothing persisted in memory
 * between calls - everything survives in [MinitelSessionState], round-tripped through the
 * gateway.
 *
 * NOTE: this hasn't been exercised against a live MiniPavi gateway (no such environment was
 * reachable while writing it) - the `submitWith`/function-key wiring in particular is a
 * best-effort reading of minipavi-kotlin's model and worth validating for real before relying on
 * it.
 */
suspend fun handleTurn(request: GatewayRequest<MinitelSessionState>): ServiceResponse<MinitelSessionState> {
    val state = request.state

    // Second half of the farewell sequence (see below): the previous turn already showed the
    // farewell message and persisted this flag - now that the user pressed something, actually
    // disconnect, without touching TerminalEngine at all.
    if (state.pendingDisconnect) {
        return ServiceResponse(
            state = state,
            content = buildVideotex { clearAll() },
            command = ServiceResponse.Command.Disconnect,
        )
    }

    if (request.event is GatewayRequest.Event.Connection) {
        // A brand-new session: construct a genuinely fresh engine (no initialState) so `uid` is
        // randomly synthesized, exactly like a fresh TerminalEngine() on the other two frontends -
        // NOT restored from MinitelSessionState.initial()'s placeholder.
        val engine = TerminalEngine(instantReveal = true)
        engine.bootTurn()
        return render(engine, chunkIndex = 0)
    }

    val engine = TerminalEngine(instantReveal = true, initialState = state.toEngineState())
    engine.setViewportWidth(ScreenChunker.COLUMNS)

    val chunksBefore = ScreenChunker.chunk(engine.liveLine.value)
    val lastChunkIndex = chunksBefore.lastIndex
    val functionKey = (request.event as? GatewayRequest.Event.KeyboardInput)?.key

    val q21PaginationActive = isQ21PaginationActive(state.entryMode, state.qon)

    var chunkIndex = state.chunkIndex
    when {
        // Still more of the current turn's output to show - just scroll, no TerminalEngine call.
        chunkIndex < lastChunkIndex && functionKey == FunctionKey.Suite -> {
            chunkIndex += 1
        }
        chunkIndex > 0 && functionKey == FunctionKey.Retour -> {
            chunkIndex -= 1
        }
        // Correction/Annulation are handled by the gateway itself during line editing (never
        // reach us meaningfully); Repetition/Guide/Sommaire have no TerminalEngine equivalent -
        // all of them just redisplay the current chunk unchanged.
        functionKey == FunctionKey.Repetition ||
            functionKey == FunctionKey.Guide ||
            functionKey == FunctionKey.Sommaire ||
            functionKey == FunctionKey.Annulation ||
            functionKey == FunctionKey.Correction -> {
            // no-op
        }
        chunkIndex == lastChunkIndex && hasOpenInputZone(state.entryMode) && functionKey == FunctionKey.Envoi -> {
            engine.submitLine(request.userInput.firstOrNull().orEmpty())
            chunkIndex = 0
        }
        chunkIndex == lastChunkIndex && isAnyKeyMode(state.entryMode) && functionKey != null -> {
            engine.advance()
            chunkIndex = 0
        }
        chunkIndex == lastChunkIndex && q21PaginationActive && functionKey == FunctionKey.Suite -> {
            engine.page(TerminalEngine.PAGE_SIZE)
            chunkIndex = 0
        }
        chunkIndex == lastChunkIndex && q21PaginationActive && functionKey == FunctionKey.Retour -> {
            engine.page(-TerminalEngine.PAGE_SIZE)
            chunkIndex = 0
        }
        else -> {
            // No recognized action on the current page - redisplay unchanged.
        }
    }

    if (engine.exitRequested.value) {
        // The farewell text is already in engine.liveLine - show it as a normal page this turn,
        // but don't disconnect yet: wait for the user's next keypress (handled at the top of this
        // function via pendingDisconnect), matching how ui-terminal/ui-web show a final message
        // before ending the session rather than cutting it off mid-sentence.
        return render(engine, chunkIndex = 0, pendingDisconnect = true)
    }

    return render(engine, chunkIndex)
}

private fun hasOpenInputZone(entryMode: Int): Boolean =
    entryMode == TerminalEngine.MODE_LOGIN ||
        entryMode == TerminalEngine.MODE_SHELL ||
        entryMode == TerminalEngine.MODE_APPLICATION

private fun isAnyKeyMode(entryMode: Int): Boolean =
    entryMode == TerminalEngine.MODE_NOTES ||
        entryMode == TerminalEngine.MODE_CAKE ||
        entryMode == TerminalEngine.MODE_BOSSKEY

private fun isQ21PaginationActive(
    entryMode: Int,
    qon: Int,
): Boolean {
    if (entryMode != TerminalEngine.MODE_APPLICATION) return false
    val question = TerminalData.questions.getOrNull(qon - 1) ?: return false
    return question.choices.size > TerminalEngine.PAGE_SIZE
}

private fun isPasswordPrompt(
    entryMode: Int,
    qon: Int,
): Boolean = entryMode == TerminalEngine.MODE_LOGIN && (qon == 2 || qon == 3)

/** Chunks [engine]'s current output, renders [chunkIndex]'s slice as one Vidéotex frame, and
 * persists everything needed to resume on the next call. */
private fun render(
    engine: TerminalEngine,
    chunkIndex: Int,
    pendingDisconnect: Boolean = false,
): ServiceResponse<MinitelSessionState> {
    val chunks = ScreenChunker.chunk(engine.liveLine.value)
    val safeIndex = chunkIndex.coerceIn(0, chunks.lastIndex)
    val lines = chunks[safeIndex]
    val isLastChunk = safeIndex == chunks.lastIndex
    val state = engine.captureState()

    val openInputZone = isLastChunk && !pendingDisconnect && hasOpenInputZone(state.entryMode)

    val content =
        buildVideotex {
            clearAll()
            lines.forEach { appendLine(it) }
        }

    val command =
        if (openInputZone) {
            val lastLine = lines.lastOrNull().orEmpty()
            val inputCol = (lastLine.length + 1).coerceAtMost(ScreenChunker.COLUMNS)
            val inputLine = lines.size.coerceIn(1, ScreenChunker.ROWS_PER_SCREEN)
            val inputLength = (ScreenChunker.COLUMNS - lastLine.length).coerceAtLeast(1)
            ServiceResponse.Command.InputText(
                col = inputCol,
                line = inputLine,
                length = inputLength,
                substituteChar = if (isPasswordPrompt(state.entryMode, state.qon)) "*" else "",
                submitWith = setOf(FunctionKey.Envoi, FunctionKey.Suite, FunctionKey.Retour),
            )
        } else {
            ServiceResponse.Command.Display
        }

    return ServiceResponse(
        state = MinitelSessionState.from(state, chunkIndex = safeIndex, pendingDisconnect = pendingDisconnect),
        content = content,
        command = command,
    )
}
