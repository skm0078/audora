// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReaderControls.kt
// Purpose: The reader's bottom bar: the Voice and speed chip, previous/next sentence, play/pause and the sleep timer,
//          with shortcuts into the Voice and speed sheet under them (ADR 2026-10-29-voice-and-speed-one-level).

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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
    speech: SpeechRequest = SpeechRequest.NONE,
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
            // Which place in the Voice and speed sheet is open, if it is: the chip opens its top, a shortcut its group.
            var speechAt by rememberSaveable { mutableStateOf<SpeechSpot?>(null) }
            // A search result or Settings asked for a place in the sheet: opened once, then handed back.
            LaunchedEffect(speech.spot) {
                speech.spot?.let {
                    speechAt = it
                    speech.onTaken()
                }
            }
            ControlsRow(state, config, actions, voices) { speechAt = SpeechSpot.TOP }
            // Not while the first-run tips show: both under the page would leave it too short on small screens
            // (an emulator's 640 dp: the snippet picker's Share button had no room). They appear after Got it.
            if (onTipsSeen == null) SpeechShortcuts(enabled = state.playerReady, onOpen = { speechAt = it })
            speechAt?.let { spot -> ControlsSpeechSheet(spot, state, config, actions, voices) { speechAt = null } }
        }
    }
}

@Composable
private fun ControlsSpeechSheet(
    spot: SpeechSpot,
    state: ReaderState,
    config: ReaderConfig,
    actions: ReaderActions,
    voices: List<VoiceRow>,
    onDismiss: () -> Unit,
) {
    val study = StudyControls(state.study, canMark = state.readingPosition != null, onStudy = actions::onStudy)
    PlayerSpeechSheet(
        spot = spot,
        playback = state.playback,
        config = config,
        actions = actions,
        voices = voices,
        study = study,
        bookInPlayer = state.readingPosition != null,
        bookLang = bookLanguage(state.content),
        onDismiss = onDismiss,
    )
}

/** The open page's language, so a Hindi or Bengali book's voices come first in the speech sheet. */
private fun bookLanguage(content: PageContent): Lang? = (content as? PageContent.Ready)?.units?.let(::pageLanguage)

@Composable
private fun ControlsRow(
    state: ReaderState,
    config: ReaderConfig,
    actions: ReaderActions,
    voices: List<VoiceRow>,
    onSpeech: () -> Unit,
) {
    val enabled = state.playerReady
    val loaded = state.readingPosition != null
    // Full width, or SpaceBetween has no room to spread into and the row huddles at the start (seen on a phone).
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.SpaceS, vertical = Dimens.SpaceM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val playback = state.playback
        // Kept across the playback ticks that redraw this row: it changes only with the page, voices or picks.
        val lang = remember(state.content) { bookLanguage(state.content) }
        val own = playback.bookVoices.takeIf { playback.ownSettings }.orEmpty()
        val voiceName = remember(voices, own, lang) { lang?.let { readingVoiceName(voices, own, it) } }
        SpeechChip(playback.speed, voiceName, enabled, onSpeech)
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
