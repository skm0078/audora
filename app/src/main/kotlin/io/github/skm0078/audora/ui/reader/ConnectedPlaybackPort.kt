// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ConnectedPlaybackPort.kt
// Purpose: A player controller for a screen that only shows and steers playback: the mini player, the lock's saver.

package io.github.skm0078.audora.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect

/** A player controller connected only while visible: a connected one keeps the playback service bound. */
@Composable
fun rememberConnectedPlaybackPort(): MediaControllerPlaybackPort {
    val context = LocalContext.current.applicationContext
    val port = remember { MediaControllerPlaybackPort(context) }
    LifecycleStartEffect(port) {
        port.connect()
        onStopOrDispose { port.release() }
    }
    return port
}
