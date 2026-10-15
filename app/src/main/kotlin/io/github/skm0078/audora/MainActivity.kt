// File: app/src/main/kotlin/io/github/skm0078/audora/MainActivity.kt
// Purpose: Single activity hosting the Compose UI, edge-to-edge from day one (Build Spec §4).

package io.github.skm0078.audora

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dagger.hilt.android.AndroidEntryPoint
import io.github.skm0078.audora.core.diagnostics.ReadingSessionTracker
import io.github.skm0078.audora.diagnostics.LogSharer
import io.github.skm0078.audora.ui.PlaceholderScreen
import io.github.skm0078.audora.ui.diagnostics.DiagnosticsController
import io.github.skm0078.audora.ui.diagnostics.UnfinishedSessionDialog
import io.github.skm0078.audora.ui.pdfcheck.PdfCheckController
import io.github.skm0078.audora.ui.pdfcheck.PdfCheckSection
import io.github.skm0078.audora.ui.settings.SettingsScreen
import io.github.skm0078.audora.ui.theme.AudoraTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var readingSessionTracker: ReadingSessionTracker

    @Inject
    lateinit var logSharer: LogSharer

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AudoraTheme {
                val context = LocalContext.current.applicationContext
                val scope = rememberCoroutineScope()
                val diagnostics = remember { DiagnosticsController(readingSessionTracker, logSharer, scope) }
                val pdfCheck = remember { PdfCheckController(context, scope) }
                // Until P1-01 brings real navigation: two screens, Settings closes with back.
                var showSettings by rememberSaveable { mutableStateOf(false) }
                BackHandler(enabled = showSettings) { showSettings = false }
                LaunchedEffect(Unit) { diagnostics.checkUnfinishedSession() }

                if (showSettings) {
                    SettingsScreen(
                        shareState = diagnostics.shareState,
                        showDebugTools = BuildConfig.DEBUG,
                        onBack = { showSettings = false },
                        onShareLogs = { diagnostics.shareLogs { intent -> startActivity(intent) } },
                        onSimulateReadingSession = {
                            diagnostics.simulateReadingSession()
                            Toast.makeText(this@MainActivity, R.string.debug_simulate_done, Toast.LENGTH_LONG).show()
                        },
                    )
                } else {
                    HomeScreen(pdfCheck = pdfCheck, onOpenSettings = { showSettings = true })
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(pdfCheck: PdfCheckController, onOpenSettings: () -> Unit) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.settings_title))
                    }
                },
            )
        },
    ) { innerPadding ->
        PlaceholderScreen(
            versionName = BuildConfig.VERSION_NAME,
            modifier = Modifier.padding(innerPadding),
        ) {
            PdfCheckSection(
                state = pdfCheck.state,
                onPdfPicked = pdfCheck::open,
                onPasswordSubmitted = pdfCheck::submitPassword,
                onPasswordCancelled = pdfCheck::cancelPassword,
            )
        }
    }
}
