// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/VoiceShapingSection.kt
// Purpose: The speech sheet's sound section: even loudness, voice boost, pause trim (items 9, 10), expressiveness (8).

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.SpeechStyleConfig
import io.github.skm0078.audora.core.config.VoiceShapingConfig
import io.github.skm0078.audora.core.model.VoiceShaping
import kotlin.math.roundToInt

/** Each change applies from the next sentence rendered; the sentence playing restarts like a speed change. */
@Composable
fun VoiceShapingSection(shaping: VoiceShaping, config: VoiceShapingConfig, onChange: (VoiceShaping) -> Unit) {
    Text(stringResource(R.string.speech_sound), style = MaterialTheme.typography.titleMedium)
    ShapingSwitch(
        stringResource(R.string.speech_even_loudness),
        stringResource(R.string.speech_even_loudness_detail),
        shaping.evenLoudness,
    ) { onChange(shaping.copy(evenLoudness = it)) }
    ShapingSwitch(
        stringResource(R.string.speech_trim_pauses),
        stringResource(R.string.speech_trim_pauses_detail),
        shaping.trimPauses,
    ) { onChange(shaping.copy(trimPauses = it)) }
    ShapingSwitch(
        stringResource(R.string.speech_smart_speed),
        stringResource(R.string.speech_smart_speed_detail, SMART_SPEED_FROM),
        shaping.smartSpeed,
    ) { onChange(shaping.copy(smartSpeed = it)) }
    Text(stringResource(R.string.speech_boost), style = MaterialTheme.typography.bodyLarge)
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        config.boostChoicesDb.forEach { db ->
            FilterChip(
                selected = shaping.boostDb == db,
                onClick = { onChange(shaping.copy(boostDb = db)) },
                label = { Text(boostLabel(db)) },
            )
        }
    }
    ExpressivenessSlider(shaping.expressiveness, config.maxExpression) { onChange(shaping.copy(expressiveness = it)) }
}

/**
 * Newsreader to storyteller in whole steps (plan item 8). Applied when the thumb is let go, not while dragging: each
 * new step reloads the voice.
 */
@Composable
private fun ExpressivenessSlider(step: Int, maxStep: Int, onChange: (Int) -> Unit) {
    var dragged by remember(step) { mutableFloatStateOf(step.toFloat()) }
    Text(stringResource(R.string.speech_expressiveness), style = MaterialTheme.typography.bodyLarge)
    Text(
        stringResource(R.string.speech_expressiveness_detail),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Slider(
        value = dragged,
        onValueChange = { dragged = it },
        valueRange = 0f..maxStep.toFloat(),
        steps = (maxStep - 1).coerceAtLeast(0),
        onValueChangeFinished = { dragged.roundToInt().takeIf { it != step }?.let(onChange) },
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.speech_expressiveness_newsreader), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.speech_expressiveness_storyteller), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ShapingSwitch(title: String, detail: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = on, onCheckedChange = onChange)
    }
}

@Composable
private fun boostLabel(db: Int): String =
    if (db == 0) stringResource(R.string.speech_boost_off) else stringResource(R.string.speech_boost_db, db)

/** Where smart speed starts to act: the voice model's own top speed (ADR-033, 049). */
private val SMART_SPEED_FROM = speedLabel(SpeechStyleConfig().maxModelRate)
