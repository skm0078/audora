// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/service/AudoraPlaybackService.kt
// Purpose: The Media3 MediaLibraryService: foreground media service, notification, lock screen, headset, Android Auto.

package io.github.skm0078.audora.playback.service

import android.app.PendingIntent
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import dagger.hilt.android.AndroidEntryPoint
import io.github.skm0078.audora.core.config.QueueConfig
import io.github.skm0078.audora.core.diagnostics.ReadingSessionTracker
import io.github.skm0078.audora.core.home.BookGlances
import io.github.skm0078.audora.core.library.Library
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.AfterSentence
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.notes.BookSettingsStore
import io.github.skm0078.audora.core.notes.BookmarkStore
import io.github.skm0078.audora.core.notes.PronunciationStore
import io.github.skm0078.audora.core.resume.ResumeAnchorStore
import io.github.skm0078.audora.core.resume.ResumeLocator
import io.github.skm0078.audora.core.resume.ResumeRecorder
import io.github.skm0078.audora.core.stats.ListeningLog
import io.github.skm0078.audora.pdf.PIPELINE_VERSION
import io.github.skm0078.audora.playback.R
import io.github.skm0078.audora.playback.backup.Backups
import io.github.skm0078.audora.playback.book.LibraryBookSource
import io.github.skm0078.audora.playback.book.ReadingTextPreferences
import io.github.skm0078.audora.playback.brain.BrainNotesStore
import io.github.skm0078.audora.playback.brain.BrainSource
import io.github.skm0078.audora.playback.brain.StoryBrainReader
import io.github.skm0078.audora.playback.browse.BrowseLookup
import io.github.skm0078.audora.playback.browse.BrowseSources
import io.github.skm0078.audora.playback.browse.BrowseTree
import io.github.skm0078.audora.playback.browse.LibraryBrowser
import io.github.skm0078.audora.playback.browse.OutlineChapters
import io.github.skm0078.audora.playback.browse.ResourceBrowseTexts
import io.github.skm0078.audora.playback.contract.PendingIntentCodes
import io.github.skm0078.audora.playback.contract.SentenceTiming
import io.github.skm0078.audora.playback.covers.CoverFolders
import io.github.skm0078.audora.playback.focus.FocusLoops
import io.github.skm0078.audora.playback.focus.FocusRenderersFactory
import io.github.skm0078.audora.playback.focus.FocusSoundCues
import io.github.skm0078.audora.playback.focus.FocusSoundProcessor
import io.github.skm0078.audora.playback.focus.FocusSoundStore
import io.github.skm0078.audora.playback.notes.BookContext
import io.github.skm0078.audora.playback.queue.MeasuredRenderAhead
import io.github.skm0078.audora.playback.queue.SentenceRenderer
import io.github.skm0078.audora.playback.queue.UnitKey
import io.github.skm0078.audora.playback.reading.ReadingSource
import io.github.skm0078.audora.playback.session.BookSession
import io.github.skm0078.audora.playback.session.ItemTexts
import io.github.skm0078.audora.playback.session.LivePlaybackSpeed
import io.github.skm0078.audora.playback.session.QueueRegistry
import io.github.skm0078.audora.playback.session.SentenceTimings
import io.github.skm0078.audora.playback.session.SessionEnvironment
import io.github.skm0078.audora.playback.session.SessionSources
import io.github.skm0078.audora.playback.session.ShakeSource
import io.github.skm0078.audora.playback.session.SleepOptionsStore
import io.github.skm0078.audora.playback.session.SpeechPlayer
import io.github.skm0078.audora.playback.stats.ListeningBridge
import io.github.skm0078.audora.playback.studio.StudioClip
import io.github.skm0078.audora.playback.studio.StudioKey
import io.github.skm0078.audora.playback.studio.StudioMode
import io.github.skm0078.audora.playback.studio.StudioSentenceRenderer
import io.github.skm0078.audora.playback.upnext.QueueAdvance
import io.github.skm0078.audora.playback.upnext.UpNextStore
import io.github.skm0078.audora.playback.voice.BookRenderers
import io.github.skm0078.audora.playback.voice.CharacterCast
import io.github.skm0078.audora.playback.voice.RenderSpeedTracker
import io.github.skm0078.audora.playback.voice.ReplayRender
import io.github.skm0078.audora.playback.voice.SpeechStyleStore
import io.github.skm0078.audora.playback.voice.SpeechVoices
import io.github.skm0078.audora.playback.voice.StudyGapRenderer
import io.github.skm0078.audora.playback.voice.VoiceEngines
import io.github.skm0078.audora.playback.voice.forReading
import io.github.skm0078.audora.playback.voice.narratorBlend
import io.github.skm0078.audora.speech.brain.StoryBrainModels
import io.github.skm0078.audora.speech.brain.StoryBrainPrompt
import io.github.skm0078.audora.speech.sherpa.blendableSpeaker
import io.github.skm0078.audora.speech.sherpa.isExpressive
import io.github.skm0078.audora.speech.voices.VoiceLibrary
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
@Suppress("TooManyFunctions") // Service lifecycle callbacks plus one small setup step each.
class AudoraPlaybackService : MediaLibraryService() {
    @Inject
    lateinit var library: Library

    @Inject
    lateinit var tracker: ReadingSessionTracker

    @Inject
    lateinit var listeningLog: ListeningLog

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

    @Inject
    lateinit var bookGlances: BookGlances

    // A task that throws (a damaged file, a database error) is logged and the rest of the service keeps playing;
    // without the handler one such failure would end the process mid-book.
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate +
            CoroutineExceptionHandler { _, e -> AudoraLog.e(TAG, "Service task failed", e) },
    )

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
    private lateinit var controls: QuickControls
    private var session: MediaLibrarySession? = null
    private var brain: StoryBrainReader? = null

    // Where the voice speaks in each clip, for the reader's word highlight (plan item 14).
    private val timings = SentenceTimings()

    // The sentence "What did it say?" renders again slower, with smart speed on (ADR-049).
    private val replayRender = ReplayRender()

    /** Set when this process started with a restore half undone: no session, no stores (ADR-046). */
    private var restoring = false

    override fun onCreate() {
        super.onCreate() // Hilt injects fields here.
        // The tile, Android Auto and the system media controls bind the session; with none they get nothing.
        restoring = Backups.restoreUnfinishedAtStart.also { if (it) AudoraLog.w(TAG, "Restore unfinished: no session") }
        if (restoring) return
        val style = SpeechStyleStore.forContext(this)
        // Expressiveness (plan item 8) is global, like the rest of the voice shaping; a change reloads the voice.
        // The narrator blend (plan item 75) goes to the voice reading English now, nothing else.
        val engines = VoiceEngines(this, blend = { voices.narratorBlend(style.current.shaping) }) {
            style.current.shaping.expressiveness
        }
        voices = SpeechVoices(engines)
        val sleepOptions = SleepOptionsStore.forContext(this)
        val book = BookContext(bookSettings, pronunciations, scope, style.config::clampPitch, ::onBookRulesChanged)
        voices.bookVoice = book::voice
        // What is heard: the global style with the open book's own speed and pace on top (plan item 4).
        val heard = { book.style(style.current) }
        val focusSound = FocusSoundStore.forContext(this)
        // In the voice's audio: pauses and ducks with it (72); recorded loops from the assets (Q-1).
        val focus = FocusSoundProcessor(focusSound::current, beds = FocusLoops(assets))
        player = speechPlayer(this, registry, config, focus)
        // ADR-033, 049: the voice speaks the rest of the speed. The pitch is the player's alone (ADR-067).
        player.playbackParameters = PlaybackParameters(style.config.playerSpeed(heard()), heard().pitch)
        speed = LivePlaybackSpeed(player)
        // The reading modes travel with the speech style; a change restarts the sentence playing (ADR-035).
        val reading = ReadingSource(modes = { style.current.modes }, brain = storyBrain(heard).also { brain = it })
        val stores = SessionStores(style, sleepOptions, book, focusSound)
        val characters = CharacterCast.forReading(this, engines, { voices }) { heard().shaping }
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
                    // The book's own pitch too (ADR-068), so a book opened after another is heard at its own.
                    player.playbackParameters = PlaybackParameters(style.config.playerSpeed(heard()), heard().pitch)
                },
                keepGoingToNextChapter = { upNext.queue.value.keepGoingToNextChapter },
                // The cover the library drew, on the notification, lock screen and car screen.
                artwork = { opened -> CoverFolders(filesDir).shownFile(opened)?.let(CoverFolders::artworkUri) },
                onBookFinished = queueAdvance()::onBookFinished,
                replayRender = replayRender,
                replayStyle = { factor -> style.config.replayStyle(heard(), factor) },
            ),
            config = config,
            events = publisher(mediaSession, stores, heard, engines, reading).also { it.publishInitial() },
            env = sessionEnvironment(scope, AccelerometerShakes(this, config.sleep)),
        )
        followReadingChanges()
        resumption = PlaybackResumption(
            anchors = resumeAnchors,
            library = library,
            locator = locator,
            scope = scope,
            player = player,
        ) { bookSession }
        AudoraLog.i(TAG, "Service created")
    }

    /**
     * The listener changed the open book's language in Edit details: from the sentence playing on, it is read by the
     * phone's voice for that language, or by the three languages' own (ADR 2026-10-26), at once (ADR-064). The
     * session compares it with the language it reads in, so the library's update when a book opens restarts nothing.
     */
    private fun followBookLanguage() {
        scope.launch {
            library.books().collect { books ->
                val id = bookSession.bookId ?: return@collect
                val book = books.firstOrNull { it.id == id } ?: return@collect
                bookSession.onBookLanguage(id, book.language)
            }
        }
    }

    /**
     * A voice picked in Settings → Voices or the reader, or a language pack's first voice, reads from the sentence
     * playing now: the sentences rendered ahead in the old voice are redone, playing or paused. Without this the old
     * voice went on for every sentence already rendered, until some other setting happened to restart reading.
     * Likewise a reading text setting (skip citation numbers) reads the open book again at once (ADR-064), and so
     * does a new language for the open book ([followBookLanguage]).
     */
    private fun followReadingChanges() {
        val context = applicationContext
        scope.launch {
            // Read first (off the main thread), so the first value seen is the saved state and only changes follow.
            val reading = withContext(Dispatchers.IO) { VoiceLibrary.get(context).readingVoices.also { it.current() } }
            var before = reading.choices.value.orEmpty()
            reading.choices.filterNotNull().distinctUntilChanged().drop(1).collect { now ->
                AudoraLog.i(TAG, "Reading voices changed; restarting the sentence with the new voice")
                bookSession.restartRendering()
                sayIfBookKeepsItsVoice(Lang.entries.filter { before[it] != now[it] })
                before = now
            }
        }
        scope.launch {
            ReadingTextPreferences.changes.drop(1).collect {
                AudoraLog.i(TAG, "Reading text settings changed; reading the open book again")
                bookSession.onReadingTextChanged()
            }
        }
        followBookLanguage()
    }

    /**
     * A voice picked in Settings → Voices for a language the open book reads with its own voice changes nothing
     * audible: say so at once instead of leaving the listener to think the setting is broken (ADR-064).
     */
    private fun sayIfBookKeepsItsVoice(changed: List<Lang>) {
        if (bookSession.bookId == null || changed.none(voices::bookOverrides)) return
        AudoraLog.i(TAG, "Voice picked for ${changed.map { it.name }}, but the open book keeps its own voice")
        Toast.makeText(this, R.string.voice_book_keeps_own, Toast.LENGTH_LONG).show()
    }

    private fun mediaSession(stores: SessionStores): MediaLibrarySession {
        // The lock screen's buttons, headphones coming back and the widget's time listened (CF-1, CF-4, CF-7).
        controls = QuickControls(this, player, { bookSession }, bookmarks, scope, PIPELINE_VERSION, config.sleep.buzzMs)
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
            controls.lockScreen,
        )
        // The notification's and lock screen's buttons: a sentence back, play/pause, a sentence on, bookmark (CF-1).
        return MediaLibrarySession.Builder(this, player, callback)
            .setMediaButtonPreferences(controls.initialButtons)
            .apply { sessionActivity()?.let { setSessionActivity(it) } }
            .build()
            .also(controls::attach)
    }

    /** A headset's play/pause button (plan item 20); a double press bookmarks and buzzes to confirm. */
    private fun mediaButtons() = MediaButtons(
        player = { player },
        bookSession = { bookSession },
        bookmarks = controls.headsetBookmarks,
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

    /** The live voice, with studio audio in front of it: a sentence rendered ahead plays at once (ADR-039). */
    private fun renderer(
        engines: VoiceEngines,
        stores: SessionStores,
        reading: ReadingSource,
        heard: () -> SpeechStyle,
        characters: CharacterCast,
    ): SentenceRenderer {
        val live = BookRenderers.create(
            this,
            voices,
            style = heard,
            styleConfig = stores.style.config,
            book = reading.book(fixer = { stores.book.fixer }, characters = characters),
            speakability = reading.speakability,
            speed = RenderSpeedTracker(engines::speeds),
            timings = timings,
            replay = replayRender,
        )
        val book = stores.book
        val played = { unit: ReadingUnit, clip: StudioClip ->
            timings.record(
                UnitKey.of(unit),
                SentenceTiming(unit.page, unit.charStart, unit.index, clip.leadMs, clip.speechMs, clip.durationMs),
            )
        }
        val studio = StudioSentenceRenderer(live, StudioMode.store(this), replay = replayRender, played = played) {
            book.bookId?.let { id ->
                // Studio mode reads Hindi with the acted voice once installed (ADR-059): look its audio up under it.
                val studioVoice = { lang: Lang -> StudioMode.actedVoice(this, lang) ?: voices.choiceFor(lang) }
                val signature = StudioMode.signature(this, heard(), studioVoice, book.fixer.signature, reading.language)
                StudioKey(id, signature)
            }
        }
        // Outermost, so studio audio still leaves the "Leave a gap to say it back" pause (plan item 31).
        return StudyGapRenderer(studio, gaps = { heard().modes.afterSentence == AfterSentence.GAP })
    }

    private fun publisher(
        mediaSession: MediaSession,
        stores: SessionStores,
        heard: () -> SpeechStyle,
        engines: VoiceEngines,
        reading: ReadingSource,
    ): SessionPublisher {
        val recorder = ResumeRecorder(resumeAnchors, resumeScope, PIPELINE_VERSION, System::currentTimeMillis)
        return SessionPublisher(
            mediaSession,
            DiagnosticsBridge(tracker, scope),
            ResumeBridge(recorder, resumeScope, bookGlances),
            heard,
            stores.sleepOptions::current,
            bookSettings = { stores.book.settings },
            timings = timings,
            focusSound = stores.focusSound::current,
            blend = { blendAvailability(engines, voices) },
            // The speech sheet says what the book reads as (ADR-056), only while that shapes the reading.
            intent = { reading.intent.takeIf { it.told && heard().shaping.suitBook }?.current },
            listening = ListeningBridge(listeningLog, scope),
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A headset's play starts this as a foreground service: Android requires startForeground before stopping.
        if (restoring) RestoreRefusal.refuse(this)
        val sticky = super.onStartCommand(intent, flags, startId) // With no session it finds nothing to play.
        return if (restoring) START_NOT_STICKY else sticky
    }

    /**
     * Gives back the neural models not in use (P4-02). Since API 34 Android delivers only UI_HIDDEN and BACKGROUND
     * (the RUNNING_* levels are deprecated and no longer sent). UI_HIDDEN just means the user left the app, which
     * is how people listen, so it keeps both models; BACKGROUND and above (MODERATE and COMPLETE on older releases)
     * mean the process is on the kill list, where a second ~150 MB model is the first thing to give back.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (restoring) return
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            AudoraLog.i(TAG, "Trim memory level=$level, book=${bookSession.bookId?.logLabel}")
            voices.trimMemory()
        }
    }

    /** The open book's fixes changed while it plays: what was rendered ahead is rendered again. */
    private fun onBookRulesChanged() = bookSession.restartRendering()

    override fun onDestroy() {
        if (restoring) {
            super.onDestroy()
            return
        }
        AudoraLog.i(TAG, "Service destroyed, book=${bookSession.bookId?.logLabel}")
        controls.release()
        bookSession.release()
        speed.release()
        session?.release()
        session = null
        player.release()
        voices.release()
        brain?.close()
        scope.cancel()
        super.onDestroy()
    }

    /**
     * The opt-in story brain (ADR story-brain). Built on every phone but costs nothing without its model: whether the
     * phone can run it and has it is asked on the brain's own thread, the first time a page has speech on it.
     */
    private fun storyBrain(heard: () -> SpeechStyle): StoryBrainReader {
        val models = StoryBrainModels.get(this)
        return StoryBrainReader(
            config = models.config,
            source = BrainSource(load = models::load, installed = { models.supported && models.isInstalled() }),
            store = BrainNotesStore(File(filesDir, BRAIN_NOTES_DIR), "${models.model.id}/${StoryBrainPrompt.VERSION}"),
            wanted = { heard().shaping.let { it.characterVoices || it.suitBook } },
        )
    }

    private inner class ResourceTexts : ItemTexts {
        override fun pageOf(page: Int, pageCount: Int): String = getString(R.string.playback_page_of, page, pageCount)

        override fun chapterOf(chapter: Int, chapterCount: Int): String =
            getString(R.string.playback_chapter_of, chapter, chapterCount)

        override fun loadingNextPage(): String = getString(R.string.playback_loading_next_page)
    }

    private companion object {
        const val TAG = "Playback.Service"
        const val BRAIN_NOTES_DIR = "story-brain-notes"
    }
}

/** Tapping the notification opens the app; :playback does not know its activity, the launcher does. */
private fun Context.sessionActivity(): PendingIntent? {
    val launch: Intent = packageManager.getLaunchIntentForPackage(packageName) ?: return null
    return PendingIntent.getActivity(
        this,
        PendingIntentCodes.OPEN_FROM_PLAYBACK,
        launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
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
    val known = Lang.entries.mapNotNull(voices::knownVoice)
    val expressive = known.takeIf { it.isNotEmpty() }
        ?.any { it.engine == EngineId.SHERPA && bundled.engine.isExpressive(it) }
    return BlendAvailability(narrator?.let(bundled.engine::blendableSpeaker), bundled.blends.neededBytes, expressive)
}

/** The session's threads and clocks, on the service's [scope]. */
private fun sessionEnvironment(scope: CoroutineScope, shakes: ShakeSource) = SessionEnvironment(
    scope = scope,
    extraction = Dispatchers.IO.limitedParallelism(1),
    elapsedRealtime = SystemClock::elapsedRealtime,
    wallClock = System::currentTimeMillis,
    shakes = shakes,
)
