// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReaderControls.kt
// Purpose: The reader's bottom bar: speed, previous/next sentence, play/pause and the sleep timer.

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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
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
            SpeedButton(state.playback.speed, config, enabled, actions::onSpeed)
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
            SleepTimerButton(
                state.playback.sleepTimer,
                state.playback.hasChapters,
                config,
                enabled,
                actions::onSleepTimer,
            )
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
private fun SpeedButton(speed: Float, config: ReaderConfig, enabled: Boolean, onSpeed: (Float) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, enabled = enabled) {
            Text(stringResource(R.string.reader_speed, speedLabel(speed)))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            config.speeds.forEach { option ->
                DropdownMenuItem(
                    text = {
                        val color = if (option == speed) MaterialTheme.colorScheme.primary else Color.Unspecified
                        Text("${speedLabel(option)}×", color = color)
                    },
                    onClick = {
                        open = false
                        onSpeed(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun SleepTimerButton(
    timer: SleepTimerState,
    hasChapters: Boolean,
    config: ReaderConfig,
    enabled: Boolean,
    onSleepTimer: (SleepTimerSetting) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, enabled = enabled) {
            Icon(painterResource(R.drawable.ic_timer), stringResource(R.string.reader_sleep_timer))
            sleepTimerLabel(timer)?.let { label ->
                Spacer(Modifier.width(4.dp))
                Text(label)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val choose = { setting: SleepTimerSetting ->
                open = false
                onSleepTimer(setting)
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reader_sleep_off)) },
                onClick = { choose(SleepTimerSetting.Off) },
            )
            config.sleepMinutes.forEach { minutes ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.reader_sleep_minutes, minutes)) },
                    onClick = { choose(SleepTimerSetting.AfterMinutes(minutes)) },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reader_sleep_end_of_page)) },
                onClick = { choose(SleepTimerSetting.EndOfPage) },
            )
            if (hasChapters) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.reader_sleep_end_of_chapter)) },
                    onClick = { choose(SleepTimerSetting.EndOfChapter) },
                )
            }
        }
    }
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
}

private val PLAY_BUTTON_SIZE = 64.dp
private const val TICK_MS = 1_000L
