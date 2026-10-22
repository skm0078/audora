// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/FocusSoundSection.kt
// Purpose: The speech sheet's focus sound controls (plan item 72): which sound plays under the voice, and how loud.

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.model.FocusSound
import io.github.skm0078.audora.core.model.FocusSoundOptions
import kotlin.math.roundToInt

/**
 * The volume is sent when the slider is let go, not on every step, since each change is saved. Plain Material
 * controls (restyled with the UI pass), like the study controls above.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusSoundSection(options: FocusSoundOptions, onOptions: (FocusSoundOptions) -> Unit) {
    Text(stringResource(R.string.focus_title), style = MaterialTheme.typography.titleMedium)
    Text(
        stringResource(R.string.focus_detail),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        FocusSound.entries.forEachIndexed { index, sound ->
            SegmentedButton(
                selected = options.sound == sound,
                onClick = { onOptions(options.copy(sound = sound)) },
                shape = SegmentedButtonDefaults.itemShape(index, FocusSound.entries.size),
            ) { Text(stringResource(soundLabel(sound))) }
        }
    }
    if (options.sound == FocusSound.OFF) return
    var level by remember(options.level) { mutableFloatStateOf(options.level) }
    val title = stringResource(R.string.focus_level)
    val percent = stringResource(R.string.slider_percent, (level * PERCENT).roundToInt())
    Text(title, style = MaterialTheme.typography.bodyLarge)
    Slider(
        modifier = Modifier.semantics {
            contentDescription = title
            stateDescription = percent
        },
        value = level,
        onValueChange = { level = it },
        onValueChangeFinished = { onOptions(options.copy(level = level)) },
    )
}

private fun soundLabel(sound: FocusSound): Int = when (sound) {
    FocusSound.OFF -> R.string.focus_off
    FocusSound.RAIN -> R.string.focus_rain
    FocusSound.CAFE -> R.string.focus_cafe
    FocusSound.BROWN_NOISE -> R.string.focus_brown_noise
}

/** The level is 0-1; TalkBack reads it as a percentage. */
private const val PERCENT = 100
