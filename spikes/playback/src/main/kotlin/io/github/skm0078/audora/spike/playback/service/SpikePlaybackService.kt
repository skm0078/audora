// File: spikes/playback/src/main/kotlin/io/github/skm0078/audora/spike/playback/service/SpikePlaybackService.kt
// Purpose: The Media3 MediaSessionService that owns the run: foreground service, notification and lock-screen controls.

package io.github.skm0078.audora.spike.playback.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.spike.playback.config.SpikePreset
import io.github.skm0078.audora.spike.playback.ui.SpikeActivity

/**
 * The session always has a player: an idle placeholder until the first run, then each run's own player
 * (a run's player options, such as per-stream progression, can only be set when the player is built).
 *
 * Runs start only through [SpikeCommands.START] sent by the activity on a button tap, or the notification's
 * play button, so playback always begins from a user action (C7).
 */
@OptIn(UnstableApi::class)
class SpikePlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var run: PlaybackRun? = null
    private var placeholderPlayer: ExoPlayer? = null
    private lateinit var store: RunStore

    override fun onCreate() {
        super.onCreate()
        store = RunStore(this)
        val idle = ExoPlayer.Builder(this).build().also { placeholderPlayer = it }
        val openActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, SpikeActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, idle)
            .setSessionActivity(openActivity)
            .setCallback(SessionCallback())
            .build()
        ActiveRun.service = this
        AudoraLog.i(TAG, "Service created")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        AudoraLog.i(TAG, "Service destroyed; run active=${run != null}")
        ActiveRun.service = null
        run?.finish()
        run = null
        session?.release()
        session = null
        placeholderPlayer?.release()
        placeholderPlayer = null
        super.onDestroy()
    }

    /** Live report for the activity; null when no run is active. */
    fun liveReport(): String? = run?.report()

    private fun startRun(preset: SpikePreset, ownWakeLock: Boolean): SessionResult {
        val config = preset.config.copy(ownWakeLock = ownWakeLock)
        val problems = config.problems()
        if (problems.isNotEmpty()) {
            AudoraLog.w(TAG, "Refusing run ${preset.name}: $problems")
            return SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE)
        }
        stopRun()
        val newRun = PlaybackRun(this, preset, config, store)
        val currentSession = checkNotNull(session) { "Session missing in startRun" }
        currentSession.player = newRun.player
        run = newRun
        newRun.start()
        return SessionResult(SessionResult.RESULT_SUCCESS)
    }

    private fun stopRun(): SessionResult {
        val current = run ?: return SessionResult(SessionResult.RESULT_SUCCESS)
        // Hand the session back to the idle player before the run releases its own.
        placeholderPlayer?.let { idle -> session?.player = idle }
        current.finish()
        run = null
        return SessionResult(SessionResult.RESULT_SUCCESS)
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SpikeCommands.START)
                .add(SpikeCommands.STOP)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            val result = when (customCommand.customAction) {
                SpikeCommands.ACTION_START -> startRun(
                    preset = SpikeCommands.presetFrom(args),
                    ownWakeLock = args.getBoolean(SpikeCommands.ARG_OWN_WAKE_LOCK, false),
                )

                SpikeCommands.ACTION_STOP -> stopRun()

                else -> SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED)
            }
            return Futures.immediateFuture(result)
        }
    }

    private companion object {
        const val TAG = "S3.Service"
    }
}

/** Same-process handle the activity uses to read live numbers; the spike has a single process. */
object ActiveRun {
    @Volatile
    var service: SpikePlaybackService? = null
}
