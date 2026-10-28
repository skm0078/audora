// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/outputs/MadeAudioService.kt
// Purpose: Plays the audiobook and studio audio files the app saved, inside the app (ADR-068): its own small player
//          and media session beside the book's, so the reading service stays as it is; with its own sleep timer.

package io.github.skm0078.audora.playback.outputs

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.playback.jobs.OpenJobs

/**
 * Files are played as Android's own media player would play them: a playlist of the set's chapters, with the phone's
 * media notification and lock-screen controls. Both players take audio focus, so starting one pauses the other.
 * Where the listener stopped in each set is saved ([MadePositions]) on every pause, chapter change and stop. The
 * sleep timer ([MadeSleepTimer]) is set by the app's own player screen only, and published in the session's extras.
 */
class MadeAudioService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var positions: MadePositions
    private val sleep = MadeSleepTimer(SystemClock::elapsedRealtime)
    private val handler = Handler(Looper.getMainLooper())
    private val sleepNow = Runnable {
        AudoraLog.i(TAG, "Sleep timer: paused after its minutes")
        session?.player?.pause()
        clearSleep()
    }

    override fun onCreate() {
        super.onCreate()
        positions = MadePositions(this)
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(SPOKEN, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(PositionSaver(player))
        player.addListener(SleepWatcher())
        val builder = MediaSession.Builder(this, player).setId(SESSION_ID).setCallback(SleepCommands())
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
        handler.removeCallbacks(sleepNow)
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

    /** [args] from [MadeSleepContract.SLEEP]: minutes, chapter ends, or neither for off. */
    private fun setSleep(args: Bundle) {
        val minutes = args.getInt(MadeSleepContract.ARG_MINUTES, 0)
        val chapters = args.getInt(MadeSleepContract.ARG_CHAPTERS, 0)
        handler.removeCallbacks(sleepNow)
        when {
            minutes > 0 -> {
                sleep.minutes(minutes)
                sleep.remainingMs()?.let { handler.postDelayed(sleepNow, it) }
            }

            chapters > 0 -> sleep.chapters(chapters)

            else -> sleep.clear()
        }
        AudoraLog.i(TAG, "Sleep timer set, minutes=$minutes chapters=$chapters")
        publishSleep()
    }

    private fun clearSleep() {
        handler.removeCallbacks(sleepNow)
        sleep.clear()
        publishSleep()
    }

    private fun publishSleep() {
        val player = session?.player as? ExoPlayer
        player?.pauseAtEndOfMediaItems = sleep.pausesAtThisChaptersEnd
        session?.setSessionExtras(MadeSleepContract.extras(sleep, System.currentTimeMillis()))
    }

    /** Only this app's own screens set the timer; the notification and other apps get the usual commands. */
    private inner class SleepCommands : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            if (controller.packageName != packageName) return super.onConnect(session, controller)
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(MadeSleepContract.SLEEP)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction != MadeSleepContract.ACTION_SLEEP) {
                return super.onCustomCommand(session, controller, customCommand, args)
            }
            setSleep(args)
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    /** Counts chapter ends for a chapter timer; the pause at the last one ends it, as does the set's end. */
    private inner class SleepWatcher : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && sleep.chaptersLeft > 1) {
                sleep.chapterEnded()
                publishSleep()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM && sleep.chaptersLeft > 0) {
                AudoraLog.i(TAG, "Sleep timer: paused at the chapter's end")
                clearSleep()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED && sleep.isSet) clearSleep()
        }
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
