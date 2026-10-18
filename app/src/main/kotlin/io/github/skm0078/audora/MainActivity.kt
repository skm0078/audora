// File: app/src/main/kotlin/io/github/skm0078/audora/MainActivity.kt
// Purpose: Single activity hosting the Compose UI, edge-to-edge from day one (Build Spec §4), and its routes.

package io.github.skm0078.audora

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.skm0078.audora.battery.BatteryExemption
import io.github.skm0078.audora.battery.OnboardingPreferences
import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.core.config.TextBookConfig
import io.github.skm0078.audora.core.diagnostics.ReadingSessionTracker
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.Library
import io.github.skm0078.audora.core.library.LibraryQuery
import io.github.skm0078.audora.core.logging.AudoraLog
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
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.resume.ResumeAnchorStore
import io.github.skm0078.audora.core.resume.ResumeLocator
import io.github.skm0078.audora.diagnostics.LogSharer
import io.github.skm0078.audora.pdf.PIPELINE_VERSION
import io.github.skm0078.audora.pdf.outline.ChapterEntry
import io.github.skm0078.audora.pdf.render.ViewPoint
import io.github.skm0078.audora.playback.backup.Backups
import io.github.skm0078.audora.playback.book.ReadingTextPreferences
import io.github.skm0078.audora.playback.documents.SharedPdfCopies
import io.github.skm0078.audora.playback.documents.WatchedFolders
import io.github.skm0078.audora.playback.epub.BookCopies
import io.github.skm0078.audora.playback.epub.CopyError
import io.github.skm0078.audora.playback.epub.CopyKind
import io.github.skm0078.audora.playback.export.AudioClips
import io.github.skm0078.audora.playback.export.AudiobookExport
import io.github.skm0078.audora.playback.export.MarkStores
import io.github.skm0078.audora.playback.export.NotesExport
import io.github.skm0078.audora.playback.notes.VoiceNoteAudio
import io.github.skm0078.audora.playback.photo.PhotoBooks
import io.github.skm0078.audora.playback.report.SoundedWrongReason
import io.github.skm0078.audora.playback.search.SearchIndexes
import io.github.skm0078.audora.playback.studio.StudioMode
import io.github.skm0078.audora.speech.dictionary.Dictionaries
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
import io.github.skm0078.audora.ui.library.BackupRoute
import io.github.skm0078.audora.ui.library.BookAction
import io.github.skm0078.audora.ui.library.BookCovers
import io.github.skm0078.audora.ui.library.LibraryAction
import io.github.skm0078.audora.ui.library.LibraryActions
import io.github.skm0078.audora.ui.library.LibraryController
import io.github.skm0078.audora.ui.library.LibraryListState
import io.github.skm0078.audora.ui.library.LibraryScreen
import io.github.skm0078.audora.ui.library.MiniPlayer
import io.github.skm0078.audora.ui.library.WatchedFoldersRoute
import io.github.skm0078.audora.ui.library.nowPlaying
import io.github.skm0078.audora.ui.library.restoreMessageOf
import io.github.skm0078.audora.ui.licences.LicenceSource
import io.github.skm0078.audora.ui.licences.LicencesController
import io.github.skm0078.audora.ui.licences.LicencesScreen
import io.github.skm0078.audora.ui.notes.NotesScreen
import io.github.skm0078.audora.ui.notes.bookNotes
import io.github.skm0078.audora.ui.quiz.QuizRoute
import io.github.skm0078.audora.ui.reader.BookNotesModel
import io.github.skm0078.audora.ui.reader.DocumentState
import io.github.skm0078.audora.ui.reader.KeepScreenOn
import io.github.skm0078.audora.ui.reader.LibraryReaderBooks
import io.github.skm0078.audora.ui.reader.LocatorReaderResume
import io.github.skm0078.audora.ui.reader.LookUpBinding
import io.github.skm0078.audora.ui.reader.LookUpModel
import io.github.skm0078.audora.ui.reader.MediaControllerPlaybackPort
import io.github.skm0078.audora.ui.reader.NoteStores
import io.github.skm0078.audora.ui.reader.NotesState
import io.github.skm0078.audora.ui.reader.PlaybackPort
import io.github.skm0078.audora.ui.reader.PlayerScreen
import io.github.skm0078.audora.ui.reader.ReaderActions
import io.github.skm0078.audora.ui.reader.ReaderDocumentOpener
import io.github.skm0078.audora.ui.reader.ReaderModel
import io.github.skm0078.audora.ui.reader.ReaderScreen
import io.github.skm0078.audora.ui.reader.ReaderState
import io.github.skm0078.audora.ui.reader.ReaderView
import io.github.skm0078.audora.ui.reader.ReadingLook
import io.github.skm0078.audora.ui.reader.ReadingLookPreferences
import io.github.skm0078.audora.ui.reader.ScreenPreference
import io.github.skm0078.audora.ui.reader.SearchModel
import io.github.skm0078.audora.ui.reader.SearchScreen
import io.github.skm0078.audora.ui.reader.SearchSource
import io.github.skm0078.audora.ui.reader.SleepTimerSetting
import io.github.skm0078.audora.ui.reader.SpeedPreference
import io.github.skm0078.audora.ui.reader.ViewPreference
import io.github.skm0078.audora.ui.reader.VoiceNotesBinding
import io.github.skm0078.audora.ui.reader.VoiceNotesModel
import io.github.skm0078.audora.ui.reader.VolumeKeySkips
import io.github.skm0078.audora.ui.reader.rememberPlayWithNotificationPermission
import io.github.skm0078.audora.ui.reader.rememberRecordWithPermission
import io.github.skm0078.audora.ui.reader.rememberSpokenWord
import io.github.skm0078.audora.ui.settings.OcrModelsAction
import io.github.skm0078.audora.ui.settings.SettingsScreen
import io.github.skm0078.audora.ui.settings.runModelAction
import io.github.skm0078.audora.ui.shell.AppShell
import io.github.skm0078.audora.ui.theme.Appearance
import io.github.skm0078.audora.ui.theme.AppearancePreferences
import io.github.skm0078.audora.ui.theme.AudoraTheme
import io.github.skm0078.audora.ui.upnext.UpNextPort
import io.github.skm0078.audora.ui.upnext.UpNextSheet
import io.github.skm0078.audora.ui.upnext.libraryBooks
import io.github.skm0078.audora.ui.voices.VoiceManager
import io.github.skm0078.audora.ui.voices.VoicesActions
import io.github.skm0078.audora.ui.voices.VoicesController
import io.github.skm0078.audora.ui.voices.VoicesScreen
import io.github.skm0078.audora.widget.ResumeWidgetEffect
import io.github.skm0078.audora.widget.ResumeWidgetProvider
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
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

    /** Short jobs that must outlive a screen (the notes export, saving a voice note); cancelled with the activity. */
    private val activityScope = MainScope()

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

    @Inject
    lateinit var licenceSource: LicenceSource

    @Inject
    lateinit var upNext: UpNextPort

    @Inject
    lateinit var backups: Backups

    @Inject
    lateinit var photoBooks: PhotoBooks

    private val appearancePreferences: AppearancePreferences by lazy { AppearancePreferences(applicationContext) }

    private val readingLookPreferences: ReadingLookPreferences by lazy { ReadingLookPreferences(applicationContext) }

    private val searchIndexes: SearchIndexes by lazy { SearchIndexes(applicationContext) }

    private val voiceNoteAudio: VoiceNoteAudio by lazy { VoiceNoteAudio(applicationContext) }

    private val watchedFolders: WatchedFolders by lazy { WatchedFolders(applicationContext, library) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            sharedPdf = SharedPdfIntent.uriOf(intent)
            sharedText = SharedPdfIntent.textOf(intent)
            widgetResume = ResumeWidgetProvider.bookIdOf(intent)
            BookCopies.continueFrom(this, intent)
            AudiobookExport.continueFrom(this, intent)
            Backups.takeStartResult()?.let { Toast.makeText(this, restoreMessageOf(it), Toast.LENGTH_LONG).show() }
        }
        setContent {
            val appearance by remember {
                appearancePreferences.appearance
            }.collectAsStateWithLifecycle(initialValue = Appearance())
            AudoraTheme(appearance) { AppContent(appearance) }
        }
    }

    /** The screens and the routing between them (ADR-036: three tabs, the reader and the player over them). */
    @Composable
    private fun AppContent(appearance: Appearance) {
        val scope = rememberCoroutineScope()
        val diagnostics = remember { DiagnosticsController(readingSessionTracker, logSharer, scope) }
        // Activity-wide, so an add still running when the user opens Settings is not cancelled.
        val libraryController = remember {
            LibraryController(library, scope, resumeAnchors.observeAll(), sharedCopies::copy).apply { start() }
        }
        val battery = rememberBatterySetup(scope)
        // Activity-wide too, so an import keeps going when the user leaves the screen; created on first visit.
        val voicesController = remember { lazy { VoicesController(voiceManager, scope).also { it.start() } } }
        val nav = rememberAppNavigation()
        val destination = nav.destination
        BackHandler(enabled = destination.parent != null) {
            nav.navigate(destination.parent ?: AppDestination.Library)
        }
        LaunchedEffect(Unit) { diagnostics.checkUnfinishedSession() }
        LaunchedEffect(Unit) { dropStaleBookFiles() }
        SharedPdfEffect(sharedPdf, { sharedPdf = null }, libraryController::addShared, nav::navigate)
        SharedTextEffect()
        PhotoBookEffect()
        ResumeWidgetEffect(libraryController.listState, widgetResume, { widgetResume = null }, nav::continueReading)
        val books = libraryBooks(libraryController.listState)

        destination.tab?.let { tab ->
            TabsRoute(tab, libraryController, nav::navigate) {
                when (tab) {
                    AppTab.LIBRARY -> LibraryRoute(libraryController, nav::navigate, nav::continueReading) {
                        if (battery.showCard) {
                            BatterySetupCard(
                                onSetUp = { nav.navigate(AppDestination.BatterySetup(fromSettings = false)) },
                                onNotNow = battery::dismissCard,
                            )
                        }
                    }

                    AppTab.NOTES -> NotesRoute(books, nav::openBookmark)

                    AppTab.SETTINGS -> SettingsRoute(diagnostics, battery, nav::navigate, appearance)
                }
            }
        }
        ScreenRoute(destination, nav, voicesController, battery, books)
        UnfinishedSessionPrompt(diagnostics, nav::continueReading)
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

            AppDestination.Voices -> VoicesRoute(voicesController.value) { nav.navigate(AppDestination.Settings) }

            AppDestination.Licences -> LicencesRoute { nav.navigate(AppDestination.Settings) }

            is AppDestination.BatterySetup -> BatterySetupRoute(battery) {
                nav.navigate(destination.parent ?: AppDestination.Library)
            }

            is AppDestination.Reader -> {
                ReaderRoute(
                    ReaderTarget(destination.id, autoResume = nav.autoResumeBook == destination.id),
                    books,
                    onBack = { nav.navigate(AppDestination.Library) },
                    openBookmark = nav.pendingBookmark?.takeIf { it.bookId == destination.id },
                    onBookmarkOpened = { nav.pendingBookmark = null },
                )
                // The reader took the flag when it was created; opening the book later must not replay it.
                LaunchedEffect(destination.id) { nav.autoResumeBook = null }
            }

            is AppDestination.Player -> ReaderRoute(
                ReaderTarget(destination.id, autoResume = false, asPlayer = true),
                books,
                onBack = { nav.navigate(AppDestination.Library) },
                onShowPage = { nav.navigate(AppDestination.Reader(destination.id)) },
            )
        }
    }

    /** The tabs' frame with the mini player, which follows whatever the player holds (ADR-036). */
    @Composable
    private fun TabsRoute(
        tab: AppTab,
        library: LibraryController,
        navigate: (AppDestination) -> Unit,
        content: @Composable () -> Unit,
    ) {
        val port = rememberConnectedPlaybackPort()
        val playback by port.state.collectAsStateWithLifecycle()
        val playWithPermission = rememberPlayWithNotificationPermission()
        val playing = nowPlaying(playback, library.listState)
        AppShell(
            selected = tab,
            onSelect = { navigate(it.destination) },
            miniPlayer = {
                playing?.let { now ->
                    MiniPlayer(
                        playing = now,
                        onOpen = { navigate(AppDestination.Player(now.bookId)) },
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
    ) {
        val ocrState by remember { ocrModels.state() }.collectAsStateWithLifecycle(initialValue = null)
        val punctState by remember { punctuationModels.state() }.collectAsStateWithLifecycle(initialValue = null)
        val volumeKeys by remember { screenPreference.volumeKeysSkip() }.collectAsStateWithLifecycle(false)
        val scope = rememberCoroutineScope()
        val textPreferences = remember { ReadingTextPreferences.forContext(applicationContext) }
        // A one-line properties file, read once when Settings opens (like the onboarding flags). Saved off the main
        // thread, one write at a time and in order, so quick taps leave the file as the switch shows.
        var skipCitations by remember { mutableStateOf(textPreferences.skipCitationMarkers()) }
        val textWrites = remember { Dispatchers.IO.limitedParallelism(1) }
        val keepScreenOn by remember {
            screenPreference.keepScreenOn()
        }.collectAsStateWithLifecycle(initialValue = false)
        SettingsScreen(
            shareState = diagnostics.shareState,
            batteryExempt = battery.exempt,
            versionName = BuildConfig.VERSION_NAME,
            showDebugTools = BuildConfig.DEBUG,
            onOpenBatterySetup = { navigate(AppDestination.BatterySetup(fromSettings = true)) },
            onOpenVoices = { navigate(AppDestination.Voices) },
            onOpenLicences = { navigate(AppDestination.Licences) },
            onShareLogs = { diagnostics.shareLogs { intent -> startActivity(intent) } },
            onSimulateReadingSession = {
                diagnostics.simulateReadingSession()
                Toast.makeText(this@MainActivity, R.string.debug_simulate_done, Toast.LENGTH_LONG).show()
            },
            ocrModels = ocrState,
            onOcrModels = { action ->
                when (action) {
                    OcrModelsAction.DOWNLOAD -> ocrModels.download()
                    OcrModelsAction.CANCEL -> ocrModels.cancel()
                    OcrModelsAction.DELETE -> scope.launch { ocrModels.delete() }
                }
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
            appearance = appearance,
            onAppearanceChange = { changed -> scope.launch { appearancePreferences.save(changed) } },
        )
    }

    override fun onDestroy() {
        activityScope.cancel()
        super.onDestroy()
    }

    /** Plan item 30: the book's highlights, bookmarks and voice notes to Documents/Audora; a toast says where. */
    private fun exportNotes(book: Book) {
        val stores = MarkStores(highlightStore, bookmarkStore, voiceNoteStore)
        activityScope.launch {
            val saved = NotesExport.export(applicationContext, book.id, book.title, stores)
            val message = when (saved) {
                is Outcome.Success -> getString(R.string.notes_export_done, saved.value.joinToString(", "))

                is Outcome.Failure -> getString(
                    if (saved.error ==
                        CopyError.NothingToCopy
                    ) {
                        R.string.notes_export_empty
                    } else {
                        R.string.notes_export_failed
                    },
                )
            }
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Plan item 28: a staged restore is put in place at process start, so the app starts again in a new process.
     * The task is relaunched first, then this process ends; playback stops with it (nothing resumes on its own).
     */
    private fun restartForRestore() {
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.component ?: return
        AudoraLog.i(TAG, "Restarting to apply a restore")
        startActivity(Intent.makeRestartActivityTask(launch))
        Runtime.getRuntime().exit(0)
    }

    /** Plan item 62: the camera app takes the photo (no camera permission); [makePhotoBook] reads what it saved. */
    private fun pointAndRead(takePhoto: ActivityResultLauncher<Uri>) {
        try {
            takePhoto.launch(photoBooks.captureUri())
        } catch (e: ActivityNotFoundException) {
            AudoraLog.w(TAG, "No camera app: ${e.javaClass.simpleName}")
            Toast.makeText(this, R.string.photo_no_camera, Toast.LENGTH_LONG).show()
        } catch (e: SecurityException) {
            // Thrown if a CAMERA permission is ever declared but not granted; the manifest removes any merged one.
            AudoraLog.w(TAG, "Camera app refused: ${e.javaClass.simpleName}")
            Toast.makeText(this, R.string.photo_no_camera, Toast.LENGTH_LONG).show()
        }
    }

    /** The photo becomes a one-page book in the app's scope (rotation keeps it); [PhotoBookEffect] adds it. */
    private fun makePhotoBook() {
        val takenAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern(PHOTO_TITLE_DATE))
        photoBooks.startBook(getString(R.string.photo_book_title, takenAt))
    }

    /** A finished photo book is added and opened like a shared PDF, whichever activity instance is showing. */
    @Composable
    private fun PhotoBookEffect() {
        val made by photoBooks.made.collectAsStateWithLifecycle()
        LaunchedEffect(made) {
            val result = made ?: return@LaunchedEffect
            photoBooks.consume()
            when (result) {
                is Outcome.Success -> sharedPdf = result.value

                is Outcome.Failure ->
                    Toast.makeText(this@MainActivity, R.string.photo_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Text shared from another app (plan item 42): saved as a .txt beside the shared copies, then added and opened. */
    @Composable
    private fun SharedTextEffect() {
        LaunchedEffect(sharedText) {
            val shared = sharedText ?: return@LaunchedEffect
            sharedText = null
            val saved = if (shared.isOnlyLink) {
                null
            } else {
                sharedCopies.saveText(shared.text, shared.title, TextBookConfig().maxSharedChars)
            }
            if (saved != null) {
                sharedPdf = saved
            } else {
                val message = if (shared.isOnlyLink) R.string.shared_text_link else R.string.shared_text_failed
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        SharedPdfIntent.uriOf(intent)?.let { sharedPdf = it }
        SharedPdfIntent.textOf(intent)?.let { sharedText = it }
        BookCopies.continueFrom(this, intent)
        AudiobookExport.continueFrom(this, intent)
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
        val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
            if (taken) makePhotoBook() else photoBooks.discardCapture()
        }
        var watchedOpen by rememberSaveable { mutableStateOf(false) }
        var backupOpen by rememberSaveable { mutableStateOf(false) }
        var quizOpen by rememberSaveable { mutableStateOf(false) }
        val actions = remember(controller) {
            object : LibraryActions {
                override fun onAddPdf(documentUri: Uri) = controller.add(documentUri.toString())

                override fun onLocate(id: BookId, documentUri: Uri) = controller.locate(id, documentUri.toString())

                override fun onOpenBook(book: Book) = navigate(AppDestination.Reader(book.id))

                override fun onContinue(book: Book) = playWithPermission { onContinue(book.id) }

                override fun onRequestRemoval(book: Book) = controller.proposeRemoval(book)

                // Its progress, Cancel and result are in a notification, so it asks for that permission first.
                override fun onExportAudiobook(book: Book) = playWithPermission {
                    AudiobookExport.start(context, book.id)
                }

                override fun onConfirmRemoval() = controller.confirmRemoval()

                override fun onCancelRemoval() = controller.proposeRemoval(null)

                override fun onMessageShown() = controller.messageShown()

                override fun onScreenResumed() = controller.refreshAvailability()

                override fun onQuery(query: LibraryQuery) = controller.setQuery(query)

                override fun onAddToUpNext(book: Book) = upNext.add(book.id)

                // Studio mode and the copies report in a notification, so they ask for that permission first.
                override fun onBookAction(book: Book, action: BookAction) = when (action) {
                    BookAction.STUDIO -> playWithPermission { StudioMode.start(context, book.id) }

                    BookAction.ACCESSIBLE_COPY ->
                        playWithPermission { BookCopies.start(context, book.id, CopyKind.ACCESSIBLE) }

                    BookAction.TALKING_BOOK ->
                        playWithPermission { BookCopies.start(context, book.id, CopyKind.TALKING_BOOK) }

                    BookAction.EXPORT_NOTES -> exportNotes(book)
                }

                override fun onLibraryAction(action: LibraryAction) = when (action) {
                    LibraryAction.POINT_AND_READ -> pointAndRead(takePhoto)
                    LibraryAction.WATCHED_FOLDERS -> watchedOpen = true
                    LibraryAction.BACKUP -> backupOpen = true
                    LibraryAction.QUIZ -> quizOpen = true
                }
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
            banner = banner,
            covers = bookCovers,
        )
        WatchedFoldersRoute(watchedFolders, open = watchedOpen) { watchedOpen = false }
        BackupRoute(backups, open = backupOpen, onClose = { backupOpen = false }, onRestart = ::restartForRestore)
        QuizRoute(highlightStore, open = quizOpen) { quizOpen = false }
    }

    @Composable
    private fun VoicesRoute(controller: VoicesController, navigateBack: () -> Unit) {
        val state by controller.state.collectAsStateWithLifecycle()
        val actions = remember(controller) {
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
        }
        VoicesScreen(state = state, actions = actions)
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
        // Connected only while visible: a controller left connected keeps the playback service bound.
        LifecycleStartEffect(port) {
            port.connect()
            onStopOrDispose { port.release() }
        }
        DisposableEffect(model) { onDispose { model.close() } }
        val state by model.state.collectAsStateWithLifecycle()
        val ocrState by remember { ocrModels.state() }.collectAsStateWithLifecycle(initialValue = null)
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
        val search = rememberSearch(id, model, scope)
        val voiceNotes = rememberVoiceNotes(id, notes, port)
        val lookUp = rememberLookUp(id)
        val voiceRows by remember { voiceManager.rows() }.collectAsStateWithLifecycle(initialValue = emptyList())
        val actions = remember(model) { readerActions(model, notes, playWithPermission, onBack) }
        val keepScreenOn by remember {
            screenPreference.keepScreenOn()
        }.collectAsStateWithLifecycle(initialValue = false)
        KeepScreenOn(keepScreenOn && state.playback.wantsToPlay)
        val volumeKeys by remember { screenPreference.volumeKeysSkip() }.collectAsStateWithLifecycle(false)
        VolumeKeySkips(volumeKeys && state.playback.wantsToPlay, model::skipForward, model::skipBack)
        val opened = state.document is DocumentState.Open
        LaunchedEffect(model, opened, openBookmark) {
            if (opened && openBookmark != null) {
                model.goToBookmark(openBookmark)
                onBookmarkOpened()
            }
        }
        ReaderContent(
            target, books, state, actions, ocrState, notesState, notes, voiceRows, onShowPage, port, search, voiceNotes,
            lookUp,
        )
    }

    /** Search in the book (plan item 16): its index is built from the reader's own document while search is open. */
    @Composable
    private fun rememberSearch(id: BookId, model: ReaderModel, scope: CoroutineScope): SearchModel {
        val indexThread = remember { Dispatchers.IO.limitedParallelism(1) }
        val search = remember(model) {
            val source =
                SearchSource({ model.state.value.pageCount }, model::sentencesForSearch) { searchIndexes.open(id) }
            SearchModel(id, source, scope, indexThread, PIPELINE_VERSION)
        }
        DisposableEffect(search) { onDispose { search.close() } }
        return search
    }

    /** Search indexes and studio audio of removed books (and of older builds), and voice notes whose note is gone. */
    private suspend fun dropStaleBookFiles() = withContext(Dispatchers.IO) {
        val books = library.books().first().mapTo(HashSet()) { it.id }
        searchIndexes.dropBooksNotIn(books)
        StudioMode.reclaim(applicationContext, books)
        voiceNoteAudio.dropFilesNotIn(voiceNoteStore.observeAll().first().mapTo(HashSet()) { it.fileName })
    }

    /**
     * The dictionary on tap (plan item 33, ADR-047): the sentence menu's "Look up a word". The download state follows
     * the dictionary in use (the picked word's script, else the sentence's language); a word looked up while its
     * dictionary was missing is looked up again once it is installed.
     */
    @Composable
    private fun rememberLookUp(id: BookId): LookUpBinding {
        val dictionaries = remember { Dictionaries.get(applicationContext) }
        val scope = rememberCoroutineScope()
        val model = remember(id) {
            LookUpModel(dictionaries::forLanguage, scope, dictionaries::download, dictionaries::cancel)
        }
        val state by model.state.collectAsStateWithLifecycle()
        val lang = state?.dictionaryLang ?: Lang.EN
        val download by remember(lang) { dictionaries.state(lang) }.collectAsStateWithLifecycle(initialValue = null)
        val ready = download is OcrModelsState.Ready
        LaunchedEffect(ready, state?.missing) {
            val word = state?.word
            if (ready && state?.missing == true && word != null) model.pick(word)
        }
        return LookUpBinding(state, download, model)
    }

    /** Voice notes on sentences (plan item 32); reading pauses while one is recorded or played. */
    @Composable
    private fun rememberVoiceNotes(id: BookId, notes: BookNotesModel, port: PlaybackPort): VoiceNotesBinding {
        val model = remember(id) {
            // Activity-wide scope: a recording saved as the reader closes must still be written.
            VoiceNotesModel(id, voiceNoteStore, voiceNoteAudio, activityScope, notes::anchorOf, port::pause)
                .also { it.start() }
        }
        DisposableEffect(model) { onDispose { model.close() } }
        val state by model.state.collectAsStateWithLifecycle()
        val record = rememberRecordWithPermission(model)
        return VoiceNotesBinding(state, model, record)
    }

    /** The reader, or the full player on the same model; both open the Up next sheet (plan item 25). */
    @Composable
    private fun ReaderContent(
        target: ReaderTarget,
        books: List<Book>,
        state: ReaderState,
        actions: ReaderActions,
        ocrState: OcrModelsState?,
        notesState: NotesState,
        notes: BookNotesModel,
        voiceRows: List<VoiceRow>,
        onShowPage: () -> Unit,
        port: PlaybackPort,
        search: SearchModel,
        voiceNotes: VoiceNotesBinding,
        lookUp: LookUpBinding,
    ) {
        val scope = rememberCoroutineScope()
        var upNextOpen by rememberSaveable { mutableStateOf(false) }
        var searchOpen by rememberSaveable { mutableStateOf(false) }
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
                spokenWord = spokenWord,
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
                spokenWord = spokenWord,
                onOpenUpNext = { upNextOpen = true },
                onSearch = { searchOpen = true },
                voiceNotes = voiceNotes,
                lookUp = lookUp,
            )
        }
        if (upNextOpen) UpNextRoute(books, playing = target.id) { upNextOpen = false }
        if (searchOpen) SearchRoute(search, onGoToPage = actions::onGoToPage) { searchOpen = false }
    }

    @Composable
    private fun SearchRoute(search: SearchModel, onGoToPage: (Int) -> Unit, onClose: () -> Unit) {
        DisposableEffect(search) {
            search.open()
            onDispose { search.close() }
        }
        val state by search.state.collectAsStateWithLifecycle()
        SearchScreen(state, search::setQuery, onHit = { hit ->
            onGoToPage(hit.page)
            onClose()
        }, onDismiss = onClose)
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
        override fun onSaveAudiobook() = playWithPermission {
            AudiobookExport.start(applicationContext, model.state.value.bookId, model.openPassword)
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

        override fun onSpeed(speed: Float) = model.setSpeed(speed)

        override fun onPace(pace: Pace) = model.setPace(pace)

        override fun onClausePauses(on: Boolean) = model.setClausePauses(on)

        override fun onReadingModes(modes: ReadingModes) = model.setReadingModes(modes)

        override fun onSleepTimer(setting: SleepTimerSetting) = model.setSleepTimer(setting)

        override fun onSleepOptions(options: SleepOptions) = model.setSleepOptions(options)

        override fun onVoiceShaping(shaping: VoiceShaping) = model.setVoiceShaping(shaping)

        override fun onUndoJump() = model.undoJump()

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

    fun navigate(next: AppDestination) {
        route = next.toRoute()
    }

    fun continueReading(id: BookId) {
        autoResumeBook = id
        navigate(AppDestination.Reader(id))
    }

    fun openBookmark(bookmark: Bookmark) {
        pendingBookmark = bookmark
        navigate(AppDestination.Reader(bookmark.bookId))
    }
}

@Composable
private fun rememberAppNavigation(): AppNavigation {
    val route = rememberSaveable { mutableStateOf(AppDestination.Library.toRoute()) }
    return remember { AppNavigation(route) }
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

private const val TAG = "Audora.Main"

/** A photo book's title date: no colon or slash, since the title is also its file name (plan item 62). */
private const val PHOTO_TITLE_DATE = "d MMM yyyy, HH.mm"
