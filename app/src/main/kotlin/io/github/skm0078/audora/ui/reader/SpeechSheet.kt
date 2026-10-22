// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/SpeechSheet.kt
// Purpose: The player's speech sheet: speed, pauses, sound, cast voices (item 7) and what to read (ADR-035).

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.core.model.FocusSoundOptions
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.Pace
import io.github.skm0078.audora.core.model.ReadingModes
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.model.VoiceShaping
import io.github.skm0078.audora.speech.voices.VoiceRow
import kotlin.math.roundToInt

/**
 * The slider only sends its speed when the finger lifts: every change restarts the sentence playing at the new
 * speed (ADR-033), so sending while dragging would restart it many times a second.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeechSheet(
    speed: Float,
    pace: Pace,
    config: ReaderConfig,
    onSpeed: (Float) -> Unit,
    onPace: (Pace) -> Unit,
    onDismiss: () -> Unit,
    clausePauses: Boolean = true,
    onClausePauses: (Boolean) -> Unit = {},
    readingModes: ReadingModes = ReadingModes(),
    onReadingModes: (ReadingModes) -> Unit = {},
    voiceShaping: VoiceShaping = VoiceShaping(),
    onVoiceShaping: (VoiceShaping) -> Unit = {},
    ownSettings: Boolean = false,
    onOwnSettings: (Boolean) -> Unit = {},
    voices: BookVoices = BookVoices(),
    study: StudyControls = StudyControls(),
    focusSound: FocusSoundOptions = FocusSoundOptions(),
    onFocusSound: (FocusSoundOptions) -> Unit = {},
    blend: NarratorBlendState = NarratorBlendState(),
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OwnSettingsRow(ownSettings, onOwnSettings)
            if (ownSettings) BookVoicesSection(voices.rows, voices.chosen, voices.onVoice)
            SpeedSection(speed, config, onSpeed)
            PaceSection(pace, onPace)
            ClausePausesRow(clausePauses, onClausePauses)
            VoiceShapingSection(voiceShaping, config.voiceShaping, onVoiceShaping)
            blend.ownSpeaker?.let { own ->
                NarratorBlendSection(voiceShaping, own, blend.neededBytes, onVoiceShaping)
            }
            CastVoicesSection(voices.rows, voiceShaping, onVoiceShaping)
            ReadingModesSection(readingModes, onReadingModes)
            StudySection(readingModes, study, onReadingModes)
            FocusSoundSection(focusSound, onFocusSound)
            Text(
                stringResource(R.string.speech_sheet_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SpeedSection(speed: Float, config: ReaderConfig, onSpeed: (Float) -> Unit) {
    val range = config.speech.minRate..config.speech.maxRate
    val step = config.speech.rateStep
    // Follows the player's speed, but shows the finger's position while dragging.
    var dragged by remember(speed) { mutableFloatStateOf(speed) }
    Text(
        stringResource(R.string.speech_sheet_speed, speedLabel(config.nearestSpeed(dragged))),
        style = MaterialTheme.typography.titleMedium,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onSpeed(config.nearestSpeed(speed - step)) }, enabled = speed > range.start) {
            Icon(painterResource(R.drawable.ic_remove), stringResource(R.string.speech_sheet_slower))
        }
        Slider(
            value = dragged,
            onValueChange = { dragged = it },
            onValueChangeFinished = { onSpeed(config.nearestSpeed(dragged)) },
            valueRange = range,
            steps = stepsBetween(range, step),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onSpeed(config.nearestSpeed(speed + step)) }, enabled = speed < range.endInclusive) {
            Icon(painterResource(R.drawable.ic_add), stringResource(R.string.speech_sheet_faster))
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        config.speeds.forEach { preset ->
            FilterChip(
                selected = preset == speed,
                onClick = { onSpeed(preset) },
                label = { Text("${speedLabel(preset)}×") },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaceSection(pace: Pace, onPace: (Pace) -> Unit) {
    Text(stringResource(R.string.speech_sheet_pace), style = MaterialTheme.typography.titleMedium)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        Pace.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == pace,
                onClick = { onPace(option) },
                shape = SegmentedButtonDefaults.itemShape(index, Pace.entries.size),
            ) { Text(stringResource(paceLabel(option))) }
        }
    }
}

/** A switch so the listener can hear the sentence with and without the clause pauses (RI-6). */
@Composable
private fun ClausePausesRow(on: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.speech_clause_pauses), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.speech_clause_pauses_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = on, onCheckedChange = onChange)
    }
}

/** Plan item 4: speed, pace and voices become this book's own; off, it follows the settings of every book. */
@Composable
private fun OwnSettingsRow(on: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.speech_own_settings), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.speech_own_settings_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = on, onCheckedChange = onChange)
    }
}

private fun paceLabel(pace: Pace): Int = when (pace) {
    Pace.TIGHT -> R.string.speech_pace_tight
    Pace.NORMAL -> R.string.speech_pace_normal
    Pace.RELAXED -> R.string.speech_pace_relaxed
}

/** Material's Slider counts the stops between the ends, not the intervals. */
internal fun stepsBetween(range: ClosedFloatingPointRange<Float>, step: Float): Int =
    (((range.endInclusive - range.start) / step).roundToInt() - 1).coerceAtLeast(0)

/** The voices the open book can pick from (plan item 4), its own choices, and what a tap does. */
class BookVoices(
    val rows: List<VoiceRow> = emptyList(),
    val chosen: Map<Lang, VoiceId> = emptyMap(),
    val onVoice: (Lang, VoiceId) -> Unit = { _, _ -> },
)
