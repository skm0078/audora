// File: app/src/main/kotlin/io/github/skm0078/audora/MainActivity.kt
// Purpose: Single activity hosting the Compose UI, edge-to-edge from day one (Build Spec §4), and its routes.

package io.github.skm0078.audora

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.github.skm0078.audora.battery.BatteryExemption
import io.github.skm0078.audora.battery.OnboardingPreferences
import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.core.diagnostics.ReadingSessionTracker
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.Library
import io.github.skm0078.audora.core.library.LibraryQuery
import io.github.skm0078.audora.core.model.FocusSoundOptions
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.Pace
import io.github.skm0078.audora.core.model.ReadingModes
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.SleepOptions
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.model.VoiceShaping
import io.github.skm0078.audora.core.notes.Bookmark
import io.github.skm0078.audora.core.notes.BookmarkStore
import io.github.skm0078.audora.core.notes.HighlightStore
import io.github.skm0078.audora.core.notes.PronunciationStore
import io.github.skm0078.audora.core.notes.VoiceNoteStore
import io.github.skm0078.audora.core.resume.ResumeAnchorStore
import io.github.skm0078.audora.core.resume.ResumeLocator
import io.github.skm0078.audora.diagnostics.LogSharer
import io.github.skm0078.audora.language.AppLanguage
import io.github.skm0078.audora.language.AppLanguageStore
import io.github.skm0078.audora.language.AppLanguages
import io.github.skm0078.audora.pdf.PIPELINE_VERSION
import io.github.skm0078.audora.pdf.outline.ChapterEntry
import io.github.skm0078.audora.pdf.render.ViewPoint
import io.github.skm0078.audora.playback.backup.Backups
import io.github.skm0078.audora.playback.book.ReadingTextPreferences
import io.github.skm0078.audora.playback.contract.StudyAction
import io.github.skm0078.audora.playback.documents.SharedPdfCopies
import io.github.skm0078.audora.playback.documents.WatchedFolders
import io.github.skm0078.audora.playback.export.AudioClips
import io.github.skm0078.audora.playback.export.AudiobookExport
import io.github.skm0078.audora.playback.export.MarkStores
import io.github.skm0078.audora.playback.jobs.LongJobKind
import io.github.skm0078.audora.playback.notes.VoiceNoteAudio
import io.github.skm0078.audora.playback.photo.PhotoBooks
import io.github.skm0078.audora.playback.report.SoundedWrongReason
import io.github.skm0078.audora.playback.search.SearchIndexes
import io.github.skm0078.audora.speech.kws.VoiceCommandModels
import io.github.skm0078.audora.speech.ocr.OcrModels
import io.github.skm0078.audora.speech.ocr.OcrModelsState
import io.github.skm0078.audora.speech.punct.PunctuationModels
import io.github.skm0078.audora.speech.voices.VoiceRow
import io.github.skm0078.audora.ui.AppDestination
import io.github.skm0078.audora.ui.AppTab
import io.github.skm0078.audora.ui.battery.BatterySetupCard
import io.github.skm0078.audora.ui.battery.BatterySetupController
import io.github.skm0078.audora.ui.battery.BatterySetupScreen
import io.github.skm0078.audora.ui.diagnostics.DiagnosticsController
import io.github.skm0078.audora.ui.diagnostics.UnfinishedSessionDialog
import io.github.skm0078.audora.ui.firstrun.FirstRunScreen
import io.github.skm0078.audora.ui.firstrun.shouldShowFirstRun
import io.github.skm0078.audora.ui.jobs.LongJobsPanel
import io.github.skm0078.audora.ui.jobs.startLongJob
import io.github.skm0078.audora.ui.kids.KidsBanner
import io.github.skm0078.audora.ui.kids.KidsMode
import io.github.skm0078.audora.ui.kids.KidsModePreferences
import io.github.skm0078.audora.ui.kids.LocalKidsMode
import io.github.skm0078.audora.ui.kids.kidsDestination
import io.github.skm0078.audora.ui.kids.kidsSpeed
import io.github.skm0078.audora.ui.library.BookAction
import io.github.skm0078.audora.ui.library.BookCovers
import io.github.skm0078.audora.ui.library.LibraryAction
import io.github.skm0078.audora.ui.library.LibraryActions
import io.github.skm0078.audora.ui.library.LibraryController
import io.github.skm0078.audora.ui.library.LibraryListState
import io.github.skm0078.audora.ui.library.LibraryScreen
import io.github.skm0078.audora.ui.library.MiniPlayer
import io.github.skm0078.audora.ui.library.PDF_MIME_TYPES
import io.github.skm0078.audora.ui.library.nowPlaying
import io.github.skm0078.audora.ui.library.restoreMessageOf
import io.github.skm0078.audora.ui.library.showFinishRestoringIfNeeded
import io.github.skm0078.audora.ui.licences.LicenceSource
import io.github.skm0078.audora.ui.licences.LicencesController
import io.github.skm0078.audora.ui.licences.LicencesScreen
import io.github.skm0078.audora.ui.loggingFailures
import io.github.skm0078.audora.ui.motion.AnimatedScreens
import io.github.skm0078.audora.ui.motion.isLeavingScreen
import io.github.skm0078.audora.ui.notes.NotesScreen
import io.github.skm0078.audora.ui.notes.bookNotes
import io.github.skm0078.audora.ui.reader.BookNotesModel
import io.github.skm0078.audora.ui.reader.DocumentState
import io.github.skm0078.audora.ui.reader.KeepScreenOn
import io.github.skm0078.audora.ui.reader.LibraryReaderBooks
import io.github.skm0078.audora.ui.reader.LocatorReaderResume
import io.github.skm0078.audora.ui.reader.MediaControllerPlaybackPort
import io.github.skm0078.audora.ui.reader.NoteStores
import io.github.skm0078.audora.ui.reader.NotesState
import io.github.skm0078.audora.ui.reader.PREVIEW_WIDTH
import io.github.skm0078.audora.ui.reader.PageThumbnails
import io.github.skm0078.audora.ui.reader.ParagraphSkipNotice
import io.github.skm0078.audora.ui.reader.PlaybackPort
import io.github.skm0078.audora.ui.reader.PlayerConnectionEffect
import io.github.skm0078.audora.ui.reader.PlayerScreen
import io.github.skm0078.audora.ui.reader.ReaderActions
import io.github.skm0078.audora.ui.reader.ReaderDocumentOpener
import io.github.skm0078.audora.ui.reader.ReaderModel
import io.github.skm0078.audora.ui.reader.ReaderScreen
import io.github.skm0078.audora.ui.reader.ReaderState
import io.github.skm0078.audora.ui.reader.ReaderView
import io.github.skm0078.audora.ui.reader.ReaderVoiceCommands
import io.github.skm0078.audora.ui.reader.ReadingLook
import io.github.skm0078.audora.ui.reader.ReadingLookPreferences
import io.github.skm0078.audora.ui.reader.ScreenPreference
import io.github.skm0078.audora.ui.reader.SleepTimerSetting
import io.github.skm0078.audora.ui.reader.SpeedPreference
import io.github.skm0078.audora.ui.reader.ViewPreference
import io.github.skm0078.audora.ui.reader.VolumeKeySkips
import io.github.skm0078.audora.ui.reader.rememberPlayWithNotificationPermission
import io.github.skm0078.audora.ui.reader.rememberSpokenWord
import io.github.skm0078.audora.ui.settings.SettingsScreen
import io.github.skm0078.audora.ui.settings.rememberPronunciationSettings
import io.github.skm0078.audora.ui.settings.rememberVoiceCommandsSettings
import io.github.skm0078.audora.ui.settings.runModelAction
import io.github.skm0078.audora.ui.shell.AppShell
import io.github.skm0078.audora.ui.shell.ReadableWidth
import io.github.skm0078.audora.ui.storage.StorageRoute
import io.github.skm0078.audora.ui.theme.Appearance
import io.github.skm0078.audora.ui.theme.AppearancePreferences
import io.github.skm0078.audora.ui.theme.AudoraTheme
import io.github.skm0078.audora.ui.theme.Dimens
import io.github.skm0078.audora.ui.tutor.ReaderTutorRoute
import io.github.skm0078.audora.ui.upnext.UpNextPort
import io.github.skm0078.audora.ui.upnext.UpNextSheet
import io.github.skm0078.audora.ui.upnext.libraryBooks
import io.github.skm0078.audora.ui.voices.VoiceManager
import io.github.skm0078.audora.ui.voices.VoicesActions
import io.github.skm0078.audora.ui.voices.VoicesController
import io.github.skm0078.audora.ui.voices.VoicesScreen
import io.github.skm0078.audora.widget.ResumeWidgetEffect
import io.github.skm0078.audora.widget.ResumeWidgetProvider
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
@Suppress("TooManyFunctions") // One route per screen.
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var readingSessionTracker: ReadingSessionTracker

    @Inject
    lateinit var logSharer: LogSharer

    @Inject
    lateinit var library: Library

    @Inject
    lateinit var bookCovers: BookCovers

    @Inject
    lateinit var readerConfig: ReaderConfig

    @Inject
    lateinit var speedPreference: SpeedPreference

    @Inject
    lateinit var viewPreference: ViewPreference

    @Inject
    lateinit var screenPreference: ScreenPreference

    @Inject
    lateinit var documentOpener: ReaderDocumentOpener

    @Inject
    lateinit var resumeLocator: ResumeLocator

    @Inject
    lateinit var resumeAnchors: ResumeAnchorStore

    @Inject
    lateinit var bookmarkStore: BookmarkStore

    @Inject
    lateinit var pronunciationStore: PronunciationStore

    @Inject
    lateinit var highlightStore: HighlightStore

    @Inject
    lateinit var voiceNoteStore: VoiceNoteStore

    /** Audora's own copies of PDFs another app only lends for a moment ("Open with Audora", ADR-033). */
    private val sharedCopies: SharedPdfCopies by lazy { SharedPdfCopies(applicationContext) }

    /** A PDF opened or shared from another app, waiting to be added. Never set on a restore: it was handled then. */
    private var sharedPdf by mutableStateOf<String?>(null)

    /** A book the home-screen widget's Resume asked to continue (plan item 35), until it is opened. */
    private var widgetResume by mutableStateOf<String?>(null)

    /** Text shared from another app (plan item 42), until it is saved as a book. */
    private var sharedText by mutableStateOf<SharedText?>(null)

    @Inject
    lateinit var voiceManager: VoiceManager

    /** The OCR model manager (P3-05); one per process, shared with the playback service's OCR. */
    private val ocrModels: OcrModels by lazy { OcrModels.get(applicationContext) }
    private val punctuationModels: PunctuationModels by lazy { PunctuationModels.get(applicationContext) }
    private val voiceCommandModels: VoiceCommandModels by lazy { VoiceCommandModels.get(applicationContext) }

    @Inject
    lateinit var licenceSource: LicenceSource

    @Inject
    lateinit var upNextLazy: dagger.Lazy<UpNextPort>

    // Lazy: StoredUpNext reads Room and rewrites files/up-next.txt (a backup unit) when built, which must not happen
    // before onCreate's "Finish restoring" check (ADR-046 §6-7).
    private val upNext: UpNextPort get() = upNextLazy.get()

    @Inject
    lateinit var backups: Backups

    @Inject
    lateinit var photoBooks: PhotoBooks

    private val appearancePreferences: AppearancePreferences by lazy { AppearancePreferences(applicationContext) }

    private val readingLookPreferences: ReadingLookPreferences by lazy { ReadingLookPreferences(applicationContext) }

    private val kidsModePreferences: KidsModePreferences by lazy { KidsModePreferences(applicationContext) }

    // Built from the base context in attachBaseContext, before applicationContext can be relied on.
    private lateinit var appLanguageStore: AppLanguageStore

    override fun attachBaseContext(newBase: Context) {
        appLanguageStore = AppLanguageStore(newBase)
        super.attachBaseContext(AppLanguages.wrap(newBase, appLanguageStore.load()))
    }

    private val searchIndexes: SearchIndexes by lazy { SearchIndexes(applicationContext) }

    private val voiceNoteParts by lazy {
        VoiceNoteParts(voiceNoteStore, VoiceNoteAudio(applicationContext), lifecycleScope)
    }

    private val studioParts by lazy {
        val notes = MarkStores(highlightStore, bookmarkStore, voiceNoteStore)
        val watched = WatchedFolders(applicationContext, library)
        StudioLibraryParts(this, photoBooks, watched, backups, resumeAnchors, notes)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Nothing that opens the library, prunes or resumes runs until a half-undone restore is finished (ADR-046).
        if (showFinishRestoringIfNeeded()) return
        if (savedInstanceState == null) {
            sharedPdf = SharedPdfIntent.uriOf(intent)
            sharedText = SharedPdfIntent.textOf(intent)
            widgetResume = ResumeWidgetProvider.bookIdOf(intent)
            continueStudioJobs(intent)
            Backups.takeStartResult()?.let { Toast.makeText(this, restoreMessageOf(it), Toast.LENGTH_LONG).show() }
            photoBooks.resumePending()
        }
        setContent {
            val appearance by remember {
                appearancePreferences.appearance
            }.collectAsStateWithLifecycle(initialValue = Appearance())
            val dark = appearance.isDark(isSystemInDarkTheme())
            SystemBarsFollow(dark)
            AudoraTheme(appearance, darkTheme = dark) { AppContent(appearance) }
        }
    }

    /**
     * The status and navigation bar icons follow the app's own Light or Dark choice, not the phone's night mode, so
     * Dark on a light phone does not leave dark icons on a dark app. The scrims are edge-to-edge's defaults.
     */
    @Composable
    private fun SystemBarsFollow(dark: Boolean) {
        DisposableEffect(dark) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                navigationBarStyle = SystemBarStyle.auto(LIGHT_NAV_SCRIM, DARK_NAV_SCRIM) { dark },
            )
            onDispose {}
        }
    }

    /** The screens and the routing between them (ADR-036: three tabs, the reader and the player over them). */
    @Composable
    private fun AppContent(appearance: Appearance) {
        val nav = rememberAppNavigation()
        val kids by remember { kidsModePreferences.mode }.collectAsStateWithLifecycle(initialValue = KidsMode())
        // Turning kids mode on moves a grown-up screen (Settings, the page view) to the child's form of it.
        LaunchedEffect(kids.on) {
            nav.kids = kids.on
            nav.navigate(nav.destination)
        }
        CompositionLocalProvider(LocalKidsMode provides kids.on) {
            // Edge to edge is enforced from API 35, so a camera cutout at the side of a phone in landscape would
            // cover a screen's start or end. Every screen stays clear of it here, once, and still draws under the
            // status and navigation bars, which each screen pads for itself.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
            ) { AppScreens(nav, kids, appearance) }
        }
    }

    /** The tabs and the screens over them; kids mode only changes what they draw and where they lead. */
    @Composable
    private fun AppScreens(nav: AppNavigation, kids: KidsMode, appearance: Appearance) {
        val scope = rememberCoroutineScope()
        val diagnostics = remember { DiagnosticsController(readingSessionTracker, logSharer, scope) }
        // Activity-wide, so an add still running when the user opens Settings is not cancelled.
        val libraryController = remember {
            LibraryController(library, scope, resumeAnchors.observeAll(), sharedCopies::copy).apply { start() }
        }
        val battery = rememberBatterySetup(scope)
        // Activity-wide too, so an import keeps going when the user leaves the screen; created on first visit.
        val voicesController = remember { lazy { VoicesController(voiceManager, scope).also { it.start() } } }
        val destination = nav.destination
        // Above the screens, so the mini player is there from the first frame of a back swipe to the tabs and the
        // cover has somewhere to shrink back into.
        val miniPort = rememberConnectedPlaybackPort()
        LaunchedEffect(Unit) { diagnostics.checkUnfinishedSession() }
        LaunchedEffect(Unit) { dropStaleBookFiles(applicationContext, library, searchIndexes, voiceNoteParts) }
        SharedPdfEffect(sharedPdf, { sharedPdf = null }, libraryController::addShared, nav::navigate)
        SharedTextEffect(sharedText, sharedCopies, onTaken = { sharedText = null }) { sharedPdf = it }
        PhotoBookEffect(photoBooks, nav::navigate)
        ResumeWidgetEffect(libraryController.listState, widgetResume, { widgetResume = null }, nav::continueReading)
        val books = libraryBooks(libraryController.listState)
        if (firstRunShowing(libraryController, voicesController, battery, kids.on)) return

        // Back, with its preview, goes through AnimatedScreens; screens' own sheets and menus register after it.
        AnimatedScreens(destination, onBack = nav::navigate) { screen ->
            val tab = screen.tab
            if (tab == null) {
                ScreenRoute(screen, nav, voicesController, battery, books)
            } else {
                TabsRoute(tab, libraryController, miniPort, nav::navigate) {
                    when (tab) {
                        AppTab.LIBRARY -> LibraryRoute(libraryController, nav::navigate, nav::continueReading) {
                            if (kids.on) {
                                KidsBanner(onLeave = { scope.launch { turnOffKidsMode(kids) } })
                            } else if (battery.showCard) {
                                BatterySetupCard(
                                    onSetUp = { nav.navigate(AppDestination.BatterySetup(fromSettings = false)) },
                                    onNotNow = battery::dismissCard,
                                )
                            }
                        }

                        AppTab.NOTES -> ReadableWidth { NotesRoute(books, nav::openBookmark) }

                        AppTab.SETTINGS -> ReadableWidth {
                            SettingsRoute(diagnostics, battery, nav::navigate, appearance) {
                                scope.launch { turnOnKidsMode() }
                            }
                        }
                    }
                }
            }
        }
        UnfinishedSessionPrompt(diagnostics, nav::continueReading)
    }

    /**
     * The first-run screens over everything, once, for someone with an empty library (UI plan, wave 4); true while
     * they show, and while it is not yet known whether they will, so the tabs never flash first. Decided once, from
     * the saved flag (read off the main thread) and the library's first settled state: a PDF opened with Audora on
     * the very first launch still gets the voice and background steps. Someone updating with books already gets them
     * marked done, unseen. Kids mode never shows them.
     */
    @Composable
    private fun firstRunShowing(
        library: LibraryController,
        voices: Lazy<VoicesController>,
        battery: BatterySetupController,
        kids: Boolean,
    ): Boolean {
        val onboarding = remember { OnboardingPreferences(applicationContext) }
        // Saved, so a rotation keeps the decision instead of taking it again from a library that has since changed.
        var show by rememberSaveable { mutableStateOf<Boolean?>(null) }
        val finish = {
            show = false
            onboarding.firstRunDone = true // an apply(): written in the background
        }
        LaunchedEffect(Unit) {
            if (show != null) return@LaunchedEffect
            val done = withContext(Dispatchers.IO) { onboarding.firstRunDone }
            val settled = snapshotFlow { library.listState }.first { shouldShowFirstRun(done, it) != null }
            val decided = shouldShowFirstRun(done, settled) == true
            if (!done && !decided && settled is LibraryListState.Loaded) finish()
            show = decided
        }
        // Registered here, not in the screens: they close before the picker returns, and the result must still land.
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { library.add(it.toString()) }
        }
        if (kids) return false
        if (show == true) {
            val voicesState by voices.value.state.collectAsStateWithLifecycle()
            val voiceActions = remember { voicesActions(voices.value) {} }
            ReadableWidth {
                FirstRunScreen(
                    voices = voicesState,
                    voiceActions = voiceActions,
                    backgroundAllowed = battery.exempt,
                    onAllowBackground = { battery.requestExemption(::startActivity) },
                    onAddPdf = { picker.launch(PDF_MIME_TYPES) },
                    onFinish = finish,
                )
            }
        }
        return show != false
    }

    /** The screens drawn over the tabs: sub-pages of Settings, the reader and the full player. */
    @Composable
    private fun ScreenRoute(
        destination: AppDestination,
        nav: AppNavigation,
        voicesController: Lazy<VoicesController>,
        battery: BatterySetupController,
        books: List<Book>,
    ) {
        when (destination) {
            AppDestination.Library, AppDestination.Notes, AppDestination.Settings -> Unit

            is AppDestination.Voices -> ReadableWidth {
                VoicesRoute(voicesController.value) { nav.navigate(destination.parent ?: AppDestination.Settings) }
            }

            AppDestination.Licences -> ReadableWidth { LicencesRoute { nav.navigate(AppDestination.Settings) } }

            AppDestination.Storage -> ReadableWidth {
                StorageRoute(
                    voices = remember { voiceManager.rows() },
                    owners = remember { storageOwners(applicationContext, bookCovers, sharedCopies, searchIndexes) },
                    onOpenVoices = { nav.navigate(AppDestination.Voices(fromStorage = true)) },
                    onBack = { nav.navigate(AppDestination.Settings) },
                )
            }

            is AppDestination.BatterySetup -> ReadableWidth {
                BatterySetupRoute(battery) { nav.navigate(destination.parent ?: AppDestination.Library) }
            }

            is AppDestination.Reader -> BookRoute(destination, destination.id, destination.from, nav, books)

            is AppDestination.Player -> BookRoute(destination, destination.id, destination.from, nav, books)
        }
    }

    /**
     * The reader and the full player of one book from one call site, so switching between them keeps the same
     * ReaderModel and open PDF (ADR-036 §6: one model). Kids mode opens books in the player, so "Continue" reads on
     * from either.
     */
    @Composable
    private fun BookRoute(
        destination: AppDestination,
        id: BookId,
        from: AppTab,
        nav: AppNavigation,
        books: List<Book>,
    ) {
        val asPlayer = destination is AppDestination.Player
        key(id) {
            ReaderRoute(
                ReaderTarget(id, autoResume = nav.autoResumeBook == id, asPlayer = asPlayer),
                books,
                onBack = { nav.navigate(destination.parent ?: AppDestination.Library) },
                onShowPage = { nav.navigate(AppDestination.Reader(id, from, viaPlayer = true)) },
                openBookmark = nav.pendingBookmark?.takeIf { it.bookId == id && !asPlayer },
                onBookmarkOpened = { nav.pendingBookmark = null },
                onOtherBookPlaying = { other -> nav.navigate(AppDestination.Player(other, from)) },
                // Taken once, so opening the book again later never starts reading by itself.
                onAutoResumeTaken = { nav.autoResumeBook = null },
            )
        }
    }

    /** The tabs' frame with the mini player, which follows whatever the player holds (ADR-036). */
    @Composable
    private fun TabsRoute(
        tab: AppTab,
        library: LibraryController,
        port: MediaControllerPlaybackPort,
        navigate: (AppDestination) -> Unit,
        content: @Composable () -> Unit,
    ) {
        val playback by port.state.collectAsStateWithLifecycle()
        val playWithPermission = rememberPlayWithNotificationPermission()
        val playing = nowPlaying(playback, library.listState)
        AppShell(
            selected = tab,
            onSelect = { navigate(it.destination) },
            tabs = if (LocalKidsMode.current) listOf(AppTab.LIBRARY) else AppTab.entries,
            miniPlayer = {
                playing?.let { now ->
                    MiniPlayer(
                        playing = now,
                        onOpen = { navigate(AppDestination.Player(now.bookId, tab)) },
                        onPlayPause = {
                            if (port.state.value.wantsToPlay) port.pause() else playWithPermission(port::play)
                        },
                        covers = bookCovers,
                    )
                }
            },
            content = content,
        )
    }

    @Composable
    private fun NotesRoute(books: List<Book>, onOpen: (Bookmark) -> Unit) {
        val groups by remember(books) { bookNotes(bookmarkStore, books) }
            .collectAsStateWithLifecycle(initialValue = emptyList())
        NotesScreen(groups, onOpen)
    }

    /** Re-checks the exemption on every resume: the user comes back from the system prompt or Settings. */
    @Composable
    private fun rememberBatterySetup(scope: CoroutineScope): BatterySetupController {
        val battery = remember {
            BatterySetupController(
                BatteryExemption(applicationContext),
                OnboardingPreferences(applicationContext),
                scope,
            )
        }
        LifecycleResumeEffect(Unit) {
            battery.refresh()
            onPauseOrDispose {}
        }
        return battery
    }

    @Composable
    private fun BatterySetupRoute(battery: BatterySetupController, navigateBack: () -> Unit) {
        BatterySetupScreen(
            exempt = battery.exempt,
            brand = battery.brand,
            steps = battery.steps,
            onAllow = { battery.requestExemption(::startActivity) },
            onOpenAppSettings = { battery.openAppSettings(::startActivity) },
            onBack = navigateBack,
        )
    }

    @Composable
    private fun SettingsRoute(
        diagnostics: DiagnosticsController,
        battery: BatterySetupController,
        navigate: (AppDestination) -> Unit,
        appearance: Appearance,
        onKidsMode: () -> Unit,
    ) {
        val ocrState by remember { ocrModels.state().loggingFailures(SETTINGS_TAG, "the OCR models") }
            .collectAsStateWithLifecycle(initialValue = null)
        val punctState by remember { punctuationModels.state().loggingFailures(SETTINGS_TAG, "the punctuation model") }
            .collectAsStateWithLifecycle(initialValue = null)
        val volumeKeys by remember { screenPreference.volumeKeysSkip() }.collectAsStateWithLifecycle(false)
        val scope = rememberCoroutineScope()
        val textPreferences = remember { ReadingTextPreferences.forContext(applicationContext) }
        // A one-line properties file, read once when Settings opens (like the onboarding flags). Saved off the main
        // thread, one write at a time and in order, so quick taps leave the file as the switch shows.
        var skipCitations by remember { mutableStateOf(textPreferences.skipCitationMarkers()) }
        val textWrites = remember { Dispatchers.IO.limitedParallelism(1) }
        val keepScreenOn by remember { screenPreference.keepScreenOn() }.collectAsStateWithLifecycle(false)
        SettingsScreen(
            shareState = diagnostics.shareState,
            batteryExempt = battery.exempt,
            versionName = BuildConfig.VERSION_NAME,
            showDebugTools = BuildConfig.DEBUG,
            onOpenBatterySetup = { navigate(AppDestination.BatterySetup(fromSettings = true)) },
            onOpenVoices = { navigate(AppDestination.Voices()) },
            onOpenLicences = { navigate(AppDestination.Licences) },
            onOpenStorage = { navigate(AppDestination.Storage) },
            onShareLogs = { diagnostics.shareLogs { intent -> startActivity(intent) } },
            onSimulateReadingSession = {
                diagnostics.simulateReadingSession()
                Toast.makeText(this@MainActivity, R.string.debug_simulate_done, Toast.LENGTH_LONG).show()
            },
            ocrModels = ocrState,
            onOcrModels = { action ->
                scope.runModelAction(action, ocrModels::download, ocrModels::cancel) { ocrModels.delete() }
            },
            skipCitations = skipCitations,
            onSkipCitationsChange = { skip ->
                skipCitations = skip
                scope.launch {
                    val saved = withContext(textWrites) { textPreferences.setSkipCitationMarkers(skip) }
                    if (!saved) skipCitations = !skip // logged; the switch shows what books will use
                }
            },
            keepScreenOn = keepScreenOn,
            onKeepScreenOnChange = { on -> scope.launch { screenPreference.saveKeepScreenOn(on) } },
            punctuationModel = punctState,
            volumeKeysSkip = volumeKeys,
            onVolumeKeysSkipChange = { on -> scope.launch { screenPreference.saveVolumeKeysSkip(on) } },
            onPunctuationModel = { action ->
                scope.runModelAction(action, punctuationModels::download, punctuationModels::cancel) {
                    punctuationModels.delete()
                }
            },
            voiceCommands = rememberVoiceCommandsSettings(voiceCommandModels, screenPreference),
            pronunciation = rememberPronunciationSettings(pronunciationStore),
            appearance = appearance,
            onAppearanceChange = { changed -> scope.launch { appearancePreferences.save(changed) } },
            onKidsMode = onKidsMode,
            language = appLanguageStore.load(),
            onLanguageChange = ::switchLanguage,
        )
    }

    /** Saves the app language (plan item 44), then shows the screens in it. */
    private fun switchLanguage(language: AppLanguage) {
        if (appLanguageStore.save(language)) AppLanguages.switchTo(this, language)
    }

    /** Kids mode (plan item 71) reads slower: the listener's speed is kept to give back when a grown-up leaves. */
    private suspend fun turnOnKidsMode() {
        val before = speedPreference.load()
        if (kidsModePreferences.turnOn(before)) speedPreference.save(kidsSpeed(before))
    }

    /** Gives the listener's own speed back; with none saved before, the kids speed stays as their speed. */
    private suspend fun turnOffKidsMode(mode: KidsMode) {
        if (kidsModePreferences.turnOff()) mode.speedBefore?.let { speedPreference.save(it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        SharedPdfIntent.uriOf(intent)?.let { sharedPdf = it }
        SharedPdfIntent.textOf(intent)?.let { sharedText = it }
        continueStudioJobs(intent)
        ResumeWidgetProvider.bookIdOf(intent)?.let { widgetResume = it }
    }

    @Composable
    private fun LibraryRoute(
        controller: LibraryController,
        navigate: (AppDestination) -> Unit,
        onContinue: (BookId) -> Unit,
        banner: @Composable () -> Unit,
    ) {
        val context = LocalContext.current.applicationContext
        val playWithPermission = rememberPlayWithNotificationPermission()
        val studio = rememberStudioLibrary(studioParts, playWithPermission)
        val actions = remember(controller) {
            object : LibraryActions {
                override fun onAddPdf(documentUri: Uri) = controller.add(documentUri.toString())

                override fun onLocate(id: BookId, documentUri: Uri) = controller.locate(id, documentUri.toString())

                override fun onOpenBook(book: Book) = navigate(AppDestination.Reader(book.id))

                override fun onContinue(book: Book) = playWithPermission { onContinue(book.id) }

                override fun onRequestRemoval(book: Book) = controller.proposeRemoval(book)

                // Its progress, Cancel and result are on the library's card and in a notification, so it asks for
                // that permission first.
                override fun onExportAudiobook(book: Book) = context.startLongJob(LongJobKind.AUDIOBOOK) {
                    playWithPermission { AudiobookExport.start(context, book.id) }
                }

                override fun onConfirmRemoval() = controller.confirmRemoval()

                override fun onCancelRemoval() = controller.proposeRemoval(null)

                override fun onMessageShown() = controller.messageShown()

                override fun onScreenResumed() = controller.refreshAvailability()

                override fun onQuery(query: LibraryQuery) = controller.setQuery(query)

                override fun onAddToUpNext(book: Book) {
                    upNext.add(book.id)
                    // Nothing on the library screen changes, so say it was done.
                    Toast.makeText(context, R.string.up_next_added, Toast.LENGTH_SHORT).show()
                }

                override fun onBookAction(book: Book, action: BookAction) = studio.onBookAction(book, action)

                override fun onLibraryAction(action: LibraryAction) = studio.onLibraryAction(action)
            }
        }
        val books = (controller.listState as? LibraryListState.Loaded)?.all?.mapTo(HashSet()) { it.book.id }
        // Covers of removed books go once the list has loaded; never while it is loading or failed.
        LaunchedEffect(books) { books?.let { bookCovers.prune(it) } }
        LibraryScreen(
            listState = controller.listState,
            busy = controller.busy,
            message = controller.message,
            pendingRemoval = controller.pendingRemoval,
            actions = actions,
            // The long jobs' progress and results sit under the activity's own banner, above the books.
            banner = {
                banner()
                LongJobsPanel(Modifier.padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceS))
            },
            covers = bookCovers,
        )
        studio.Sheets((controller.listState as? LibraryListState.Loaded)?.all?.map { it.book })
    }

    @Composable
    private fun VoicesRoute(controller: VoicesController, navigateBack: () -> Unit) {
        val state by controller.state.collectAsStateWithLifecycle()
        val actions = remember(controller) { voicesActions(controller, navigateBack) }
        VoicesScreen(state = state, actions = actions)
    }

    private fun voicesActions(controller: VoicesController, navigateBack: () -> Unit): VoicesActions =
        object : VoicesActions {
            override fun onBack() = navigateBack()

            override fun onDownload(row: VoiceRow) = controller.download(row)

            override fun onCancel(row: VoiceRow) = controller.cancel(row)

            override fun onUse(row: VoiceRow) = controller.use(row)

            override fun onUseDefault(lang: Lang) = controller.useDefault(lang)

            override fun onRequestDelete(row: VoiceRow) = controller.proposeDelete(row)

            override fun onConfirmDelete() = controller.confirmDelete()

            override fun onCancelDelete() = controller.proposeDelete(null)

            override fun onImport(treeUri: Uri, lang: Lang, label: String) =
                controller.import(treeUri.toString(), lang, label)

            override fun onMessageShown() = controller.messageShown()
        }

    /** Loaded on each visit: a voice downloaded since then shows its licence files. */
    @Composable
    private fun LicencesRoute(navigateBack: () -> Unit) {
        val scope = rememberCoroutineScope()
        val controller = remember { LicencesController(licenceSource, scope).also { it.start() } }
        val state by controller.state.collectAsStateWithLifecycle()
        LicencesScreen(
            state = state,
            onBack = navigateBack,
            onOpenTexts = controller::openTexts,
            onCloseTexts = controller::closeTexts,
        )
    }

    @Composable
    private fun ReaderRoute(
        target: ReaderTarget,
        books: List<Book>,
        onBack: () -> Unit,
        onShowPage: () -> Unit = {},
        openBookmark: Bookmark? = null,
        onBookmarkOpened: () -> Unit = {},
        onOtherBookPlaying: (BookId) -> Unit = {},
        onAutoResumeTaken: () -> Unit = {},
    ) {
        val id = target.id
        val context = LocalContext.current.applicationContext
        val scope = rememberCoroutineScope()
        val port = remember(id) { MediaControllerPlaybackPort(context) }
        val model = remember(id) {
            ReaderModel(
                bookId = id,
                books = LibraryReaderBooks(library),
                opener = documentOpener,
                player = port,
                speeds = speedPreference,
                config = readerConfig,
                scope = scope,
                resume = LocatorReaderResume(resumeLocator),
                autoResume = target.autoResume,
                views = viewPreference,
            ).also { it.start() }
        }
        val thumbs = rememberPageThumbnails(id, model)
        // Connected while visible, or while voice commands listen with the screen off (they act on this player).
        val listeningInBackground = remember(port) { MutableStateFlow(false) }
        PlayerConnectionEffect(port, listeningInBackground)
        DisposableEffect(model) { onDispose { model.close() } }
        AutoResumeRequests(model, target.autoResume, onAutoResumeTaken)
        val state by model.state.collectAsStateWithLifecycle()
        val ocrState by remember { ocrModels.state().loggingFailures(READER_TAG, "the OCR models") }
            .collectAsStateWithLifecycle(initialValue = null)
        // A scanned page waiting for the OCR models is read as soon as they are in.
        LaunchedEffect(model, ocrState is OcrModelsState.Ready) {
            if (ocrState is OcrModelsState.Ready) model.reloadIfWaitingForOcr()
        }
        val playWithPermission = rememberPlayWithNotificationPermission()
        val notes = remember(id) {
            BookNotesModel(id, NoteStores(bookmarkStore, pronunciationStore, highlightStore), scope, PIPELINE_VERSION)
                .also { it.start() }
        }
        val notesState by notes.state.collectAsStateWithLifecycle()
        val studio = rememberReaderStudio(id, model, scope, notes, port, searchIndexes, voiceNoteParts)
        val voiceRows by remember { voiceManager.rows().loggingFailures(READER_TAG, "the voices") }
            .collectAsStateWithLifecycle(initialValue = emptyList())
        // The reader and the player share the model, so back must follow the screen now shown, not the first one.
        val back by rememberUpdatedState(onBack)
        val actions = remember(model) { readerActions(model, notes, playWithPermission) { back() } }
        BookWindowEffects(model, state, listeningInBackground)
        val opened = state.document is DocumentState.Open
        LaunchedEffect(model, opened, openBookmark) {
            if (opened && openBookmark != null) {
                model.goToBookmark(openBookmark)
                onBookmarkOpened()
            }
        }
        val screen = BookScreen(target, books, onShowPage, onOtherBookPlaying)
        ReaderContent(screen, state, actions, ocrState, notesState, notes, voiceRows, port, thumbs, studio)
    }

    /**
     * What this book's screen holds outside its own layout: the screen kept on, the volume keys, the paragraph-skip
     * notice and the voice-command microphone. While the screen animates out, the arriving one owns them all, so a
     * transition never keeps the screen on twice, shows two notices or opens two microphones.
     */
    @Composable
    private fun BookWindowEffects(model: ReaderModel, state: ReaderState, listening: MutableStateFlow<Boolean>) {
        val shown = !isLeavingScreen()
        val playing = shown && state.playback.wantsToPlay
        val keepScreenOn by remember { screenPreference.keepScreenOn() }.collectAsStateWithLifecycle(false)
        KeepScreenOn(playing && keepScreenOn)
        val volumeKeys by remember { screenPreference.volumeKeysSkip() }.collectAsStateWithLifecycle(false)
        VolumeKeySkips(playing && volumeKeys, model::skipForward, model::skipBack)
        if (shown) {
            ParagraphSkipNotice(state.skipRefused, model::skipRefusalShown)
            ReaderVoiceCommands(model, screenPreference, voiceCommandModels, readerConfig) { listening.value = it }
        }
    }

    /** The book's page pictures for the scrubber (plan item 17), drawn by its reader model. */
    @Composable
    private fun rememberPageThumbnails(id: BookId, model: ReaderModel): PageThumbnails {
        val widthPx = with(LocalDensity.current) { PREVIEW_WIDTH.roundToPx() }
        return remember(model, widthPx) {
            PageThumbnails(File(cacheDir, "$THUMBNAILS_DIR/${id.value}"), widthPx, id.logLabel, model::thumbnail)
        }
    }

    /** The reader, or the full player on the same model; both open the Up next sheet (plan item 25). */
    @Composable
    private fun ReaderContent(
        screen: BookScreen,
        state: ReaderState,
        actions: ReaderActions,
        ocrState: OcrModelsState?,
        notesState: NotesState,
        notes: BookNotesModel,
        voiceRows: List<VoiceRow>,
        port: PlaybackPort,
        thumbnails: PageThumbnails,
        studio: ReaderStudio,
    ) {
        val (target, books, onShowPage) = screen
        if (target.asPlayer) FollowPlayingBook(target.id, state.playback.position?.bookId, screen.onOtherBookPlaying)
        val scope = rememberCoroutineScope()
        var upNextOpen by rememberSaveable { mutableStateOf(false) }
        var searchOpen by rememberSaveable { mutableStateOf(false) }
        var tutorOpen by rememberSaveable { mutableStateOf(false) }
        val document = state.document
        val look by remember { readingLookPreferences.look }.collectAsStateWithLifecycle(ReadingLook())
        val spokenWord = rememberSpokenWord(state, port, look.wordHighlight)
        // Errors fall back to the reader, which explains them and offers a retry or the password prompt.
        if (target.asPlayer && (document is DocumentState.Open || document == DocumentState.Loading)) {
            PlayerScreen(
                state = state,
                config = readerConfig,
                actions = actions,
                onShowPage = onShowPage,
                onOpenUpNext = { upNextOpen = true },
                book = books.firstOrNull { it.id == target.id },
                covers = bookCovers,
                voices = voiceRows,
                spokenWord = { spokenWord.value },
            )
        } else {
            ReaderScreen(
                state = state,
                config = readerConfig,
                actions = actions,
                ocrModels = ocrState,
                notes = notesState,
                notesActions = notes,
                voices = voiceRows,
                look = look,
                onLookChange = { changed -> scope.launch { readingLookPreferences.save(changed) } },
                spokenWord = { spokenWord.value },
                thumbnails = thumbnails,
                onOpenUpNext = { upNextOpen = true },
                onSearch = { searchOpen = true },
                voiceNotes = studio.voiceNotes,
                lookUp = studio.lookUp,
                onTutor = { tutorOpen = true },
            )
        }
        // What is playing, not the book on screen: with A playing, B's reader must not hide B from the sheet.
        val playing = state.playback.position?.bookId ?: target.id
        if (upNextOpen) UpNextRoute(books, playing) { upNextOpen = false }
        if (searchOpen) SearchRoute(studio.search, onGoToPage = actions::onGoToPage) { searchOpen = false }
        if (tutorOpen) ReaderTutorRoute(state, port::pause, actions::onGoToPage) { tutorOpen = false }
    }

    @Composable
    private fun UpNextRoute(books: List<Book>, playing: BookId, onDismiss: () -> Unit) {
        val state by upNext.state.collectAsStateWithLifecycle()
        UpNextSheet(state, books, playing, upNext, onDismiss, bookCovers)
    }

    private fun readerActions(
        model: ReaderModel,
        notes: BookNotesModel,
        playWithPermission: (() -> Unit) -> Unit,
        navigateBack: () -> Unit,
    ): ReaderActions = object : ReaderActions {
        override fun onBack() = navigateBack()

        override fun onPreviousPage() = model.previousPage()

        override fun onNextPage() = model.nextPage()

        override fun onGoToPage(page: Int) = model.showPage(page)

        override fun onShowReadingPosition() = model.showReadingPosition()

        override fun onSentenceTapped(unit: ReadingUnit) = readFrom(unit)

        // Asks for the notification permission only when the tap is on a sentence.
        override fun onPageTapped(point: ViewPoint) {
            model.sentenceAt(point)?.let { readFrom(it) }
        }

        // A greyed sentence first explains why it is skipped (RI-9); the permission is asked if it is read anyway.
        private fun readFrom(unit: ReadingUnit) {
            if (model.state.value.isUnreadable(unit)) {
                model.readFrom(unit)
            } else {
                playWithPermission { model.readFrom(unit) }
            }
        }

        override fun onSentenceLongPressed(unit: ReadingUnit) = notes.openMenu(unit)

        override fun onPageLongPressed(point: ViewPoint) {
            model.sentenceAt(point)?.let(notes::openMenu)
        }

        override fun onReportSentence(unit: ReadingUnit) = model.askReport(unit)

        // The clip's progress and its Share button are in a notification, so it asks for that permission first.
        override fun onShareClip(unit: ReadingUnit) = playWithPermission {
            AudioClips.share(applicationContext, model.state.value.bookId, unit.page, unit.index, model.openPassword)
        }

        // From the reader, a password-protected PDF can be saved too: its password goes along in memory (ADR-034).
        override fun onSaveAudiobook() = startLongJob(LongJobKind.AUDIOBOOK) {
            playWithPermission {
                AudiobookExport.start(applicationContext, model.state.value.bookId, model.openPassword)
            }
        }

        override fun onBookmark(bookmark: Bookmark) = model.goToBookmark(bookmark)

        override fun onBookmarkHere() {
            model.sentenceHere()?.let(notes::bookmark)
        }

        override fun onOwnSettings(on: Boolean) = model.setOwnSettings(on)

        override fun onBookVoice(lang: Lang, voice: VoiceId) = model.setBookVoice(lang, voice)

        override fun onReport(reason: SoundedWrongReason) = model.report(reason)

        override fun onReportDismissed() = model.dismissReport()

        override fun onReadUnreadable() = playWithPermission(model::readUnreadable)

        override fun onUnreadableDismissed() = model.dismissUnreadable()

        override fun onChapter(chapter: ChapterEntry) = model.goToChapter(chapter)

        override fun onViewChanged(view: ReaderView) = model.setView(view)

        override fun onPageWidth(widthPx: Int) = model.setPageWidth(widthPx)

        override fun onPlayPause() {
            if (model.state.value.isReading) model.playOrPause() else playWithPermission(model::playOrPause)
        }

        override fun onSkipBack() = model.skipBack()

        override fun onSkipForward() = model.skipForward()

        override fun onSkipParagraph(forward: Boolean) = model.skipParagraph(forward)

        override fun onReplayPrevious() = model.replayPrevious()

        override fun onSpeed(speed: Float) = model.setSpeed(speed)

        override fun onPace(pace: Pace) = model.setPace(pace)

        override fun onClausePauses(on: Boolean) = model.setClausePauses(on)

        override fun onReadingModes(modes: ReadingModes) = model.setReadingModes(modes)

        override fun onSleepTimer(setting: SleepTimerSetting) = model.setSleepTimer(setting)

        override fun onSleepOptions(options: SleepOptions) = model.setSleepOptions(options)

        override fun onFocusSound(options: FocusSoundOptions) = model.setFocusSound(options)

        override fun onVoiceShaping(shaping: VoiceShaping) = model.setVoiceShaping(shaping)

        override fun onUndoJump() = model.undoJump()

        override fun onStudy(action: StudyAction, loop: Boolean) = model.study(action, loop)

        override fun onPasswordSubmitted(password: String) = model.submitPassword(password)

        override fun onPasswordDismissed() = model.dismissPassword()

        override fun onAskForPassword() = model.askForPassword()

        override fun onRetry() = model.retry()

        override fun onDownloadOcrModels() = ocrModels.download()
    }
}

/**
 * Where the app is (a saveable route) and two one-shot hand-offs to the reader, which are not saveable on purpose: a
 * restored activity must never start reading by itself (C7), nor jump to a bookmark tapped before.
 */
@Stable
internal class AppNavigation(routeState: MutableState<String>) {
    private var route by routeState
    val destination: AppDestination get() = AppDestination.fromRoute(route)

    /** "Continue" was pressed: the reader opened next reads on from where the book was left. */
    var autoResumeBook by mutableStateOf<BookId?>(null)

    /** A bookmark tapped in the Notes tab, opened once its book's reader has the PDF open. */
    var pendingBookmark by mutableStateOf<Bookmark?>(null)

    /** Kids mode (plan item 71) is on: only the library and the player can be reached. */
    var kids by mutableStateOf(false)

    fun navigate(next: AppDestination) {
        route = (if (kids) kidsDestination(next) else next).toRoute()
    }

    fun continueReading(id: BookId) {
        autoResumeBook = id
        navigate(AppDestination.Reader(id))
    }

    fun openBookmark(bookmark: Bookmark) {
        pendingBookmark = bookmark
        navigate(AppDestination.Reader(bookmark.bookId, AppTab.NOTES))
    }
}

@Composable
private fun rememberAppNavigation(): AppNavigation {
    val route = rememberSaveable { mutableStateOf(AppDestination.Library.toRoute()) }
    return remember { AppNavigation(route) }
}

/** The book screen's target and its ways out, bundled for ReaderContent. */
internal data class BookScreen(
    val target: ReaderTarget,
    val books: List<Book>,
    val onShowPage: () -> Unit,
    val onOtherBookPlaying: (BookId) -> Unit,
)

/**
 * The full player follows the listener's book: once this book has played, another book taking over (Up next moving
 * on) moves the player to it, so Play does not restart the finished one. A book that was never playing here (a
 * player opened in kids mode while another book plays) stays until it plays.
 */
@Composable
private fun FollowPlayingBook(id: BookId, playing: BookId?, onOther: (BookId) -> Unit) {
    var heldThisBook by remember(id) { mutableStateOf(false) }
    val other by rememberUpdatedState(onOther)
    LaunchedEffect(playing) {
        when {
            playing == id -> heldThisBook = true
            playing != null && heldThisBook -> other(playing)
        }
    }
}

/**
 * "Continue" for this book (the widget's Resume, the tile, the library). The model reads on by itself when created
 * with it; a Resume asked while the book is already open goes to [ReaderModel.requestAutoResume]. Either way the
 * request is taken once.
 */
@Composable
private fun AutoResumeRequests(model: ReaderModel, autoResume: Boolean, onTaken: () -> Unit) {
    var modelOpen by remember(model) { mutableStateOf(false) }
    val taken by rememberUpdatedState(onTaken)
    LaunchedEffect(model, autoResume) {
        if (autoResume) {
            if (modelOpen) model.requestAutoResume()
            taken()
        }
        modelOpen = true
    }
}

/** Which book the reader route opens, and whether as the full player (ADR-036). */
internal data class ReaderTarget(val id: BookId, val autoResume: Boolean, val asPlayer: Boolean = false)

/** A player controller connected only while visible: a connected one keeps the playback service bound. */
@Composable
private fun rememberConnectedPlaybackPort(): MediaControllerPlaybackPort {
    val context = LocalContext.current.applicationContext
    val port = remember { MediaControllerPlaybackPort(context) }
    LifecycleStartEffect(port) {
        port.connect()
        onStopOrDispose { port.release() }
    }
    return port
}

@Composable
private fun UnfinishedSessionPrompt(diagnostics: DiagnosticsController, resume: (BookId) -> Unit) {
    val session = diagnostics.unfinishedSession ?: return
    UnfinishedSessionDialog(
        session = session,
        onResume = { diagnostics.resumeUnfinished(resume) },
        onDismiss = diagnostics::dismissUnfinished,
    )
}

/**
 * Adds a PDF shared by another app while its grant lasts ([add] keeps the grant or a copy), then opens it.
 * [add] runs on the library's own scope, so clearing [shared] here does not cancel it.
 */
@Composable
private fun SharedPdfEffect(
    shared: String?,
    onTaken: () -> Unit,
    add: (String, (BookId) -> Unit) -> Unit,
    navigate: (AppDestination) -> Unit,
) {
    LaunchedEffect(shared) {
        if (shared == null) return@LaunchedEffect
        onTaken()
        add(shared) { id -> navigate(AppDestination.Reader(id)) }
    }
}

private const val SETTINGS_TAG = "Audora.Settings"
private const val READER_TAG = "Audora.Reader"

/** cacheDir subfolder for the page scrubber's thumbnails (plan item 17), one folder per book. */
internal const val THUMBNAILS_DIR = "thumbnails"

/** Edge-to-edge's own three-button navigation bar scrims (androidx.activity's defaults), kept explicit here. */
private const val LIGHT_NAV_SCRIM = 0xE6FFFFFF.toInt()
private const val DARK_NAV_SCRIM = 0x801B1B1B.toInt()
