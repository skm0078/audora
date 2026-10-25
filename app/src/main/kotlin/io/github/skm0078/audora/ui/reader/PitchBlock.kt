// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/PitchBlock.kt
// Purpose: The speech sheet's pitch control (ADR-067, ADR-070), built like speed's: named presets with Natural in the
//          middle, a fine slider with - and + that is heard while it moves, and the value in words.

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
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
import io.github.skm0078.audora.ui.theme.Dimens
import io.github.skm0078.audora.ui.theme.OptionBlock
import kotlin.math.roundToInt

/**
 * Pitch (ADR-070): a preset row like speed's (Deeper, A bit deeper, Natural, A bit higher, Higher), then a slider
 * for the steps between. A pitch change restarts nothing, so the slider sends each new step while the finger moves
 * and the listener hears it at once; the range stays where a shifted voice still sounds like itself. Natural is in
 * the middle of the track and one tap away.
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
    // Follows the service's pitch; while dragging, the finger's step, sent once per step.
    var dragged by remember(pitch) { mutableFloatStateOf(pitch) }
    val label = pitchLabel(dragged)
    OptionBlock(
        stringResource(R.string.speech_pitch_title),
        modifier = modifier,
        value = label,
        subtitle = stringResource(R.string.speech_pitch_hint),
        help = stringResource(R.string.speech_pitch_help),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onPitch(speech.clampPitch(pitch - step)) }, enabled = pitch > range.start) {
                Icon(painterResource(R.drawable.ic_remove), stringResource(R.string.speech_pitch_down))
            }
            val spoken = stringResource(R.string.speech_pitch_spoken, label)
            Slider(
                value = dragged,
                onValueChange = { moved ->
                    val snapped = speech.clampPitch(moved)
                    if (snapped != dragged) {
                        dragged = snapped
                        onPitch(snapped)
                    }
                },
                valueRange = range,
                steps = stepsBetween(range, step),
                // TalkBack reads the pitch ("Pitch: 10% higher"), not the thumb's share of the track.
                modifier = Modifier.weight(1f).semantics { stateDescription = spoken },
            )
            IconButton(onClick = { onPitch(speech.clampPitch(pitch + step)) }, enabled = pitch < range.endInclusive) {
                Icon(painterResource(R.drawable.ic_add), stringResource(R.string.speech_pitch_up))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
        ) {
            speech.pitchPresets.forEach { preset ->
                FilterChip(
                    selected = preset == pitch,
                    onClick = { onPitch(preset) },
                    label = { Text(stringResource(presetName(preset))) },
                )
            }
        }
    }
}

/** "Natural", "10% higher" or "15% deeper". */
@Composable
internal fun pitchLabel(pitch: Float): String {
    val percent = ((pitch - SpeechStyle.NORMAL_PITCH) * PERCENT).roundToInt()
    return when {
        percent > 0 -> stringResource(R.string.speech_pitch_higher, percent)
        percent < 0 -> stringResource(R.string.speech_pitch_lower, -percent)
        else -> stringResource(R.string.speech_pitch_own)
    }
}

/** A preset's name in words: the further from Natural, the plainer the word. */
internal fun presetName(preset: Float): Int {
    val offset = preset - SpeechStyle.NORMAL_PITCH
    return when {
        offset <= -STRONG_OFFSET -> R.string.speech_pitch_down
        offset < 0f -> R.string.speech_pitch_bit_deeper
        offset >= STRONG_OFFSET -> R.string.speech_pitch_up
        offset > 0f -> R.string.speech_pitch_bit_higher
        else -> R.string.speech_pitch_own
    }
}

private const val PERCENT = 100

/** From this far off Natural (with a float's slack under 0.1) a preset is plainly Deeper or Higher. */
private const val STRONG_OFFSET = 0.095f
