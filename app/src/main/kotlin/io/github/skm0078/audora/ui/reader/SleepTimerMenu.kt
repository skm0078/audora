// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/SleepTimerMenu.kt
// Purpose: The sleep timer's button and menu, shared by the book player and the saved-audio player (ADR
//          2026-10-28-made-tab), so both show the same choices, the same time left and say it the same way.

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.theme.Dimens
import kotlinx.coroutines.delay

/**
 * The timer icon with the time left beside it while one runs; a tap opens [choices]. [onOptions], when given, adds
 * "Sleep options…" at the end (the book player's fade and shake settings).
 */
@Composable
internal fun SleepTimerMenuButton(
    timer: SleepTimerState,
    choices: List<SleepTimerSetting>,
    enabled: Boolean,
    onChoose: (SleepTimerSetting) -> Unit,
    onOptions: (() -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val text = sleepTimerText(timer)
    Box {
        // TalkBack hears the time left in words, once a minute, not a ticking "14:59".
        TextButton(
            onClick = { open = true },
            enabled = enabled,
            modifier = Modifier.semantics { stateDescription = text.spoken },
        ) {
            Icon(painterResource(R.drawable.ic_timer), stringResource(R.string.reader_sleep_timer))
            text.shown?.let { label ->
                Spacer(Modifier.width(Dimens.SpaceXs))
                Text(label, Modifier.clearAndSetSemantics {})
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { setting ->
                DropdownMenuItem(
                    text = { Text(settingLabel(setting)) },
                    onClick = {
                        open = false
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        onChoose(setting)
                    },
                )
            }
            onOptions?.let { options ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sleep_options_menu)) },
                    onClick = {
                        open = false
                        options()
                    },
                )
            }
        }
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

/** What the timer button shows next to its icon (null when off), and what TalkBack says for it. */
private data class SleepTimerText(val shown: String?, val spoken: String)

@Composable
private fun sleepTimerText(timer: SleepTimerState): SleepTimerText = when (timer) {
    SleepTimerState.Off -> SleepTimerText(null, stringResource(R.string.reader_sleep_off))

    is SleepTimerState.Counting -> {
        val remaining by produceState(timer.endsAtMillis - System.currentTimeMillis(), timer) {
            while (value > 0) {
                delay(TICK_MS)
                value = timer.endsAtMillis - System.currentTimeMillis()
            }
        }
        val minutes = minutesLeft(remaining)
        SleepTimerText(
            remainingLabel(remaining),
            pluralStringResource(R.plurals.a11y_sleep_minutes_left, minutes, minutes),
        )
    }

    SleepTimerState.EndOfPage -> stringResource(R.string.reader_sleep_end_of_page).let { SleepTimerText(it, it) }

    SleepTimerState.EndOfChapter -> stringResource(R.string.reader_sleep_end_of_chapter).let { SleepTimerText(it, it) }

    is SleepTimerState.AfterChapters ->
        stringResource(R.string.reader_sleep_chapters, timer.count).let { SleepTimerText(it, it) }
}

private const val TICK_MS = 1_000L
