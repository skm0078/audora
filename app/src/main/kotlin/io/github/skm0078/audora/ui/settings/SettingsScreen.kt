// File: app/src/main/kotlin/io/github/skm0078/audora/ui/settings/SettingsScreen.kt
// Purpose: Settings tab: Voices (P4-01), OCR models (P3-05), citation numbers (P2-03), keep screen on, appearance
//          and app language (ADR-036, plan item 44), background reading (P1-06), kids mode (plan item 71), licences
//          (P3-06); under a closed "Advanced" (ADR-056): punctuation, volume keys, voice commands, pronunciation,
//          Share logs (P1-07) and the debug tool.

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
import io.github.skm0078.audora.ui.theme.MoreOptions

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
    actedVoice: ActedVoiceSettings? = null,
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
            actedVoice?.let { ModelPack(it.state, it.onAction) },
            skipCitations,
            onSkipCitationsChange,
        )
        SettingsGroup(stringResource(R.string.settings_group_reading)) {
            SwitchItem(
                R.string.settings_keep_screen_on_title,
                R.string.settings_keep_screen_on_body,
                keepScreenOn,
                onKeepScreenOnChange,
            )
        }
        AppearanceGroup(appearance, onAppearanceChange, language, onLanguageChange)
        AppGroup(
            AppLinks(onOpenBatterySetup, onOpenLicences, onKidsMode, onOpenStorage),
            batteryExempt,
            versionName,
        )
        AdvancedSection(
            ModelPack(punctuationModel, onPunctuationModel),
            ocrReady = ocrModels is OcrModelsState.Ready,
            volumeKeysSkip,
            onVolumeKeysSkipChange,
            voiceCommands,
            pronunciation,
            shareState,
            onShareLogs,
            onSimulateReadingSession.takeIf { showDebugTools },
        )
    }
}

/**
 * Options few people change wait here, closed, so the screen opens on what most listeners need (ADR-056).
 * [onSimulateReadingSession] is null outside debug builds, and its group is then left out.
 */
@Composable
private fun AdvancedSection(
    punctuation: ModelPack,
    ocrReady: Boolean,
    volumeKeysSkip: Boolean,
    onVolumeKeysSkipChange: (Boolean) -> Unit,
    voiceCommands: VoiceCommandsSettings?,
    pronunciation: PronunciationSettings?,
    shareState: ShareLogsState,
    onShareLogs: () -> Unit,
    onSimulateReadingSession: (() -> Unit)?,
) {
    MoreOptions(
        stringResource(R.string.settings_advanced_title),
        stringResource(R.string.settings_advanced_summary),
        headerPadding = Dimens.SpaceXs,
    ) {
        SettingsGroup(stringResource(R.string.settings_group_advanced_reading)) {
            // It only touches scanned pages, so it says so while scanned pages cannot be read yet.
            val texts = if (ocrReady) ModelPackTexts.PUNCTUATION else ModelPackTexts.PUNCTUATION_NEEDS_OCR
            ModelPackRow(punctuation, texts, divider = false)
            if (punctuation.state != null) SettingsDivider()
            SwitchItem(
                R.string.settings_volume_keys_title,
                R.string.settings_volume_keys_body,
                volumeKeysSkip,
                onVolumeKeysSkipChange,
            )
            voiceCommands?.let { VoiceCommandsItems(it) }
        }
        pronunciation?.let { PronunciationGroup(it) }
        SettingsGroup(stringResource(R.string.settings_group_help)) { ShareLogsItem(shareState, onShareLogs) }
        onSimulateReadingSession?.let { simulate ->
            SettingsGroup(stringResource(R.string.settings_group_debug)) {
                SettingsRow(
                    stringResource(R.string.debug_simulate_title),
                    summary = { Text(stringResource(R.string.debug_simulate_body)) },
                    onClick = simulate,
                )
            }
        }
    }
}

/** What the App group's rows open. */
private class AppLinks(
    val onOpenBatterySetup: () -> Unit,
    val onOpenLicences: () -> Unit,
    val onKidsMode: () -> Unit,
    val onOpenStorage: () -> Unit,
)

@Composable
private fun AppGroup(links: AppLinks, batteryExempt: Boolean, versionName: String) {
    SettingsGroup(stringResource(R.string.settings_group_app)) {
        BatteryItem(batteryExempt, links.onOpenBatterySetup)
        SettingsDivider()
        SettingsRow(
            stringResource(R.string.storage_title),
            summary = { Text(stringResource(R.string.settings_storage_body)) },
            onClick = links.onOpenStorage,
        )
        SettingsDivider()
        KidsModeItem(links.onKidsMode)
        SettingsDivider()
        SettingsRow(
            stringResource(R.string.licences_title),
            summary = { Text(stringResource(R.string.settings_licences_body)) },
            onClick = links.onOpenLicences,
        )
        SettingsDivider()
        PrivacyPolicyItem()
        SettingsDivider()
        SettingsRow(stringResource(R.string.settings_version_title), summary = { Text(versionName) })
    }
}

/** A downloadable model pack's state (null while it loads) and what its buttons do. */
private class ModelPack(val state: OcrModelsState?, val onAction: (OcrModelsAction) -> Unit)

/** A model pack's row with the divider before it; nothing while its state is still loading. */
@Composable
private fun ModelPackRow(pack: ModelPack, texts: ModelPackTexts, divider: Boolean = true) =
    ModelPackRow(pack.state, pack.onAction, texts, divider)

/** A model pack's row with the divider before it, for rows inside a group; nothing while its state still loads. */
@Composable
internal fun ModelPackRow(
    state: OcrModelsState?,
    onAction: (OcrModelsAction) -> Unit,
    texts: ModelPackTexts,
    divider: Boolean = true,
) {
    state?.let {
        if (divider) SettingsDivider()
        OcrModelsItem(it, onAction, texts)
    }
}

@Composable
private fun ListeningGroup(
    onOpenVoices: () -> Unit,
    ocr: ModelPack,
    acted: ModelPack?,
    skipCitations: Boolean,
    onSkipCitationsChange: (Boolean) -> Unit,
) {
    SettingsGroup(stringResource(R.string.settings_group_listening)) {
        SettingsRow(
            stringResource(R.string.settings_voices_title),
            summary = { Text(stringResource(R.string.settings_voices_body)) },
            onClick = onOpenVoices,
        )
        // Studio mode's slow Hindi voice (ADR-059), next to the voices it stands in for.
        acted?.let { ModelPackRow(it, ModelPackTexts.ACTED) }
        ModelPackRow(ocr, ModelPackTexts.OCR)
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
