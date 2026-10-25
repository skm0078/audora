// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/outputs/MadeAudioService.kt
// Purpose: Plays the audiobook and studio audio files the app saved, inside the app (ADR-068): its own small player
//          and media session beside the book's, so the reading service stays as it is.

package io.github.skm0078.audora.playback.outputs

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.playback.jobs.OpenJobs

/**
 * Files are played as Android's own media player would play them: a playlist of the set's chapters, with the phone's
 * media notification and lock-screen controls. Both players take audio focus, so starting one pauses the other.
 * Where the listener stopped in each set is saved ([MadePositions]) on every pause, chapter change and stop.
 */
class MadeAudioService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var positions: MadePositions

    override fun onCreate() {
        super.onCreate()
        positions = MadePositions(this)
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(SPOKEN, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(PositionSaver(player))
        val builder = MediaSession.Builder(this, player).setId(SESSION_ID)
        OpenJobs.appIntent(this)?.let(builder::setSessionActivity)
        session = builder.build()
        AudoraLog.i(TAG, "Made audio player created")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Swiped away from recents while paused: nothing to keep playing, so the notification goes too. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            save(player)
            player.release()
            release()
        }
        session = null
        AudoraLog.i(TAG, "Made audio player released")
        super.onDestroy()
    }

    private fun save(player: Player) {
        val item = player.currentMediaItem ?: return
        val set = MadePositions.setOf(item.mediaId) ?: return
        positions.save(set, player.currentMediaItemIndex, player.currentPosition)
    }

    private inner class PositionSaver(private val player: Player) : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) save(player)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = save(player)

        override fun onPlaybackStateChanged(playbackState: Int) {
            // The last chapter played to its end: the next start begins the set again, not at its last second.
            if (playbackState == Player.STATE_ENDED) {
                player.currentMediaItem?.mediaId?.let(MadePositions::setOf)?.let(positions::clear)
            }
        }
    }

    companion object {
        private const val TAG = "Library.Player"
        const val SESSION_ID = "made-audio"
        private val SPOKEN = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()
    }
}
