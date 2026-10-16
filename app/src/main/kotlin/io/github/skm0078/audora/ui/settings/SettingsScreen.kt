// File: app/src/main/kotlin/io/github/skm0078/audora/ui/settings/SettingsScreen.kt
// Purpose: Settings: Voices (P4-01), Share logs (P1-07), the app version, and a debug-only session-detection tool.

package io.github.skm0078.audora.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.diagnostics.ShareLogsState
import io.github.skm0078.audora.ui.theme.AudoraTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    shareState: ShareLogsState,
    versionName: String,
    showDebugTools: Boolean,
    onBack: () -> Unit,
    onOpenVoices: () -> Unit,
    onShareLogs: () -> Unit,
    onSimulateReadingSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).verticalScroll(rememberScrollState())) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_voices_title)) },
                supportingContent = { Text(stringResource(R.string.settings_voices_body)) },
                modifier = Modifier.clickable(onClick = onOpenVoices),
            )
            HorizontalDivider()
            ShareLogsItem(shareState, onShareLogs)
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_version_title)) },
                supportingContent = { Text(versionName) },
            )
            if (showDebugTools) {
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.debug_simulate_title)) },
                    supportingContent = { Text(stringResource(R.string.debug_simulate_body)) },
                    overlineContent = { Text(stringResource(R.string.debug_tools_label)) },
                    modifier = Modifier.clickable(onClick = onSimulateReadingSession),
                )
            }
        }
    }
}

@Composable
private fun ShareLogsItem(state: ShareLogsState, onShareLogs: () -> Unit) {
    val preparing = state == ShareLogsState.Preparing
    val progress: (@Composable () -> Unit)? = if (preparing) {
        { CircularProgressIndicator(modifier = Modifier.size(PROGRESS_SIZE)) }
    } else {
        null
    }
    ListItem(
        headlineContent = { Text(stringResource(R.string.share_logs_title)) },
        supportingContent = {
            if (state == ShareLogsState.Failed) {
                Text(stringResource(R.string.share_logs_failed), color = MaterialTheme.colorScheme.error)
            } else {
                Text(stringResource(R.string.share_logs_body_hint))
            }
        },
        trailingContent = progress,
        modifier = Modifier.clickable(enabled = !preparing, onClick = onShareLogs),
    )
}

private val PROGRESS_SIZE = 24.dp

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    AudoraTheme {
        SettingsScreen(
            shareState = ShareLogsState.Idle,
            versionName = "0.0.1-debug",
            showDebugTools = true,
            onBack = {},
            onOpenVoices = {},
            onShareLogs = {},
            onSimulateReadingSession = {},
        )
    }
}
