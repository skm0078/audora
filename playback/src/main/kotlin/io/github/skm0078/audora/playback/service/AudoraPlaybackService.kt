// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/service/AudoraPlaybackService.kt
// Purpose: The Media3 MediaLibraryService: foreground media service, notification, lock screen, headset, Android Auto.

package io.github.skm0078.audora.playback.service

import android.app.PendingIntent
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import dagger.hilt.android.AndroidEntryPoint
import io.github.skm0078.audora.core.config.QueueConfig
import io.github.skm0078.audora.core.diagnostics.ReadingSessionTracker
import io.github.skm0078.audora.core.library.Library
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.AfterSentence
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.notes.BookSettingsStore
import io.github.skm0078.audora.core.notes.BookmarkStore
import io.github.skm0078.audora.core.notes.PronunciationStore
import io.github.skm0078.audora.core.result.getOrNull
import io.github.skm0078.audora.core.resume.ResumeAnchorStore
import io.github.skm0078.audora.core.resume.ResumeLocator
import io.github.skm0078.audora.core.resume.ResumeRecorder
import io.github.skm0078.audora.pdf.PIPELINE_VERSION
import io.github.skm0078.audora.playback.R
import io.github.skm0078.audora.playback.book.LibraryBookSource
import io.github.skm0078.audora.playback.browse.BrowseLookup
import io.github.skm0078.audora.playback.browse.BrowseSources
import io.github.skm0078.audora.playback.browse.BrowseTree
import io.github.skm0078.audora.playback.browse.LibraryBrowser
import io.github.skm0078.audora.playback.browse.OutlineChapters
import io.github.skm0078.audora.playback.browse.ResourceBrowseTexts
import io.github.skm0078.audora.playback.contract.PendingIntentCodes
import io.github.skm0078.audora.playback.focus.FocusRenderersFactory
import io.github.skm0078.audora.playback.focus.FocusSoundCues
import io.github.skm0078.audora.playback.focus.FocusSoundProcessor
import io.github.skm0078.audora.playback.focus.FocusSoundStore
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
import io.github.skm0078.audora.playback.voice.BookRenderers
import io.github.skm0078.audora.playback.voice.CharacterCast
import io.github.skm0078.audora.playback.voice.CharacterFileStore
import io.github.skm0078.audora.playback.voice.RenderSpeedTracker
import io.github.skm0078.audora.playback.voice.SpeechStyleStore
import io.github.skm0078.audora.playback.voice.SpeechVoices
import io.github.skm0078.audora.playback.voice.StudyGapRenderer
import io.github.skm0078.audora.playback.voice.VoiceEngines
import io.github.skm0078.audora.playback.voice.narratorBlend
import io.github.skm0078.audora.speech.sherpa.blendableSpeaker
import io.github.skm0078.audora.speech.sherpa.castSpeakers
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first

/**
 * Synthesis and playback run here only (Build Spec C9). One player for the service's life; [BookSession]
 * swaps books and voices on it. Media3 keeps the service in the foreground (type mediaPlayback) while the
 * player plays or buffers with playWhenReady, which is exactly when a sentence is being synthesized (C5).
 *
 * Playback starts only from a user action (C7): PLAY_FROM from the UI, or play on the notification, lock
 * screen or a headset (playback resumption after Audora was closed, P1-06), or a book picked or asked for by voice
 * in Android Auto (plan item 34). Nothing starts it on boot.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class AudoraPlaybackService : MediaLibraryService() {
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
    private var session: MediaLibrarySession? = null

    // Where the voice speaks in each clip, for the reader's word highlight (plan item 14).
    private val timings = SentenceTimings()

    override fun onCreate() {
        super.onCreate() // Hilt injects fields here.
        val style = SpeechStyleStore.forContext(this)
        // Expressiveness (plan item 8) is global, like the rest of the voice shaping; a change reloads the voice.
        // The narrator blend (plan item 75) goes to the voice reading English now, nothing else.
        val engines = VoiceEngines(this, blend = { voices.narratorBlend(style.current.shaping) }) {
            style.current.shaping.expressiveness
        }
        voices = SpeechVoices(engines)
        val sleepOptions = SleepOptionsStore.forContext(this)
        val book = BookContext(bookSettings, pronunciations, scope) { bookSession.restartRendering() }
        voices.bookVoice = book::voice
        // What is heard: the global style with the open book's own speed and pace on top (plan item 4).
        val heard = { book.style(style.current) }
        val focusSound = FocusSoundStore.forContext(this)
        val focus = FocusSoundProcessor(focusSound::current) // In the voice's audio: pauses and ducks with it (72).
        player = speechPlayer(this, registry, config, focus)
        player.setPlaybackSpeed(style.config.playerSpeed(heard())) // ADR-033, 049: the voice speaks the rest.
        speed = LivePlaybackSpeed(player)
        // The reading modes travel with the speech style; a change restarts the sentence playing (ADR-035).
        val reading = ReadingSource(modes = { style.current.modes })
        val stores = SessionStores(style, sleepOptions, book, focusSound)
        val characters = characterCast(this, engines, { voices }, heard)
        val mediaSession = mediaSession(stores).also { session = it }
        bookSession = BookSession(
            player = player,
            registry = registry,
            sources = SessionSources(
                renderer = renderer(engines, stores, reading, heard, characters),
                books = LibraryBookSource(this, library),
                texts = ResourceTexts(),
                // The model's share of the speed and smart speed's drain the queue like the player's (ADR-033, 049).
                renderAhead = MeasuredRenderAhead(voices::speedOf) {
                    speed.value * style.config.modelRate(heard().rate) * style.config.voiceStretch(heard())
                },
                reading = reading,
                sleepOptions = sleepOptions::current,
                onBook = { id ->
                    book.open(id)
                    characters.open(id)
                    player.setPlaybackSpeed(style.config.playerSpeed(heard()))
                },
                keepGoingToNextChapter = { upNext.queue.value.keepGoingToNextChapter },
                onBookFinished = queueAdvance()::onBookFinished,
            ),
            config = config,
            events = publisher(mediaSession, stores, heard, engines).also { it.publishInitial() },
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

    private fun mediaSession(stores: SessionStores): MediaLibrarySession {
        // Android Auto and other media browsers (plan item 34): the library, Up next and each book's chapters.
        val chapters = OutlineChapters(this)
        val browse = BrowseSources(
            books = { library.books().first() },
            upNext = { upNext.queue.value },
            chapters = chapters::of,
            resume = { id -> bookSession.resume(id, locator) },
            playFrom = { id, page -> bookSession.playFrom(id, page, charStart = 0, password = null) },
        )
        val callback = SessionCallback(
            packageName,
            scope,
            voices,
            stores,
            { bookSession },
            { resumption },
            mediaButtons(),
            LibraryBrowser(BrowseLookup(BrowseTree(ResourceBrowseTexts(this)), browse), scope) {
                PlaybackResumption.playlistOf(player)
            },
        )
        return MediaLibrarySession.Builder(this, player, callback)
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
        characters: CharacterCast,
    ) = StudyGapRenderer(
        BookRenderers.create(
            this,
            voices,
            style = heard,
            styleConfig = stores.style.config,
            book = reading.book(fixer = { stores.book.fixer }, characters = characters),
            speakability = reading.speakability,
            speed = RenderSpeedTracker(engines::speeds),
            timings = timings,
        ),
        // Live playback only: "Leave a gap to say it back" (plan item 31).
        gaps = { heard().modes.afterSentence == AfterSentence.GAP },
    )

    private fun publisher(
        mediaSession: MediaSession,
        stores: SessionStores,
        heard: () -> SpeechStyle,
        engines: VoiceEngines,
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
            focusSound = stores.focusSound::current,
            blend = { blendAvailability(engines, voices) },
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

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
            PendingIntentCodes.OPEN_FROM_PLAYBACK,
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

/** The player, with the focus sound in its audio and told of seeks, stalls and the book's end. */
private fun speechPlayer(context: Context, registry: QueueRegistry, config: QueueConfig, focus: FocusSoundProcessor) =
    SpeechPlayer.build(context, registry, config, renderers = FocusRenderersFactory(context, focus))
        .also { FocusSoundCues.attach(it, focus) }

/**
 * What the speech sheet's narrator blend can do for the voice reading English now (plan item 75). Runs on the main
 * thread at every publish, so it never builds the engine: until the first sentence does, it reports nothing.
 */
private fun blendAvailability(engines: VoiceEngines, voices: SpeechVoices): BlendAvailability {
    val bundled = engines.bundledIfBuilt() ?: return BlendAvailability()
    val narrator = voices.knownVoice(Lang.EN)
    return BlendAvailability(narrator?.let(bundled.engine::blendableSpeaker), bundled.blends.neededBytes)
}

/** Character voices (plan item 63): other speakers of the narrator's model, never the quote or heading voice. */
private fun characterCast(
    context: Context,
    engines: VoiceEngines,
    voices: () -> SpeechVoices,
    heard: () -> SpeechStyle,
) = CharacterCast(
    narrator = { lang -> voices().knownVoice(lang) },
    speakers = { voice -> engines.bundled().getOrNull()?.engine?.castSpeakers(voice).orEmpty() },
    taken = { lang -> heard().shaping.let { setOfNotNull(it.dialogueVoices[lang], it.headingVoices[lang]) } },
    files = CharacterFileStore.forContext(context),
)
