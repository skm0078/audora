// File: app/src/main/kotlin/io/github/skm0078/audora/MainActivity.kt
// Purpose: Single activity hosting the Compose UI, edge-to-edge from day one (Build Spec §4).

package io.github.skm0078.audora

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import io.github.skm0078.audora.core.diagnostics.ReadingSessionTracker
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.Library
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.resume.ResumeLocator
import io.github.skm0078.audora.diagnostics.LogSharer
import io.github.skm0078.audora.pdf.render.ViewPoint
import io.github.skm0078.audora.playback.book.ReadingTextPreferences
import io.github.skm0078.audora.speech.ocr.OcrModels
import io.github.skm0078.audora.speech.ocr.OcrModelsState
import io.github.skm0078.audora.speech.voices.VoiceRow
import io.github.skm0078.audora.ui.AppDestination
import io.github.skm0078.audora.ui.battery.BatterySetupCard
import io.github.skm0078.audora.ui.battery.BatterySetupController
import io.github.skm0078.audora.ui.battery.BatterySetupScreen
import io.github.skm0078.audora.ui.diagnostics.DiagnosticsController
import io.github.skm0078.audora.ui.diagnostics.UnfinishedSessionDialog
import io.github.skm0078.audora.ui.library.LibraryActions
import io.github.skm0078.audora.ui.library.LibraryController
import io.github.skm0078.audora.ui.library.LibraryScreen
import io.github.skm0078.audora.ui.licences.LicenceSource
import io.github.skm0078.audora.ui.licences.LicencesController
import io.github.skm0078.audora.ui.licences.LicencesScreen
import io.github.skm0078.audora.ui.reader.LibraryReaderBooks
import io.github.skm0078.audora.ui.reader.LocatorReaderResume
import io.github.skm0078.audora.ui.reader.MediaControllerPlaybackPort
import io.github.skm0078.audora.ui.reader.ReaderActions
import io.github.skm0078.audora.ui.reader.ReaderDocumentOpener
import io.github.skm0078.audora.ui.reader.ReaderModel
import io.github.skm0078.audora.ui.reader.ReaderScreen
import io.github.skm0078.audora.ui.reader.ReaderView
import io.github.skm0078.audora.ui.reader.SleepTimerSetting
import io.github.skm0078.audora.ui.reader.SpeedPreference
import io.github.skm0078.audora.ui.reader.ViewPreference
import io.github.skm0078.audora.ui.reader.rememberPlayWithNotificationPermission
import io.github.skm0078.audora.ui.settings.OcrModelsAction
import io.github.skm0078.audora.ui.settings.SettingsScreen
import io.github.skm0078.audora.ui.theme.AudoraTheme
import io.github.skm0078.audora.ui.voices.VoiceManager
import io.github.skm0078.audora.ui.voices.VoicesActions
import io.github.skm0078.audora.ui.voices.VoicesController
import io.github.skm0078.audora.ui.voices.VoicesScreen
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var readingSessionTracker: ReadingSessionTracker

    @Inject
    lateinit var logSharer: LogSharer

    @Inject
    lateinit var library: Library

    @Inject
    lateinit var readerConfig: ReaderConfig

    @Inject
    lateinit var speedPreference: SpeedPreference

    @Inject
    lateinit var viewPreference: ViewPreference

    @Inject
    lateinit var documentOpener: ReaderDocumentOpener

    @Inject
    lateinit var resumeLocator: ResumeLocator

    @Inject
    lateinit var voiceManager: VoiceManager

    /** The OCR model manager (P3-05); one per process, shared with the playback service's OCR. */
    private val ocrModels: OcrModels by lazy { OcrModels.get(applicationContext) }

    @Inject
    lateinit var licenceSource: LicenceSource

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AudoraTheme {
                val scope = rememberCoroutineScope()
                val diagnostics = remember { DiagnosticsController(readingSessionTracker, logSharer, scope) }
                // Activity-wide, so an add still running when the user opens Settings is not cancelled.
                val libraryController = remember { LibraryController(library, scope).also { it.start() } }
                val battery = rememberBatterySetup(scope)
                // Activity-wide too, so an import keeps going when the user leaves the screen; created on first visit.
                val voicesController = remember { lazy { VoicesController(voiceManager, scope).also { it.start() } } }
                var route by rememberSaveable { mutableStateOf(AppDestination.Library.toRoute()) }
                val destination = AppDestination.fromRoute(route)
                val navigate = { next: AppDestination -> route = next.toRoute() }
                // Not saveable on purpose: a restored activity must never start reading by itself (C7).
                var autoResumeBook by remember { mutableStateOf<BookId?>(null) }
                BackHandler(enabled = destination.parent != null) {
                    navigate(destination.parent ?: AppDestination.Library)
                }
                LaunchedEffect(Unit) { diagnostics.checkUnfinishedSession() }

                when (destination) {
                    AppDestination.Library -> LibraryRoute(libraryController, navigate) {
                        if (battery.showCard) {
                            BatterySetupCard(
                                onSetUp = { navigate(AppDestination.BatterySetup(fromSettings = false)) },
                                onNotNow = battery::dismissCard,
                            )
                        }
                    }

                    AppDestination.Settings -> SettingsRoute(diagnostics, battery, navigate)

                    AppDestination.Voices -> VoicesRoute(voicesController.value) { navigate(AppDestination.Settings) }

                    AppDestination.Licences -> LicencesRoute { navigate(AppDestination.Settings) }

                    is AppDestination.BatterySetup -> BatterySetupRoute(battery) {
                        navigate(destination.parent ?: AppDestination.Library)
                    }

                    is AppDestination.Reader -> {
                        ReaderRoute(destination.id, autoResume = autoResumeBook == destination.id) {
                            navigate(AppDestination.Library)
                        }
                        // The reader took the flag when it was created; opening the book later must not replay it.
                        LaunchedEffect(destination.id) { autoResumeBook = null }
                    }
                }
                diagnostics.unfinishedSession?.let { session ->
                    UnfinishedSessionDialog(
                        session = session,
                        onResume = {
                            diagnostics.resumeUnfinished { id ->
                                autoResumeBook = id
                                navigate(AppDestination.Reader(id))
                            }
                        },
                        onDismiss = diagnostics::dismissUnfinished,
                    )
                }
            }
        }
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
    ) {
        val ocrState by remember { ocrModels.state() }.collectAsStateWithLifecycle(initialValue = null)
        val scope = rememberCoroutineScope()
        val textPreferences = remember { ReadingTextPreferences.forContext(applicationContext) }
        // A one-line properties file, read once when Settings opens (like the onboarding flags). Saved off the main
        // thread, one write at a time and in order, so quick taps leave the file as the switch shows.
        var skipCitations by remember { mutableStateOf(textPreferences.skipCitationMarkers()) }
        val textWrites = remember { Dispatchers.IO.limitedParallelism(1) }
        SettingsScreen(
            shareState = diagnostics.shareState,
            batteryExempt = battery.exempt,
            versionName = BuildConfig.VERSION_NAME,
            showDebugTools = BuildConfig.DEBUG,
            onBack = { navigate(AppDestination.Library) },
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
        )
    }

    @Composable
    private fun LibraryRoute(
        controller: LibraryController,
        navigate: (AppDestination) -> Unit,
        banner: @Composable () -> Unit,
    ) {
        val actions = remember(controller) {
            object : LibraryActions {
                override fun onAddPdf(documentUri: Uri) = controller.add(documentUri.toString())

                override fun onLocate(id: BookId, documentUri: Uri) = controller.locate(id, documentUri.toString())

                override fun onOpenBook(book: Book) = navigate(AppDestination.Reader(book.id))

                override fun onRequestRemoval(book: Book) = controller.proposeRemoval(book)

                override fun onConfirmRemoval() = controller.confirmRemoval()

                override fun onCancelRemoval() = controller.proposeRemoval(null)

                override fun onMessageShown() = controller.messageShown()

                override fun onOpenSettings() = navigate(AppDestination.Settings)

                override fun onScreenResumed() = controller.refreshAvailability()
            }
        }
        LibraryScreen(
            listState = controller.listState,
            busy = controller.busy,
            message = controller.message,
            pendingRemoval = controller.pendingRemoval,
            actions = actions,
            banner = banner,
        )
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
    private fun ReaderRoute(id: BookId, autoResume: Boolean, onBack: () -> Unit) {
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
                autoResume = autoResume,
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
        val actions = remember(model) { readerActions(model, playWithPermission, onBack) }
        ReaderScreen(state = state, config = readerConfig, actions = actions, ocrModels = ocrState)
    }

    private fun readerActions(
        model: ReaderModel,
        playWithPermission: (() -> Unit) -> Unit,
        navigateBack: () -> Unit,
    ): ReaderActions = object : ReaderActions {
        override fun onBack() = navigateBack()

        override fun onPreviousPage() = model.previousPage()

        override fun onNextPage() = model.nextPage()

        override fun onGoToPage(page: Int) = model.showPage(page)

        override fun onShowReadingPosition() = model.showReadingPosition()

        override fun onSentenceTapped(unit: ReadingUnit) = playWithPermission { model.readFrom(unit) }

        // Asks for the notification permission only when the tap is on a sentence.
        override fun onPageTapped(point: ViewPoint) {
            val unit = model.sentenceAt(point) ?: return
            playWithPermission { model.readFrom(unit) }
        }

        override fun onViewChanged(view: ReaderView) = model.setView(view)

        override fun onPageWidth(widthPx: Int) = model.setPageWidth(widthPx)

        override fun onPlayPause() {
            if (model.state.value.isReading) model.playOrPause() else playWithPermission(model::playOrPause)
        }

        override fun onSkipBack() = model.skipBack()

        override fun onSkipForward() = model.skipForward()

        override fun onSpeed(speed: Float) = model.setSpeed(speed)

        override fun onSleepTimer(setting: SleepTimerSetting) = model.setSleepTimer(setting)

        override fun onPasswordSubmitted(password: String) = model.submitPassword(password)

        override fun onPasswordDismissed() = model.dismissPassword()

        override fun onAskForPassword() = model.askForPassword()

        override fun onRetry() = model.retry()

        override fun onDownloadOcrModels() = ocrModels.download()
    }
}
