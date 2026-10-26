// File: app/src/main/kotlin/io/github/skm0078/audora/ui/settings/SettingsScreen.kt
// Purpose: Settings tab: Voices (P4-01), OCR models (P3-05), citation numbers (P2-03), keep screen on, appearance
//          and app language (ADR-036, plan item 44), background reading (P1-06), kids mode (plan item 71), licences
//          (P3-06); privacy and security (ADR-061) and Advanced (ADR-056: punctuation, volume keys, voice commands,
//          pronunciation, Share logs (P1-07), the debug tool) each on a page of their own (ADR-062).

package io.github.skm0078.audora.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.language.AppLanguage
import io.github.skm0078.audora.privacy.PrivacyControls
import io.github.skm0078.audora.speech.ocr.OcrModelsState
import io.github.skm0078.audora.ui.diagnostics.ShareLogsState
import io.github.skm0078.audora.ui.theme.Appearance
import io.github.skm0078.audora.ui.theme.AudoraTheme
import io.github.skm0078.audora.ui.theme.Dimens
import io.github.skm0078.audora.ui.theme.LinkOption
import io.github.skm0078.audora.ui.theme.OptionDivider
import io.github.skm0078.audora.ui.theme.OptionGroup
import io.github.skm0078.audora.ui.theme.SwitchOption
import io.github.skm0078.audora.ui.theme.optionRowColors

/**
 * The Settings tab (ADR-036, ADR-062): what most listeners change, in groups of rows that each show their control and
 * current value; Privacy and Advanced open as pages of their own, and Back returns from them.
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
    storyBrain: StoryBrainSettings? = null,
    privacy: PrivacyControls? = null,
) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.MAIN) }
    BackHandler(enabled = page != SettingsPage.MAIN) { page = SettingsPage.MAIN }
    // Each page opens at its top, not where the last one was scrolled to.
    key(page) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
                .padding(bottom = Dimens.SpaceXl),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL),
        ) {
            PageHeader(page) { page = SettingsPage.MAIN }
            when (page) {
                SettingsPage.MAIN -> {
                    ListeningGroup(
                        onOpenVoices,
                        ModelPack(ocrModels, onOcrModels),
                        actedVoice?.let { ModelPack(it.state, it.onAction) },
                        storyBrain?.let { ModelPack(it.state, it.onAction) },
                        skipCitations,
                        onSkipCitationsChange,
                    )
                    AppearanceGroup(appearance, onAppearanceChange, language, onLanguageChange, keepScreenOn) {
                        onKeepScreenOnChange(it)
                    }
                    AppGroup(
                        AppLinks(onOpenBatterySetup, onOpenLicences, onKidsMode, onOpenStorage),
                        batteryExempt,
                        versionName,
                    )
                    PagesGroup { page = it }
                }

                SettingsPage.PRIVACY -> PrivacyGroup(privacy)

                SettingsPage.ADVANCED -> AdvancedPage(
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
    }
}

/** The tab's own page and the two it opens. */
private enum class SettingsPage { MAIN, PRIVACY, ADVANCED }

/** The rows that open Settings' other pages. */
@Composable
private fun PagesGroup(onOpen: (SettingsPage) -> Unit) {
    OptionGroup(title = null) {
        LinkOption(
            stringResource(R.string.settings_group_privacy),
            onClick = { onOpen(SettingsPage.PRIVACY) },
            subtitle = stringResource(R.string.settings_privacy_short),
        )
        OptionDivider()
        LinkOption(
            stringResource(R.string.settings_advanced_title),
            onClick = { onOpen(SettingsPage.ADVANCED) },
            subtitle = stringResource(R.string.settings_advanced_summary),
        )
    }
}

/** "Settings" on the main page; a back arrow and the page's name on the others. */
@Composable
private fun PageHeader(page: SettingsPage, onBack: () -> Unit) {
    val title = when (page) {
        SettingsPage.MAIN -> R.string.settings_title
        SettingsPage.PRIVACY -> R.string.settings_group_privacy
        SettingsPage.ADVANCED -> R.string.settings_advanced_title
    }
    Row(Modifier.padding(top = Dimens.SpaceL), verticalAlignment = Alignment.CenterVertically) {
        if (page != SettingsPage.MAIN) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
            }
        }
        Text(
            stringResource(title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/**
 * Options few people change wait on their own page, so Settings opens on what most listeners need (ADR-056).
 * [onSimulateReadingSession] is null outside debug builds, and its group is then left out.
 */
@Composable
private fun AdvancedPage(
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
    OptionGroup(stringResource(R.string.settings_group_advanced_reading)) {
        SwitchOption(
            stringResource(R.string.settings_volume_keys_title),
            volumeKeysSkip,
            onVolumeKeysSkipChange,
            help = stringResource(R.string.settings_volume_keys_body),
        )
        voiceCommands?.let { VoiceCommandsItems(it) }
        // It only touches scanned pages, so it says so while scanned pages cannot be read yet.
        val texts = if (ocrReady) ModelPackTexts.PUNCTUATION else ModelPackTexts.PUNCTUATION_NEEDS_OCR
        ModelPackRow(punctuation, texts)
    }
    pronunciation?.let { PronunciationGroup(it) }
    OptionGroup(stringResource(R.string.settings_group_help)) { ShareLogsItem(shareState, onShareLogs) }
    onSimulateReadingSession?.let { simulate ->
        OptionGroup(stringResource(R.string.settings_group_debug)) {
            LinkOption(
                stringResource(R.string.debug_simulate_title),
                onClick = simulate,
                help = stringResource(R.string.debug_simulate_body),
            )
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
    OptionGroup(stringResource(R.string.settings_group_app)) {
        LinkOption(
            stringResource(R.string.settings_battery_title),
            onClick = links.onOpenBatterySetup,
            subtitle = stringResource(
                if (batteryExempt) R.string.settings_battery_allowed else R.string.settings_battery_limited,
            ),
            subtitleIsError = !batteryExempt,
        )
        OptionDivider()
        LinkOption(
            stringResource(R.string.storage_title),
            onClick = links.onOpenStorage,
            subtitle = stringResource(R.string.settings_storage_body),
        )
        OptionDivider()
        KidsModeItem(links.onKidsMode)
        OptionDivider()
        LinkOption(stringResource(R.string.licences_title), onClick = links.onOpenLicences)
        OptionDivider()
        LinkOption(stringResource(R.string.settings_version_title), onClick = null, value = versionName)
    }
}

/** A downloadable model pack's state (null while it loads) and what its buttons do. */
private class ModelPack(val state: OcrModelsState?, val onAction: (OcrModelsAction) -> Unit)

/** A model pack's row with the divider before it; nothing while its state is still loading. */
@Composable
private fun ModelPackRow(pack: ModelPack, texts: ModelPackTexts) = ModelPackRow(pack.state, pack.onAction, texts)

/** A model pack's row with the divider before it, for rows inside a group; nothing while its state still loads. */
@Composable
internal fun ModelPackRow(
    state: OcrModelsState?,
    onAction: (OcrModelsAction) -> Unit,
    texts: ModelPackTexts,
    divider: Boolean = true,
) {
    state?.let {
        if (divider) OptionDivider()
        OcrModelsItem(it, onAction, texts)
    }
}

@Composable
private fun ListeningGroup(
    onOpenVoices: () -> Unit,
    ocr: ModelPack,
    acted: ModelPack?,
    brain: ModelPack?,
    skipCitations: Boolean,
    onSkipCitationsChange: (Boolean) -> Unit,
) {
    OptionGroup(stringResource(R.string.settings_group_listening)) {
        LinkOption(
            stringResource(R.string.settings_voices_title),
            onClick = onOpenVoices,
            subtitle = stringResource(R.string.settings_voices_body),
        )
        // Studio mode's slow Hindi voice (ADR-059), next to the voices it stands in for.
        acted?.let { ModelPackRow(it, ModelPackTexts.ACTED) }
        // Who speaks each line and how (ADR story-brain): for every voice, so next to them too.
        brain?.let { ModelPackRow(it, ModelPackTexts.STORY_BRAIN) }
        ModelPackRow(ocr, ModelPackTexts.OCR)
        OptionDivider()
        SwitchOption(
            stringResource(R.string.settings_skip_citations_title),
            skipCitations,
            onSkipCitationsChange,
            subtitle = stringResource(R.string.settings_skip_citations_short),
            help = stringResource(R.string.settings_skip_citations_body),
        )
        OptionDivider()
        ResumeOnReconnectItem()
    }
}

@Composable
private fun ShareLogsItem(state: ShareLogsState, onShareLogs: () -> Unit) {
    val preparing = state == ShareLogsState.Preparing
    if (preparing) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.share_logs_title)) },
            trailingContent = { CircularProgressIndicator(modifier = Modifier.size(PROGRESS_SIZE)) },
            colors = optionRowColors(),
        )
        return
    }
    val failed = state == ShareLogsState.Failed
    LinkOption(
        stringResource(R.string.share_logs_title),
        onClick = onShareLogs,
        subtitle = stringResource(if (failed) R.string.share_logs_failed else R.string.share_logs_body_hint),
        subtitleIsError = failed,
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
