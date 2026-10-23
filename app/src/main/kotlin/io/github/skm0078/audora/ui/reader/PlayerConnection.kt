// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/PlayerConnection.kt
// Purpose: Keeps the reader's player connected while it is shown, or while voice commands listen with it hidden.

package io.github.skm0078.audora.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Connects [port] per [playerWanted]. Driven by flows, not recomposition: Compose does not recompose while the
 * activity is stopped, which is exactly when a session ending must let the player go.
 */
@Composable
fun PlayerConnectionEffect(port: MediaControllerPlaybackPort, inBackground: Flow<Boolean>) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(port, lifecycle, inBackground) {
        val visible = lifecycle.currentStateFlow.map { it.isAtLeast(Lifecycle.State.STARTED) }
        playerWanted(visible, inBackground).collect { wanted -> if (wanted) port.connect() else port.release() }
    }
    DisposableEffect(port) { onDispose { port.release() } }
}
