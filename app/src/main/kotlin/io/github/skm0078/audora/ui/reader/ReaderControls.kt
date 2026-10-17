// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReaderControls.kt
// Purpose: The reader's bottom bar: speed and pace, previous/next sentence, play/pause and the sleep timer.

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.ReaderConfig
import kotlinx.coroutines.delay

@Composable
fun ReaderControls(state: ReaderState, config: ReaderConfig, actions: ReaderActions, modifier: Modifier = Modifier) {
    val enabled = state.playerReady
    val loaded = state.readingPosition != null
    Surface(tonalElevation = 3.dp, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SpeedButton(state.playback, config, enabled, actions)
            IconButton(onClick = actions::onSkipBack, enabled = enabled && loaded && state.playback.canSkipBack) {
                Icon(painterResource(R.drawable.ic_skip_previous), stringResource(R.string.reader_skip_back))
            }
            PlayPauseButton(
                reading = state.isReading,
                buffering = state.isReading && state.playback.buffering,
                enabled = enabled,
                onClick = actions::onPlayPause,
            )
            IconButton(onClick = actions::onSkipForward, enabled = enabled && loaded && state.playback.canSkipForward) {
                Icon(painterResource(R.drawable.ic_skip_next), stringResource(R.string.reader_skip_forward))
            }
            SleepTimerButton(state.playback, config, enabled, actions)
        }
    }
}

@Composable
private fun PlayPauseButton(reading: Boolean, buffering: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        if (buffering) CircularProgressIndicator(Modifier.size(PLAY_BUTTON_SIZE))
        FilledIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(PLAY_BUTTON_SIZE - 8.dp)) {
            if (reading) {
                Icon(painterResource(R.drawable.ic_pause), stringResource(R.string.reader_pause))
            } else {
                Icon(painterResource(R.drawable.ic_play), stringResource(R.string.reader_play))
            }
        }
    }
}

@Composable
private fun SpeedButton(playback: PlaybackSnapshot, config: ReaderConfig, enabled: Boolean, actions: ReaderActions) {
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
            onPace = actions::onPace,
            clausePauses = playback.clausePauses,
            onClausePauses = actions::onClausePauses,
            readingModes = playback.readingModes,
            onReadingModes = actions::onReadingModes,
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
    var open by remember { mutableStateOf(false) }
    var editingOptions by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, enabled = enabled) {
            Icon(painterResource(R.drawable.ic_timer), stringResource(R.string.reader_sleep_timer))
            sleepTimerLabel(playback.sleepTimer)?.let { label ->
                Spacer(Modifier.width(4.dp))
                Text(label)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val choose = { setting: SleepTimerSetting ->
                open = false
                actions.onSleepTimer(setting)
            }
            sleepTimerChoices(playback.hasChapters, config).forEach { setting ->
                DropdownMenuItem(text = { Text(settingLabel(setting)) }, onClick = { choose(setting) })
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sleep_options_menu)) },
                onClick = {
                    open = false
                    editingOptions = true
                },
            )
        }
    }
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

@Composable
private fun settingLabel(setting: SleepTimerSetting): String = when (setting) {
    SleepTimerSetting.Off -> stringResource(R.string.reader_sleep_off)
    is SleepTimerSetting.AfterMinutes -> stringResource(R.string.reader_sleep_minutes, setting.minutes)
    SleepTimerSetting.EndOfPage -> stringResource(R.string.reader_sleep_end_of_page)
    SleepTimerSetting.EndOfChapter -> stringResource(R.string.reader_sleep_end_of_chapter)
    is SleepTimerSetting.AfterChapters -> stringResource(R.string.reader_sleep_chapters, setting.count)
}

/** What the timer button shows next to its icon; null when off. */
@Composable
private fun sleepTimerLabel(timer: SleepTimerState): String? = when (timer) {
    SleepTimerState.Off -> null

    is SleepTimerState.Counting -> {
        val remaining by produceState(timer.endsAtMillis - System.currentTimeMillis(), timer) {
            while (value > 0) {
                delay(TICK_MS)
                value = timer.endsAtMillis - System.currentTimeMillis()
            }
        }
        remainingLabel(remaining)
    }

    SleepTimerState.EndOfPage -> stringResource(R.string.reader_sleep_end_of_page)

    SleepTimerState.EndOfChapter -> stringResource(R.string.reader_sleep_end_of_chapter)

    is SleepTimerState.AfterChapters -> stringResource(R.string.reader_sleep_chapters, timer.count)
}

private val PLAY_BUTTON_SIZE = 64.dp
private const val TICK_MS = 1_000L
