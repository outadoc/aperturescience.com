@file:OptIn(ExperimentalJsExport::class)

package com.aperturescience.terminal.minitel.worker

import com.aperturescience.terminal.minitel.MinitelSessionState
import com.aperturescience.terminal.minitel.SERVICE_VERSION
import com.aperturescience.terminal.minitel.handleTurn
import fr.outadoc.minipavi.core.MinitelServiceHandler
import fr.outadoc.minipavi.core.cloudflare.ConsoleMinitelLogger
import fr.outadoc.minipavi.core.cloudflare.Request
import fr.outadoc.minipavi.core.cloudflare.Response
import fr.outadoc.minipavi.core.cloudflare.toFetchHandler
import kotlin.js.Promise

private val logger = ConsoleMinitelLogger()

private val handler =
    MinitelServiceHandler<MinitelSessionState>(
        version = SERVICE_VERSION,
        initialState = { MinitelSessionState.initial() },
        logger = logger,
    ) { request ->
        handleTurn(request)
    }.toFetchHandler(path = "/", logger = logger)

/**
 * The Worker's entry point, re-exported as the default export's `fetch` by `worker/index.mjs`.
 *
 * A function rather than a property: Kotlin/JS exports properties as getters.
 */
@JsExport
fun fetch(request: Request): Promise<Response> = handler(request)
