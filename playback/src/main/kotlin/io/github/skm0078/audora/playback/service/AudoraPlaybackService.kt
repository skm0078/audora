// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/service/AudoraPlaybackService.kt
// Purpose: The Media3 MediaSessionService: foreground media service, notification, lock-screen and headset controls.

package io.github.skm0078.audora.playback.service

import android.app.PendingIntent
import android.content.ComponentCallbacks2
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
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.notes.BookSettingsStore
import io.github.skm0078.audora.core.notes.BookmarkStore
import io.github.skm0078.audora.core.notes.PronunciationStore
import io.github.skm0078.audora.core.resume.ResumeAnchorStore
import io.github.skm0078.audora.core.resume.ResumeLocator
import io.github.skm0078.audora.core.resume.ResumeRecorder
import io.github.skm0078.audora.pdf.PIPELINE_VERSION
import io.github.skm0078.audora.playback.R
import io.github.skm0078.audora.playback.book.LibraryBookSource
import io.github.skm0078.audora.playback.notes.BookContext
import io.github.skm0078.audora.playback.queue.MeasuredRenderAhead
import io.github.skm0078.audora.playback.reading.ReadingSource
import io.github.skm0078.audora.playback.session.BookSession
import io.github.skm0078.audora.playback.session.ItemTexts
import io.github.skm0078.audora.playback.session.LivePlaybackSpeed
import io.github.skm0078.audora.playback.session.QueueRegistry
import io.github.skm0078.audora.playback.session.SentenceTimings
import io.github.skm0078.audora.playback.session.SessionEnvironment
import io.github.skm0078.audora.playback.session.SessionSources
import io.github.skm0078.audora.playback.session.SleepOptionsStore
import io.github.skm0078.audora.playback.session.SpeechPlayer
import io.github.skm0078.audora.playback.upnext.QueueAdvance
import io.github.skm0078.audora.playback.upnext.UpNextStore
import io.github.skm0078.audora.playback.voice.EngineRenderer
import io.github.skm0078.audora.playback.voice.ReadingSetup
import io.github.skm0078.audora.playback.voice.RenderSpeedTracker
import io.github.skm0078.audora.playback.voice.SpeechStyleStore
import io.github.skm0078.audora.playback.voice.SpeechVoices
import io.github.skm0078.audora.playback.voice.VoiceEngines
import io.github.skm0078.audora.speech.punct.PunctuationModels
import io.github.skm0078.audora.speech.punct.RunOnPunctuation
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
 * screen or a headset (playback resumption after Audora was closed, P1-06). Nothing starts it on boot.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class AudoraPlaybackService : MediaSessionService() {
    @Inject
    lateinit var library: Library

    @Inject
    lateinit var tracker: ReadingSessionTracker

    @Inject
    lateinit var resumeAnchors: ResumeAnchorStore

    @Inject
    lateinit var bookSettings: BookSettingsStore

    @Inject
    lateinit var pronunciations: PronunciationStore

    @Inject
    lateinit var upNext: UpNextStore

    @Inject
    lateinit var bookmarks: BookmarkStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Resume anchor writes (P1-06). Never cancelled: the flush sent while the service is destroyed must land;
    // it holds no work afterwards.
    private val resumeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var resumption: PlaybackResumption? = null
    private val config = QueueConfig()
    private val registry = QueueRegistry()
    private val locator by lazy { ResumeLocator(resumeAnchors, PIPELINE_VERSION) }
    private lateinit var voices: SpeechVoices
    private lateinit var player: ExoPlayer
    private lateinit var speed: LivePlaybackSpeed
    private lateinit var bookSession: BookSession
    private var session: MediaSession? = null

    // Where the voice speaks in each clip, for the reader's word highlight (plan item 14).
    private val timings = SentenceTimings()

    override fun onCreate() {
        super.onCreate() // Hilt injects fields here.
        val style = SpeechStyleStore.forContext(this)
        // Expressiveness (plan item 8) is global, like the rest of the voice shaping; a change reloads the voice.
        val engines = VoiceEngines(this) { style.current.shaping.expressiveness }
        voices = SpeechVoices(engines)
        val sleepOptions = SleepOptionsStore.forContext(this)
        val book = BookContext(bookSettings, pronunciations, scope) { bookSession.restartRendering() }
        voices.bookVoice = book::voice
        // What is heard: the global style with the open book's own speed and pace on top (plan item 4).
        val heard = { book.style(style.current) }
        player = SpeechPlayer.build(this, registry, config)
        // The player's share of the listener's speed (ADR-033); the voice model speaks the rest.
        player.setPlaybackSpeed(style.config.playerSpeed(style.current.rate))
        speed = LivePlaybackSpeed(player)
        // The reading modes travel with the speech style; a change restarts the sentence playing (ADR-035).
        val reading = ReadingSource(modes = { style.current.modes })
        val stores = SessionStores(style, sleepOptions, book)
        val mediaSession = mediaSession(stores).also { session = it }
        bookSession = BookSession(
            player = player,
            registry = registry,
            sources = SessionSources(
                renderer = renderer(engines, stores, reading, heard),
                books = LibraryBookSource(this, library),
                texts = ResourceTexts(),
                // The model's share of the speed drains the queue like the player's share does (ADR-033).
                renderAhead = MeasuredRenderAhead(voices::speedOf) {
                    speed.value * style.config.modelRate(heard().rate)
                },
                reading = reading,
                sleepOptions = sleepOptions::current,
                onBook = { id ->
                    book.open(id)
                    player.setPlaybackSpeed(style.config.playerSpeed(heard().rate))
                },
                keepGoingToNextChapter = { upNext.queue.value.keepGoingToNextChapter },
                onBookFinished = queueAdvance()::onBookFinished,
            ),
            config = config,
            events = publisher(mediaSession, stores, heard),
            env = SessionEnvironment(
                scope = scope,
                extraction = Dispatchers.IO.limitedParallelism(1),
                elapsedRealtime = SystemClock::elapsedRealtime,
                wallClock = System::currentTimeMillis,
                shakes = AccelerometerShakes(this, config.sleep),
            ),
        )
        resumption = PlaybackResumption(
            anchors = resumeAnchors,
            library = library,
            locator = locator,
            scope = scope,
            player = player,
        ) { bookSession }
        AudoraLog.i(TAG, "Service created")
    }

    private fun mediaSession(stores: SessionStores): MediaSession {
        val callback =
            SessionCallback(packageName, scope, voices, stores, { bookSession }, { resumption }, mediaButtons())
        return MediaSession.Builder(this, player)
            .setCallback(callback)
            .apply { sessionActivity()?.let { setSessionActivity(it) } }
            .build()
    }

    /** A headset's play/pause button (plan item 20); a double press bookmarks and buzzes to confirm. */
    private fun mediaButtons() = MediaButtons(
        player = { player },
        bookSession = { bookSession },
        bookmarks = HeadsetBookmarks(
            store = bookmarks,
            scope = scope,
            pipelineVersion = PIPELINE_VERSION,
            clock = System::currentTimeMillis,
            onSaved = { buzz(config.sleep.buzzMs) },
        ),
    )

    /** Up next (plan item 25): a queued chapter starts on its page, a queued book where it was left. */
    private fun queueAdvance() = QueueAdvance(upNext, scope) { entry ->
        val page = entry.startPage
        if (page != null) {
            bookSession.playFrom(entry.bookId, page, charStart = 0, password = null)
        } else {
            bookSession.resume(entry.bookId, locator)
        }
    }

    private fun renderer(
        engines: VoiceEngines,
        stores: SessionStores,
        reading: ReadingSource,
        heard: () -> SpeechStyle,
    ) = EngineRenderer(
        voices,
        speed = RenderSpeedTracker(engines::speeds),
        style = heard,
        styleConfig = stores.style.config,
        reading = ReadingSetup(
            reading.speakability,
            chapterPages = { reading.chapterPages },
            fixer = { stores.book.fixer },
            scanned = { page -> reading.scanned(page) },
            runOn = RunOnPunctuation(punctuator = PunctuationModels.get(this)::punctuator),
        ),
        timings = timings,
    )

    private fun publisher(
        mediaSession: MediaSession,
        stores: SessionStores,
        heard: () -> SpeechStyle,
    ): SessionPublisher {
        val recorder = ResumeRecorder(resumeAnchors, resumeScope, PIPELINE_VERSION, System::currentTimeMillis)
        return SessionPublisher(
            mediaSession,
            DiagnosticsBridge(tracker, scope),
            ResumeBridge(recorder, resumeScope),
            heard,
            stores.sleepOptions::current,
            bookSettings = { stores.book.settings },
            timings = timings,
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /**
     * Gives back the neural models not in use (P4-02). Since API 34 Android delivers only UI_HIDDEN and BACKGROUND
     * (the RUNNING_* levels are deprecated and no longer sent). UI_HIDDEN just means the user left the app, which
     * is how people listen, so it keeps both models; BACKGROUND and above (MODERATE and COMPLETE on older releases)
     * mean the process is on the kill list, where a second ~150 MB model is the first thing to give back.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            AudoraLog.i(TAG, "Trim memory level=$level, book=${bookSession.bookId?.logLabel}")
            voices.trimMemory()
        }
    }

    override fun onDestroy() {
        AudoraLog.i(TAG, "Service destroyed, book=${bookSession.bookId?.logLabel}")
        bookSession.release()
        speed.release()
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
