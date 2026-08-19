package com.aperturescience.terminal.minitel

import com.aperturescience.terminal.Mode
import com.aperturescience.terminal.TerminalEngine
import com.aperturescience.terminal.data.TerminalData
import fr.outadoc.minipavi.core.model.FunctionKey
import fr.outadoc.minipavi.core.model.GatewayRequest
import fr.outadoc.minipavi.core.model.ServiceResponse
import fr.outadoc.minipavi.videotex.buildVideotex

/** Drives one MiniPavi gateway call to completion, bridging its one-line-in/one-frame-out model
 * onto `TerminalEngine`'s turn API. Not yet exercised against a live gateway. */
suspend fun handleTurn(request: GatewayRequest<MinitelSessionState>): ServiceResponse<MinitelSessionState> {
    val state = request.state

    // Farewell was already shown last turn - now disconnect without touching TerminalEngine.
    if (state.pendingDisconnect) {
        return ServiceResponse(
            state = state,
            content = buildVideotex { clearAll() },
            command = ServiceResponse.Command.Disconnect,
        )
    }

    if (request.event is GatewayRequest.Event.Connection) {
        // Fresh engine so `uid` is randomly synthesized, not restored from the placeholder state.
        val engine = TerminalEngine(instantReveal = true)
        engine.setViewportWidth(ScreenChunker.WRAP_WIDTH)
        engine.bootTurn()
        return render(engine, chunkIndex = 0)
    }

    val engine = TerminalEngine(instantReveal = true, initialState = state.toEngineState())
    engine.setViewportWidth(ScreenChunker.WRAP_WIDTH)

    val chunksBefore = ScreenChunker.chunk(engine.liveLine.value)
    val lastChunkIndex = chunksBefore.lastIndex
    val functionKey = (request.event as? GatewayRequest.Event.KeyboardInput)?.key

    val mode = state.mode.toDomain()
    val q21PaginationActive = isQ21PaginationActive(mode)

    var chunkIndex = state.chunkIndex
    when {
        // More of the current turn's output to show - just scroll, no TerminalEngine call.
        // Suite is the consistent "more" key everywhere, including Q21 pagination below.
        chunkIndex < lastChunkIndex && functionKey == FunctionKey.Suite -> {
            chunkIndex += 1
        }
        chunkIndex > 0 && functionKey == FunctionKey.Retour -> {
            chunkIndex -= 1
        }
        // Correction/Annulation/Repetition/Guide/Sommaire: no TerminalEngine equivalent, just redisplay.
        functionKey == FunctionKey.Repetition ||
            functionKey == FunctionKey.Guide ||
            functionKey == FunctionKey.Sommaire ||
            functionKey == FunctionKey.Annulation ||
            functionKey == FunctionKey.Correction -> {
            // no-op
        }
        chunkIndex == lastChunkIndex && hasOpenInputZone(mode) && functionKey == FunctionKey.Envoi -> {
            engine.submitLine(request.userInput.firstOrNull().orEmpty())
            chunkIndex = 0
        }
        chunkIndex == lastChunkIndex && isAnyKeyMode(mode) && functionKey != null -> {
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
        // Show the farewell text this turn, disconnect on the next keypress (pendingDisconnect above).
        return render(engine, chunkIndex = 0, pendingDisconnect = true)
    }

    return render(engine, chunkIndex)
}

private fun hasOpenInputZone(mode: Mode): Boolean = mode is Mode.Login || mode is Mode.Shell || mode is Mode.Application

private fun isAnyKeyMode(mode: Mode): Boolean = mode is Mode.Notes || mode is Mode.Cake || mode is Mode.BossKey

private fun isQ21PaginationActive(mode: Mode): Boolean {
    if (mode !is Mode.Application) return false
    val question = TerminalData.questions.getOrNull(mode.questionNumber - 1) ?: return false
    return question.choices.size > TerminalEngine.PAGE_SIZE
}

private fun isPasswordPrompt(mode: Mode): Boolean = mode is Mode.Login.Password

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

    val openInputZone = isLastChunk && !pendingDisconnect && hasOpenInputZone(state.mode)

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
                spaceChar = " ",
                substituteChar = if (isPasswordPrompt(state.mode)) "*" else "",
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
