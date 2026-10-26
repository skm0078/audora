// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/FocusSoundSection.kt
// Purpose: The speech sheet's focus sound controls (plan item 72): which sound plays under the voice, and how loud.

package io.github.skm0078.audora.ui.reader

import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.model.FocusSound
import io.github.skm0078.audora.core.model.FocusSoundOptions
import io.github.skm0078.audora.ui.theme.ChoiceOption
import io.github.skm0078.audora.ui.theme.OptionBlock
import io.github.skm0078.audora.ui.theme.OptionDivider
import io.github.skm0078.audora.ui.theme.OptionGroup
import io.github.skm0078.audora.ui.theme.namedSlider
import kotlin.math.roundToInt

/** The volume is sent when the slider is let go, not on every step, since each change is saved. */
@Composable
fun FocusSoundSection(options: FocusSoundOptions, onOptions: (FocusSoundOptions) -> Unit) {
    OptionGroup(title = null) {
        ChoiceOption(
            stringResource(R.string.focus_title),
            FocusSound.entries,
            options.sound,
            label = { stringResource(focusSoundLabel(it)) },
            onPick = { onOptions(options.copy(sound = it)) },
            help = stringResource(R.string.focus_detail),
        )
        if (options.sound != FocusSound.OFF) {
            OptionDivider()
            LevelSlider(options, onOptions)
        }
    }
}

@Composable
private fun LevelSlider(options: FocusSoundOptions, onOptions: (FocusSoundOptions) -> Unit) {
    var level by remember(options.level) { mutableFloatStateOf(options.level) }
    val title = stringResource(R.string.focus_level)
    val percent = stringResource(R.string.slider_percent, (level * PERCENT).roundToInt())
    OptionBlock(title, value = stringResource(R.string.focus_level_value, (level * PERCENT).roundToInt())) {
        Slider(
            modifier = Modifier.namedSlider(title, percent),
            value = level,
            onValueChange = { level = it },
            onValueChangeFinished = { onOptions(options.copy(level = level)) },
        )
    }
}

/** The level is 0-1; TalkBack reads it as a percentage. */
private const val PERCENT = 100
