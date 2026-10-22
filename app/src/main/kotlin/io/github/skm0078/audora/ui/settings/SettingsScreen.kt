// File: app/src/main/kotlin/io/github/skm0078/audora/ui/settings/SettingsScreen.kt
// Purpose: Settings tab: Voices (P4-01), OCR models (P3-05), citation numbers (P2-03), keep screen on, appearance
//          and app language (ADR-036, plan item 44), background reading (P1-06), Share logs (P1-07), kids mode
//          (plan item 71), Voices & licences (P3-06), debug tool.

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
import io.github.skm0078.audora.language.AppLanguage
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
    punctuationModel: OcrModelsState? = null,
    onPunctuationModel: (OcrModelsAction) -> Unit = {},
    volumeKeysSkip: Boolean = false,
    onVolumeKeysSkipChange: (Boolean) -> Unit = {},
    appearance: Appearance = Appearance(),
    onAppearanceChange: (Appearance) -> Unit = {},
    onKidsMode: () -> Unit = {},
    language: AppLanguage = AppLanguage.DEFAULT,
    onLanguageChange: (AppLanguage) -> Unit = {},
    onOpenStorage: () -> Unit = {},
    voiceCommands: VoiceCommandsSettings? = null,
    pronunciation: PronunciationSettings? = null,
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
        ListeningGroup(
            onOpenVoices,
            ModelPack(ocrModels, onOcrModels),
            ModelPack(punctuationModel, onPunctuationModel),
            skipCitations,
            onSkipCitationsChange,
        )
        SettingsGroup(stringResource(R.string.settings_group_reading)) {
            ReaderSwitches(keepScreenOn, onKeepScreenOnChange, volumeKeysSkip, onVolumeKeysSkipChange)
            voiceCommands?.let { VoiceCommandsItems(it) }
        }
        pronunciation?.let { PronunciationGroup(it) }
        AppearanceGroup(appearance, onAppearanceChange, language, onLanguageChange)
        AppGroup(
            AppLinks(onOpenBatterySetup, onShareLogs, onOpenLicences, onKidsMode, onOpenStorage),
            batteryExempt,
            shareState,
            versionName,
        )
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

/** What the App group's rows open. */
private class AppLinks(
    val onOpenBatterySetup: () -> Unit,
    val onShareLogs: () -> Unit,
    val onOpenLicences: () -> Unit,
    val onKidsMode: () -> Unit,
    val onOpenStorage: () -> Unit,
)

@Composable
private fun AppGroup(links: AppLinks, batteryExempt: Boolean, shareState: ShareLogsState, versionName: String) {
    SettingsGroup(stringResource(R.string.settings_group_app)) {
        BatteryItem(batteryExempt, links.onOpenBatterySetup)
        SettingsDivider()
        SettingsRow(
            stringResource(R.string.storage_title),
            summary = { Text(stringResource(R.string.settings_storage_body)) },
            onClick = links.onOpenStorage,
        )
        SettingsDivider()
        ShareLogsItem(shareState, links.onShareLogs)
        SettingsDivider()
        SettingsRow(
            stringResource(R.string.settings_kids_title),
            summary = { Text(stringResource(R.string.settings_kids_body)) },
            onClick = links.onKidsMode,
        )
        SettingsDivider()
        SettingsRow(
            stringResource(R.string.licences_title),
            summary = { Text(stringResource(R.string.settings_licences_body)) },
            onClick = links.onOpenLicences,
        )
        SettingsDivider()
        SettingsRow(stringResource(R.string.settings_version_title), summary = { Text(versionName) })
    }
}

/** The reader's own switches: keep the screen on, and volume keys that move by sentence (plan item 20). */
@Composable
private fun ReaderSwitches(
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
    volumeKeysSkip: Boolean,
    onVolumeKeysSkipChange: (Boolean) -> Unit,
) {
    SwitchItem(
        R.string.settings_keep_screen_on_title,
        R.string.settings_keep_screen_on_body,
        keepScreenOn,
        onKeepScreenOnChange,
    )
    SettingsDivider()
    SwitchItem(
        R.string.settings_volume_keys_title,
        R.string.settings_volume_keys_body,
        volumeKeysSkip,
        onVolumeKeysSkipChange,
    )
}

/** A downloadable model pack's state (null while it loads) and what its buttons do. */
private class ModelPack(val state: OcrModelsState?, val onAction: (OcrModelsAction) -> Unit)

/** A model pack's row with the divider before it; nothing while its state is still loading. */
@Composable
private fun ModelPackRow(pack: ModelPack, texts: ModelPackTexts) = ModelPackRow(pack.state, pack.onAction, texts)

/** A model pack's row with the divider before it, for rows inside a group; nothing while its state still loads. */
@Composable
internal fun ModelPackRow(state: OcrModelsState?, onAction: (OcrModelsAction) -> Unit, texts: ModelPackTexts) {
    state?.let {
        SettingsDivider()
        OcrModelsItem(it, onAction, texts)
    }
}

@Composable
private fun ListeningGroup(
    onOpenVoices: () -> Unit,
    ocr: ModelPack,
    punctuation: ModelPack,
    skipCitations: Boolean,
    onSkipCitationsChange: (Boolean) -> Unit,
) {
    SettingsGroup(stringResource(R.string.settings_group_listening)) {
        SettingsRow(
            stringResource(R.string.settings_voices_title),
            summary = { Text(stringResource(R.string.settings_voices_body)) },
            onClick = onOpenVoices,
        )
        ModelPackRow(ocr, ModelPackTexts.OCR)
        ModelPackRow(punctuation, ModelPackTexts.PUNCTUATION)
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
