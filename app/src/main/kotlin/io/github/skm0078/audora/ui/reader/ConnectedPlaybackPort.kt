// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ConnectedPlaybackPort.kt
// Purpose: A player controller for a screen that only shows and steers playback: the mini player, the lock's saver.

package io.github.skm0078.audora.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.skm0078.audora.playback.service.PlaybackServiceLife
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * A player controller connected while visible and while the playback service is up (ADR
 * 2026-10-30-lazy-player-service). It never starts the service: a fresh app shows no mini player anyway, and the
 * service (its player, voices and session) is the largest thing a cold start built. The reader, the tile and the car
 * start it; once up, this joins it, and a connected controller keeps it bound as before.
 */
@Composable
fun rememberConnectedPlaybackPort(): MediaControllerPlaybackPort {
    val context = LocalContext.current.applicationContext
    val port = remember { MediaControllerPlaybackPort(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Driven by flows, like the reader's connection: Compose does not recompose while the activity is stopped.
    LaunchedEffect(port, lifecycle) {
        val visible = lifecycle.currentStateFlow.map { it.isAtLeast(Lifecycle.State.STARTED) }
        joinWanted(visible, PlaybackServiceLife.up).collect { wanted -> if (wanted) port.connect() else port.release() }
    }
    DisposableEffect(port) { onDispose { port.release() } }
    return port
}

/** Connected only while shown and the service is up: connecting while it is down would start it. */
fun joinWanted(visible: Flow<Boolean>, serviceUp: Flow<Boolean>): Flow<Boolean> =
    combine(visible, serviceUp) { shown, up -> shown && up }.distinctUntilChanged()
