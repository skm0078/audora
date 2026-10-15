// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/service/AudoraPlaybackService.kt
// Purpose: The Media3 MediaSessionService: foreground media service, notification, lock-screen and headset controls.

package io.github.skm0078.audora.playback.service

import android.app.PendingIntent
import android.content.Intent
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import io.github.skm0078.audora.core.config.QueueConfig
import io.github.skm0078.audora.core.diagnostics.ReadingSessionTracker
import io.github.skm0078.audora.core.library.Library
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.playback.R
import io.github.skm0078.audora.playback.book.LibraryBookSource
import io.github.skm0078.audora.playback.session.BookSession
import io.github.skm0078.audora.playback.session.ItemTexts
import io.github.skm0078.audora.playback.session.QueueRegistry
import io.github.skm0078.audora.playback.session.SessionEnvironment
import io.github.skm0078.audora.playback.session.SessionSources
import io.github.skm0078.audora.playback.session.SpeechPlayer
import io.github.skm0078.audora.playback.voice.EngineRenderer
import io.github.skm0078.audora.playback.voice.SpeechVoices
import io.github.skm0078.audora.playback.voice.VoiceEngines
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Synthesis and playback run here only (Build Spec C9). One player for the service's life; [BookSession]
 * swaps books and voices on it. Media3 keeps the service in the foreground (type mediaPlayback) while the
 * player plays or buffers with playWhenReady, which is exactly when a sentence is being synthesized (C5).
 *
 * Playback starts only from a user action (C7): PLAY_FROM from the UI, or play on the notification, lock
 * screen or a headset. Nothing starts it on boot, and there is no playback resumption until P1-06.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class AudoraPlaybackService : MediaSessionService() {
    @Inject
    lateinit var library: Library

    @Inject
    lateinit var tracker: ReadingSessionTracker

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val config = QueueConfig()
    private val registry = QueueRegistry()
    private lateinit var voices: SpeechVoices
    private lateinit var player: ExoPlayer
    private lateinit var bookSession: BookSession
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate() // Hilt injects fields here.
        voices = SpeechVoices(VoiceEngines(this))
        player = SpeechPlayer.build(this, registry, config)
        val diagnostics = DiagnosticsBridge(tracker, scope)
        val callback = SessionCallback(packageName, scope, voices) { bookSession }
        val mediaSession = MediaSession.Builder(this, player)
            .setCallback(callback)
            .apply { sessionActivity()?.let { setSessionActivity(it) } }
            .build()
        session = mediaSession
        bookSession = BookSession(
            player = player,
            registry = registry,
            sources = SessionSources(EngineRenderer(voices), LibraryBookSource(this, library), ResourceTexts()),
            config = config,
            events = SessionPublisher(mediaSession, diagnostics),
            env = SessionEnvironment(
                scope = scope,
                extraction = Dispatchers.IO.limitedParallelism(1),
                elapsedRealtime = SystemClock::elapsedRealtime,
                wallClock = System::currentTimeMillis,
            ),
        )
        AudoraLog.i(TAG, "Service created")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        AudoraLog.i(TAG, "Service destroyed, book=${bookSession.bookId?.logLabel}")
        bookSession.release()
        session?.release()
        session = null
        player.release()
        voices.release()
        scope.cancel()
        super.onDestroy()
    }

    /** Tapping the notification opens the app; :playback does not know its activity, the launcher does. */
    private fun sessionActivity(): PendingIntent? {
        val launch: Intent = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return PendingIntent.getActivity(
            this,
            0,
            launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private inner class ResourceTexts : ItemTexts {
        override fun pageOf(page: Int, pageCount: Int): String = getString(R.string.playback_page_of, page, pageCount)

        override fun loadingNextPage(): String = getString(R.string.playback_loading_next_page)
    }

    private companion object {
        const val TAG = "Playback.Service"
    }
}
