// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/PitchBlock.kt
// Purpose: The speech sheet's pitch control (ADR-067): a slider in steps with - and +, its value in words, and a
//          one-tap way back to the voice's own pitch.

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.SpeechStyleConfig
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.ui.theme.OptionBlock
import kotlin.math.roundToInt

/**
 * Pitch (ADR-067): deeper or higher than the voice's own, in steps; the value says how far, in percent, and Back to the
 * voice's own is one tap. The slider follows the service's pitch, and shows the finger's position while dragging.
 */
@Composable
internal fun PitchBlock(
    pitch: Float,
    speech: SpeechStyleConfig,
    onPitch: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val range = speech.minPitch..speech.maxPitch
    val step = speech.pitchStep
    var dragged by remember(pitch) { mutableFloatStateOf(pitch) }
    val label = pitchLabel(speech.clampPitch(dragged))
    OptionBlock(
        stringResource(R.string.speech_pitch_title),
        modifier = modifier,
        value = label,
        help = stringResource(R.string.speech_pitch_help),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onPitch(speech.clampPitch(pitch - step)) }, enabled = pitch > range.start) {
                Icon(painterResource(R.drawable.ic_remove), stringResource(R.string.speech_pitch_down))
            }
            val spoken = stringResource(R.string.speech_pitch_spoken, label)
            Slider(
                value = dragged,
                onValueChange = { dragged = it },
                onValueChangeFinished = { onPitch(speech.clampPitch(dragged)) },
                valueRange = range,
                steps = stepsBetween(range, step),
                // TalkBack reads the pitch ("Pitch: 10% higher"), not the thumb's share of the track.
                modifier = Modifier.weight(1f).semantics { stateDescription = spoken },
            )
            IconButton(onClick = { onPitch(speech.clampPitch(pitch + step)) }, enabled = pitch < range.endInclusive) {
                Icon(painterResource(R.drawable.ic_add), stringResource(R.string.speech_pitch_up))
            }
        }
        FilterChip(
            selected = pitch == SpeechStyle.NORMAL_PITCH,
            onClick = { onPitch(SpeechStyle.NORMAL_PITCH) },
            label = { Text(stringResource(R.string.speech_pitch_reset)) },
        )
    }
}

/** "The voice's own", "10% higher" or "15% deeper". */
@Composable
internal fun pitchLabel(pitch: Float): String {
    val percent = ((pitch - SpeechStyle.NORMAL_PITCH) * PERCENT).roundToInt()
    return when {
        percent > 0 -> stringResource(R.string.speech_pitch_higher, percent)
        percent < 0 -> stringResource(R.string.speech_pitch_lower, -percent)
        else -> stringResource(R.string.speech_pitch_own)
    }
}

private const val PERCENT = 100
