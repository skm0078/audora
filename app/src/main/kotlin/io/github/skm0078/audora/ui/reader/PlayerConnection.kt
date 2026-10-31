// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/PlayerConnection.kt
// Purpose: Keeps a player controller connected while it is wanted: the reader's while shown or while voice commands
//          listen with it hidden, the mini player's and the lock's while shown and the service is up.

package io.github.skm0078.audora.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Connects the reader's [port] per [playerWanted]. */
@Composable
fun PlayerConnectionEffect(port: MediaControllerPlaybackPort, inBackground: Flow<Boolean>) {
    val shown = rememberShown()
    ConnectWhileEffect(port, remember(shown, inBackground) { playerWanted(shown, inBackground) })
}

/**
 * Connects [port] while [wanted] holds and releases it otherwise, and when it leaves the composition. Driven by flows,
 * not recomposition: Compose does not recompose while the activity is stopped, which is exactly when a controller must
 * let the service go.
 */
@Composable
fun ConnectWhileEffect(port: MediaControllerPlaybackPort, wanted: Flow<Boolean>) {
    LaunchedEffect(port, wanted) { wanted.collect { if (it) port.connect() else port.release() } }
    DisposableEffect(port) { onDispose { port.release() } }
}

/** Whether the screen is shown (its lifecycle at least started), as a flow that moves while Compose is idle. */
@Composable
fun rememberShown(): Flow<Boolean> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return remember(lifecycle) { lifecycle.currentStateFlow.map { it.isAtLeast(Lifecycle.State.STARTED) } }
}
