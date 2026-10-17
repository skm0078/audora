// File: app/src/main/kotlin/io/github/skm0078/audora/ui/settings/SettingsScreen.kt
// Purpose: Settings: Voices (P4-01), Voices & licences (P3-06), OCR models (P3-05), citation numbers (P2-03),
//          background reading (P1-06), Share logs (P1-07), debug tool.

package io.github.skm0078.audora.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.speech.ocr.OcrModelsState
import io.github.skm0078.audora.ui.diagnostics.ShareLogsState
import io.github.skm0078.audora.ui.theme.AudoraTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    shareState: ShareLogsState,
    batteryExempt: Boolean,
    versionName: String,
    showDebugTools: Boolean,
    onBack: () -> Unit,
    onOpenBatterySetup: () -> Unit,
    onOpenVoices: () -> Unit,
    onOpenLicences: () -> Unit,
    onShareLogs: () -> Unit,
    onSimulateReadingSession: () -> Unit,
    modifier: Modifier = Modifier,
    ocrModels: OcrModelsState? = null,
    onOcrModels: (OcrModelsAction) -> Unit = {},
    skipCitations: Boolean = true,
    onSkipCitationsChange: (Boolean) -> Unit = {},
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
            ListItem(
                headlineContent = { Text(stringResource(R.string.licences_title)) },
                supportingContent = { Text(stringResource(R.string.settings_licences_body)) },
                modifier = Modifier.clickable(onClick = onOpenLicences),
            )
            HorizontalDivider()
            ocrModels?.let {
                OcrModelsItem(it, onOcrModels)
                HorizontalDivider()
            }
            SkipCitationsItem(skipCitations, onSkipCitationsChange)
            HorizontalDivider()
            BatteryItem(batteryExempt, onOpenBatterySetup)
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

/** The whole row toggles; the switch only shows the state (no onCheckedChange), so it is not a second target. */
@Composable
private fun SkipCitationsItem(skip: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_skip_citations_title)) },
        supportingContent = { Text(stringResource(R.string.settings_skip_citations_body)) },
        trailingContent = { Switch(checked = skip, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = skip, role = Role.Switch, onValueChange = onChange),
    )
}

@Composable
private fun BatteryItem(exempt: Boolean, onOpen: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_battery_title)) },
        supportingContent = {
            if (exempt) {
                Text(stringResource(R.string.settings_battery_allowed))
            } else {
                Text(stringResource(R.string.settings_battery_limited), color = MaterialTheme.colorScheme.error)
            }
        },
        modifier = Modifier.clickable(onClick = onOpen),
    )
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
            batteryExempt = false,
            versionName = "0.0.1-debug",
            showDebugTools = true,
            onBack = {},
            onOpenBatterySetup = {},
            onOpenVoices = {},
            onOpenLicences = {},
            onShareLogs = {},
            onSimulateReadingSession = {},
            skipCitations = true,
        )
    }
}
