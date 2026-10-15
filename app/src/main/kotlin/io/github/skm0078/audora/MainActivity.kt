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
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.resume.ResumeLocator
import io.github.skm0078.audora.diagnostics.LogSharer
import io.github.skm0078.audora.ui.AppDestination
import io.github.skm0078.audora.ui.battery.BatterySetupCard
import io.github.skm0078.audora.ui.battery.BatterySetupController
import io.github.skm0078.audora.ui.battery.BatterySetupScreen
import io.github.skm0078.audora.ui.diagnostics.DiagnosticsController
import io.github.skm0078.audora.ui.diagnostics.UnfinishedSessionDialog
import io.github.skm0078.audora.ui.library.LibraryActions
import io.github.skm0078.audora.ui.library.LibraryController
import io.github.skm0078.audora.ui.library.LibraryScreen
import io.github.skm0078.audora.ui.reader.LibraryReaderBooks
import io.github.skm0078.audora.ui.reader.LocatorReaderResume
import io.github.skm0078.audora.ui.reader.MediaControllerPlaybackPort
import io.github.skm0078.audora.ui.reader.ReaderActions
import io.github.skm0078.audora.ui.reader.ReaderDocumentOpener
import io.github.skm0078.audora.ui.reader.ReaderModel
import io.github.skm0078.audora.ui.reader.ReaderScreen
import io.github.skm0078.audora.ui.reader.SpeedPreference
import io.github.skm0078.audora.ui.reader.rememberPlayWithNotificationPermission
import io.github.skm0078.audora.ui.settings.SettingsScreen
import io.github.skm0078.audora.ui.theme.AudoraTheme
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

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
    lateinit var documentOpener: ReaderDocumentOpener

    @Inject
    lateinit var resumeLocator: ResumeLocator

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
                var route by rememberSaveable { mutableStateOf(AppDestination.Library.toRoute()) }
                val destination = AppDestination.fromRoute(route)
                val navigate = { next: AppDestination -> route = next.toRoute() }
                // Not saveable on purpose: a restored activity must never start reading by itself (C7).
                var autoResumeBook by remember { mutableStateOf<BookId?>(null) }
                BackHandler(enabled = destination != AppDestination.Library) { navigate(parentOf(destination)) }
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

                    is AppDestination.BatterySetup -> BatterySetupScreen(
                        exempt = battery.exempt,
                        brand = battery.brand,
                        steps = battery.steps,
                        onAllow = { battery.requestExemption(::startActivity) },
                        onOpenAppSettings = { battery.openAppSettings(::startActivity) },
                        onBack = { navigate(parentOf(destination)) },
                    )

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

    private fun parentOf(destination: AppDestination): AppDestination = when {
        destination is AppDestination.BatterySetup && destination.fromSettings -> AppDestination.Settings
        else -> AppDestination.Library
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
    private fun SettingsRoute(
        diagnostics: DiagnosticsController,
        battery: BatterySetupController,
        navigate: (AppDestination) -> Unit,
    ) {
        SettingsScreen(
            shareState = diagnostics.shareState,
            batteryExempt = battery.exempt,
            versionName = BuildConfig.VERSION_NAME,
            showDebugTools = BuildConfig.DEBUG,
            onBack = { navigate(AppDestination.Library) },
            onOpenBatterySetup = { navigate(AppDestination.BatterySetup(fromSettings = true)) },
            onShareLogs = { diagnostics.shareLogs { intent -> startActivity(intent) } },
            onSimulateReadingSession = {
                diagnostics.simulateReadingSession()
                Toast.makeText(this@MainActivity, R.string.debug_simulate_done, Toast.LENGTH_LONG).show()
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
            ).also { it.start() }
        }
        // Connected only while visible: a controller left connected keeps the playback service bound.
        LifecycleStartEffect(port) {
            port.connect()
            onStopOrDispose { port.release() }
        }
        DisposableEffect(model) { onDispose { model.close() } }
        val state by model.state.collectAsStateWithLifecycle()
        val playWithPermission = rememberPlayWithNotificationPermission()
        val actions = remember(model) { readerActions(model, playWithPermission, onBack) }
        ReaderScreen(state = state, config = readerConfig, actions = actions)
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
    }
}
