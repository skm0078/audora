// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/session/LivePlaybackSpeed.kt
// Purpose: The player's playback speed, readable from any thread without touching the player (P4-02, ADR-027).

package io.github.skm0078.audora.playback.session

import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player

/**
 * Mirrors [Player.getPlaybackParameters]' speed for the render thread: the render-ahead policy's start hold runs
 * there under the queue's lock, and ExoPlayer may only be called on its application (main) thread. Create, and
 * [release], on the main thread; [value] may be read from any thread.
 */
class LivePlaybackSpeed(private val player: Player) : Player.Listener {
    @Volatile
    var value: Double = player.playbackParameters.speed.toDouble()
        private set

    init {
        player.addListener(this)
    }

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
        value = playbackParameters.speed.toDouble()
    }

    fun release() = player.removeListener(this)
}
