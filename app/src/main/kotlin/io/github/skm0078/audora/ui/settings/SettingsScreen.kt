// File: app/src/main/kotlin/io/github/skm0078/audora/ui/settings/SettingsScreen.kt
// Purpose: Settings tab: Voices (P4-01), OCR models (P3-05), citation numbers (P2-03), keep screen on, appearance
//          (ADR-036), background reading (P1-06), Share logs (P1-07), Voices & licences (P3-06), debug tool.

package io.github.skm0078.audora.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.speech.ocr.OcrModelsState
import io.github.skm0078.audora.ui.diagnostics.ShareLogsState
import io.github.skm0078.audora.ui.theme.Appearance
import io.github.skm0078.audora.ui.theme.AudoraTheme
import io.github.skm0078.audora.ui.theme.Dimens

/**
 * The Settings tab (ADR-036): rows grouped as Listening, Reading, Appearance and App, each group a card. Rows keep
 * their own composables, so a new setting is one more row in its group.
 */
@Composable
fun SettingsScreen(
    shareState: ShareLogsState,
    batteryExempt: Boolean,
    versionName: String,
    showDebugTools: Boolean,
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
    keepScreenOn: Boolean = false,
    onKeepScreenOnChange: (Boolean) -> Unit = {},
    appearance: Appearance = Appearance(),
    onAppearanceChange: (Appearance) -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.ScreenPadding)
            .padding(bottom = Dimens.SpaceXl),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXl),
    ) {
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = Dimens.SpaceL).semantics { heading() },
        )
        ListeningGroup(onOpenVoices, ocrModels, onOcrModels, skipCitations, onSkipCitationsChange)
        SettingsGroup(stringResource(R.string.settings_group_reading)) {
            SwitchItem(
                R.string.settings_keep_screen_on_title,
                R.string.settings_keep_screen_on_body,
                keepScreenOn,
                onKeepScreenOnChange,
            )
        }
        AppearanceGroup(appearance, onAppearanceChange)
        SettingsGroup(stringResource(R.string.settings_group_app)) {
            BatteryItem(batteryExempt, onOpenBatterySetup)
            SettingsDivider()
            ShareLogsItem(shareState, onShareLogs)
            SettingsDivider()
            SettingsRow(
                stringResource(R.string.licences_title),
                summary = { Text(stringResource(R.string.settings_licences_body)) },
                onClick = onOpenLicences,
            )
            SettingsDivider()
            SettingsRow(stringResource(R.string.settings_version_title), summary = { Text(versionName) })
        }
        if (showDebugTools) {
            SettingsGroup(stringResource(R.string.settings_group_debug)) {
                SettingsRow(
                    stringResource(R.string.debug_simulate_title),
                    summary = { Text(stringResource(R.string.debug_simulate_body)) },
                    onClick = onSimulateReadingSession,
                )
            }
        }
    }
}

@Composable
private fun ListeningGroup(
    onOpenVoices: () -> Unit,
    ocrModels: OcrModelsState?,
    onOcrModels: (OcrModelsAction) -> Unit,
    skipCitations: Boolean,
    onSkipCitationsChange: (Boolean) -> Unit,
) {
    SettingsGroup(stringResource(R.string.settings_group_listening)) {
        SettingsRow(
            stringResource(R.string.settings_voices_title),
            summary = { Text(stringResource(R.string.settings_voices_body)) },
            onClick = onOpenVoices,
        )
        ocrModels?.let {
            SettingsDivider()
            OcrModelsItem(it, onOcrModels)
        }
        SettingsDivider()
        SwitchItem(
            R.string.settings_skip_citations_title,
            R.string.settings_skip_citations_body,
            skipCitations,
            onSkipCitationsChange,
        )
    }
}

/** The whole row toggles; the switch only shows the state (no onCheckedChange), so it is not a second target. */
@Composable
internal fun SwitchItem(@StringRes title: Int, @StringRes body: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(body)) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = settingsRowColors(),
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
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
        colors = settingsRowColors(),
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
        colors = settingsRowColors(),
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
            onOpenBatterySetup = {},
            onOpenVoices = {},
            onOpenLicences = {},
            onShareLogs = {},
            onSimulateReadingSession = {},
            skipCitations = true,
        )
    }
}
