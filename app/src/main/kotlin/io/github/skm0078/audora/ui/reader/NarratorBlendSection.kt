// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/NarratorBlendSection.kt
// Purpose: The speech sheet's narrator blend: another Kokoro voice mixed into the narrator's own (plan item 75).

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.model.VoiceShaping
import io.github.skm0078.audora.speech.sherpa.CastSpeakers
import kotlin.math.roundToInt

/**
 * A chip per Kokoro English speaker, Off first, and how much of it to mix in. Only Kokoro narrators change: the
 * setting is kept for the others and applies when a Kokoro voice reads. Each change reloads the voice, so the
 * slider applies when the thumb is let go.
 */
@Composable
fun NarratorBlendSection(shaping: VoiceShaping, onChange: (VoiceShaping) -> Unit) {
    Text(stringResource(R.string.speech_blend), style = MaterialTheme.typography.titleMedium)
    Text(
        stringResource(R.string.speech_blend_detail),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = shaping.blendWith == null,
            onClick = { onChange(shaping.copy(blendWith = null)) },
            label = { Text(stringResource(R.string.speech_blend_off)) },
        )
        CastSpeakers.kokoroEnglish.forEach { speaker ->
            FilterChip(
                selected = shaping.blendWith == speaker.speakerId,
                onClick = { onChange(shaping.copy(blendWith = speaker.speakerId)) },
                label = { Text(speaker.label) },
            )
        }
    }
    if (shaping.blendWith != null) BlendAmountSlider(shaping.blendPercent) { onChange(shaping.copy(blendPercent = it)) }
}

@Composable
private fun BlendAmountSlider(percent: Int, onChange: (Int) -> Unit) {
    var dragged by remember(percent) { mutableFloatStateOf(percent.toFloat()) }
    val range = VoiceShaping.BLEND_PERCENTS
    Text(stringResource(R.string.speech_blend_amount, dragged.roundToInt()), style = MaterialTheme.typography.bodyLarge)
    Slider(
        value = dragged,
        onValueChange = { dragged = it },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        onValueChangeFinished = { dragged.roundToInt().takeIf { it != percent }?.let(onChange) },
    )
}
