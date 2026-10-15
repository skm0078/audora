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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.android.AndroidEntryPoint
import io.github.skm0078.audora.core.diagnostics.ReadingSessionTracker
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.Library
import io.github.skm0078.audora.diagnostics.LogSharer
import io.github.skm0078.audora.ui.AppDestination
import io.github.skm0078.audora.ui.book.BookController
import io.github.skm0078.audora.ui.book.BookScreen
import io.github.skm0078.audora.ui.diagnostics.DiagnosticsController
import io.github.skm0078.audora.ui.diagnostics.UnfinishedSessionDialog
import io.github.skm0078.audora.ui.library.LibraryActions
import io.github.skm0078.audora.ui.library.LibraryController
import io.github.skm0078.audora.ui.library.LibraryScreen
import io.github.skm0078.audora.ui.settings.SettingsScreen
import io.github.skm0078.audora.ui.theme.AudoraTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var readingSessionTracker: ReadingSessionTracker

    @Inject
    lateinit var logSharer: LogSharer

    @Inject
    lateinit var library: Library

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AudoraTheme {
                val scope = rememberCoroutineScope()
                val diagnostics = remember { DiagnosticsController(readingSessionTracker, logSharer, scope) }
                // Activity-wide, so an add still running when the user opens Settings is not cancelled.
                val libraryController = remember { LibraryController(library, scope).also { it.start() } }
                var route by rememberSaveable { mutableStateOf(AppDestination.Library.toRoute()) }
                val destination = AppDestination.fromRoute(route)
                val navigate = { next: AppDestination -> route = next.toRoute() }
                BackHandler(enabled = destination != AppDestination.Library) { navigate(AppDestination.Library) }
                LaunchedEffect(Unit) { diagnostics.checkUnfinishedSession() }

                when (destination) {
                    AppDestination.Library -> LibraryRoute(libraryController, navigate)

                    AppDestination.Settings -> SettingsScreen(
                        shareState = diagnostics.shareState,
                        versionName = BuildConfig.VERSION_NAME,
                        showDebugTools = BuildConfig.DEBUG,
                        onBack = { navigate(AppDestination.Library) },
                        onShareLogs = { diagnostics.shareLogs { intent -> startActivity(intent) } },
                        onSimulateReadingSession = {
                            diagnostics.simulateReadingSession()
                            Toast.makeText(this@MainActivity, R.string.debug_simulate_done, Toast.LENGTH_LONG).show()
                        },
                    )

                    is AppDestination.BookDetail -> BookRoute(destination.id) { navigate(AppDestination.Library) }
                }
                diagnostics.unfinishedSession?.let { session ->
                    UnfinishedSessionDialog(
                        session = session,
                        onResume = diagnostics::resumeUnfinished,
                        onDismiss = diagnostics::dismissUnfinished,
                    )
                }
            }
        }
    }

    @Composable
    private fun LibraryRoute(controller: LibraryController, navigate: (AppDestination) -> Unit) {
        val actions = remember(controller) {
            object : LibraryActions {
                override fun onAddPdf(documentUri: Uri) = controller.add(documentUri.toString())

                override fun onLocate(id: BookId, documentUri: Uri) = controller.locate(id, documentUri.toString())

                override fun onOpenBook(book: Book) = navigate(AppDestination.BookDetail(book.id))

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
        )
    }

    @Composable
    private fun BookRoute(id: BookId, onBack: () -> Unit) {
        val context = LocalContext.current.applicationContext
        val scope = rememberCoroutineScope()
        val controller = remember(id) { BookController(context, library, scope, id).also { it.start() } }
        BookScreen(
            lookup = controller.lookup,
            checkState = controller.check.state,
            onPasswordSubmitted = controller.check::submitPassword,
            onPasswordCancelled = controller.check::cancelPassword,
            onRetry = controller.check::retry,
            onBack = onBack,
        )
    }
}
