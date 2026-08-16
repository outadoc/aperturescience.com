package com.aperturescience.terminal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.jakewharton.mosaic.layout.onKeyEvent
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.Text

@Composable
fun App() {
    val engine = remember { TerminalEngine() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        engine.boot(scope)
    }

    Text(
        value = engine.liveLine,
        modifier = Modifier.onKeyEvent { event ->
            engine.onKeyEvent(event.key, event.ctrl)
        },
    )
}
