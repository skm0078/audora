// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/SleepOptionsDialog.kt
// Purpose: The sleep timer extras (plan item 37): shake to extend, fade-out and the bedtime auto-on window.

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.core.model.SleepOptions
import io.github.skm0078.audora.ui.theme.switchRow
import java.util.Locale

/** Edits a copy of [options]; Save sends it to the service, which keeps it across restarts. */
@Composable
fun SleepOptionsDialog(
    options: SleepOptions,
    config: ReaderConfig,
    onSave: (SleepOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(options) { mutableStateOf(options) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sleep_options_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SwitchRow(stringResource(R.string.sleep_options_shake), draft.shakeToExtend) {
                    draft = draft.copy(shakeToExtend = it)
                }
                SwitchRow(stringResource(R.string.sleep_options_fade), draft.fadeOut) {
                    draft = draft.copy(fadeOut = it)
                }
                SwitchRow(stringResource(R.string.sleep_options_bedtime), draft.bedtime) {
                    draft = draft.copy(bedtime = it)
                }
                if (draft.bedtime) BedtimeSection(draft, config) { draft = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }) { Text(stringResource(R.string.sleep_options_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.password_cancel)) } },
    )
}

@Composable
private fun BedtimeSection(draft: SleepOptions, config: ReaderConfig, onChange: (SleepOptions) -> Unit) {
    val step = config.bedtimeStepMinutes
    MinuteRow(stringResource(R.string.sleep_options_from), draft.bedtimeFromMinute, step) {
        onChange(draft.copy(bedtimeFromMinute = it))
    }
    MinuteRow(stringResource(R.string.sleep_options_to), draft.bedtimeToMinute, step) {
        onChange(draft.copy(bedtimeToMinute = it))
    }
    Text(
        stringResource(R.string.sleep_options_bedtime_timer),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        config.sleepMinutes.forEach { minutes ->
            FilterChip(
                selected = draft.bedtimeMinutes == minutes,
                onClick = { onChange(draft.copy(bedtimeMinutes = minutes)) },
                label = { Text(stringResource(R.string.reader_sleep_minutes, minutes)) },
            )
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.switchRow(checked, onChange = onChange), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun MinuteRow(label: String, minuteOfDay: Int, step: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        val earlier = stringResource(R.string.sleep_options_earlier, label, step)
        val later = stringResource(R.string.sleep_options_later, label, step)
        TextButton(
            onClick = { onChange(stepMinute(minuteOfDay, -step)) },
            modifier = Modifier.semantics { contentDescription = earlier },
        ) { Text("−") }
        // Announced as it changes, so a TalkBack listener hears the new time after each tap.
        Text(clockText(minuteOfDay), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        TextButton(
            onClick = { onChange(stepMinute(minuteOfDay, step)) },
            modifier = Modifier.semantics { contentDescription = later },
        ) { Text("+") }
    }
}

/** [minuteOfDay] moved by [by] minutes, wrapping around midnight. */
internal fun stepMinute(minuteOfDay: Int, by: Int): Int = Math.floorMod(minuteOfDay + by, SleepOptions.MINUTES_PER_DAY)

/** 24-hour "HH:MM", the same in every language. */
internal fun clockText(minuteOfDay: Int): String =
    String.format(Locale.ROOT, "%02d:%02d", minuteOfDay / MINUTES_PER_HOUR, minuteOfDay % MINUTES_PER_HOUR)

private const val MINUTES_PER_HOUR = 60
