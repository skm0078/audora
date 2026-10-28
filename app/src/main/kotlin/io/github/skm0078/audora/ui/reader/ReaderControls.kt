// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReaderControls.kt
// Purpose: The reader's bottom bar: speed and pace, previous/next sentence, play/pause and the sleep timer.

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.speech.voices.VoiceRow
import io.github.skm0078.audora.ui.theme.Dimens

@Composable
fun ReaderControls(
    state: ReaderState,
    config: ReaderConfig,
    actions: ReaderActions,
    modifier: Modifier = Modifier,
    voices: List<VoiceRow> = emptyList(),
    onTipsSeen: (() -> Unit)? = null,
) {
    // A raised panel with rounded top corners, so the controls read as one block under the thumb (ADR-036).
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = Dimens.SpaceXl, topEnd = Dimens.SpaceXl),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.navigationBarsPadding()) {
            // The tips show here, near the buttons they name, when the reader's notice slot picks them.
            onTipsSeen?.let {
                GestureTips(it, Modifier.padding(start = Dimens.SpaceM, end = Dimens.SpaceM, top = Dimens.SpaceM))
            }
            ControlsRow(state, config, actions, voices)
        }
    }
}

@Composable
private fun ControlsRow(state: ReaderState, config: ReaderConfig, actions: ReaderActions, voices: List<VoiceRow>) {
    val enabled = state.playerReady
    val loaded = state.readingPosition != null
    // Full width, or SpaceBetween has no room to spread into and the row huddles at the start (seen on a phone).
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.SpaceS, vertical = Dimens.SpaceM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val study = StudyControls(state.study, canMark = state.readingPosition != null, onStudy = actions::onStudy)
        // The open page's language, so a Hindi or Bengali book's voices come first in the speech sheet.
        val bookLang = (state.content as? PageContent.Ready)?.units?.let(::pageLanguage)
        SpeedButton(state.playback, config, enabled, actions, voices, study, state.readingPosition != null, bookLang)
        SkipButton(
            forward = false,
            enabled = enabled && loaded && state.playback.canSkipBack,
            onClick = actions::onSkipBack,
            onLongClick = { actions.onSkipParagraph(forward = false) },
        )
        PlayPauseButton(
            reading = state.isReading,
            buffering = state.isReading && state.playback.buffering,
            enabled = enabled,
            onClick = actions::onPlayPause,
        )
        SkipButton(
            forward = true,
            enabled = enabled && loaded && state.playback.canSkipForward,
            onClick = actions::onSkipForward,
            onLongClick = { actions.onSkipParagraph(forward = true) },
        )
        SleepTimerButton(state.playback, config, enabled, actions)
    }
}

/**
 * Previous / next sentence; a long-press skips a paragraph (the player says when there is none that way). Drawn like
 * an IconButton, which has no long-press, with the same touch target and disabled look.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SkipButton(forward: Boolean, enabled: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val icon = if (forward) R.drawable.ic_skip_next else R.drawable.ic_skip_previous
    val label = stringResource(if (forward) R.string.reader_skip_forward else R.string.reader_skip_back)
    val longLabel = stringResource(if (forward) R.string.reader_paragraph_forward else R.string.reader_paragraph_back)
    val tint = LocalContentColor.current.let { if (enabled) it else it.copy(alpha = DISABLED_ALPHA) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .combinedClickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = label,
                onLongClickLabel = longLabel,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
    ) {
        Icon(painterResource(icon), contentDescription = label, tint = tint)
    }
}

@Composable
private fun PlayPauseButton(reading: Boolean, buffering: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val loading = stringResource(R.string.a11y_loading)
    Box(contentAlignment = Alignment.Center) {
        if (buffering) CircularProgressIndicator(Modifier.size(PLAY_BUTTON_SIZE))
        FilledIconButton(
            onClick = onClick,
            enabled = enabled,
            // The spinner says "getting ready" to the eye; TalkBack says it on the button.
            modifier = Modifier
                .size(PLAY_BUTTON_SIZE - Dimens.SpaceS)
                .semantics { if (buffering) stateDescription = loading },
        ) {
            if (reading) {
                Icon(painterResource(R.drawable.ic_pause), stringResource(R.string.reader_pause))
            } else {
                Icon(painterResource(R.drawable.ic_play), stringResource(R.string.reader_play))
            }
        }
    }
}

@Composable
private fun SpeedButton(
    playback: PlaybackSnapshot,
    config: ReaderConfig,
    enabled: Boolean,
    actions: ReaderActions,
    voices: List<VoiceRow>,
    study: StudyControls,
    bookInPlayer: Boolean,
    bookLang: Lang?,
) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }, enabled = enabled) {
        Text(stringResource(R.string.reader_speed, speedLabel(playback.speed)))
    }
    if (open) {
        SpeechSheet(
            speed = playback.speed,
            pace = playback.pace,
            config = config,
            onSpeed = actions::onSpeed,
            pitch = playback.pitch,
            onPitch = actions::onPitch,
            onPace = actions::onPace,
            clausePauses = playback.clausePauses,
            onClausePauses = actions::onClausePauses,
            readingModes = playback.readingModes,
            onReadingModes = actions::onReadingModes,
            voiceShaping = playback.voiceShaping,
            onVoiceShaping = actions::onVoiceShaping,
            ownSettings = playback.ownSettings,
            onOwnSettings = actions::onOwnSettings,
            voices = BookVoices(voices, playback.bookVoices, actions::onBookVoice, actions::onVoice, bookLang),
            study = study,
            focusSound = playback.focusSound,
            onFocusSound = actions::onFocusSound,
            blend = NarratorBlendState(playback.blendOwnSpeaker, playback.blendNeedsBytes),
            bookInPlayer = bookInPlayer,
            expressive = playback.voiceExpressive != false,
            readingIntent = playback.readingIntent,
            onDismiss = { open = false },
        )
    }
}

@Composable
private fun SleepTimerButton(
    playback: PlaybackSnapshot,
    config: ReaderConfig,
    enabled: Boolean,
    actions: ReaderActions,
) {
    var editingOptions by remember { mutableStateOf(false) }
    SleepTimerMenuButton(
        timer = playback.sleepTimer,
        choices = sleepTimerChoices(playback.hasChapters, config),
        enabled = enabled,
        onChoose = actions::onSleepTimer,
        onOptions = { editingOptions = true },
    )
    if (editingOptions) {
        SleepOptionsDialog(
            options = playback.sleepOptions,
            config = config,
            onSave = {
                editingOptions = false
                actions.onSleepOptions(it)
            },
            onDismiss = { editingOptions = false },
        )
    }
}

/** The menu's timer choices in order; chapter choices only for books with chapters. */
internal fun sleepTimerChoices(hasChapters: Boolean, config: ReaderConfig): List<SleepTimerSetting> = buildList {
    add(SleepTimerSetting.Off)
    config.sleepMinutes.forEach { add(SleepTimerSetting.AfterMinutes(it)) }
    add(SleepTimerSetting.EndOfPage)
    if (hasChapters) {
        add(SleepTimerSetting.EndOfChapter)
        config.sleepChapterCounts.forEach { add(SleepTimerSetting.AfterChapters(it)) }
    }
}

private val PLAY_BUTTON_SIZE = Dimens.PlayButton

/** Material 3's disabled-content alpha, as an IconButton draws it. */
private const val DISABLED_ALPHA = 0.38f
