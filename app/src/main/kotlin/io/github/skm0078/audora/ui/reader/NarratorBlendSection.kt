// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/NarratorBlendSection.kt
// Purpose: The speech sheet's narrator blend: another Kokoro voice mixed into the narrator's own (plan item 75).

package io.github.skm0078.audora.ui.reader

import androidx.compose.material3.Slider
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
import io.github.skm0078.audora.core.model.VoiceShaping
import io.github.skm0078.audora.speech.sherpa.CastSpeakers
import io.github.skm0078.audora.ui.theme.ChoiceOption
import io.github.skm0078.audora.ui.theme.LinkOption
import io.github.skm0078.audora.ui.theme.OptionBlock
import io.github.skm0078.audora.ui.theme.OptionDivider
import io.github.skm0078.audora.ui.theme.OptionGroup
import io.github.skm0078.audora.ui.voices.megabytes
import kotlin.math.roundToInt

/**
 * The voice to mix in (Off first) and how much of it. Shown only while a Kokoro English voice narrates
 * ([NarratorBlendState.ownSpeaker]), the one voice a blend changes; the narrator's own speaker is left out (mixing a
 * voice with itself changes nothing). Each change reloads the voice, so the slider applies when the thumb is let go.
 * A blend that could not be built says why, in the error colour.
 */
@Composable
fun NarratorBlendSection(
    shaping: VoiceShaping,
    ownSpeaker: Int,
    neededBytes: Long?,
    onChange: (VoiceShaping) -> Unit,
) {
    val speakers = CastSpeakers.kokoroEnglish.filter { it.speakerId != ownSpeaker }
    val names = speakers.associate { it.speakerId to it.label }
    val off = stringResource(R.string.speech_blend_off)
    OptionGroup(stringResource(R.string.speech_blend)) {
        ChoiceOption(
            stringResource(R.string.speech_blend_voice),
            listOf<Int?>(null) + speakers.map { it.speakerId },
            shaping.blendWith,
            label = { id -> id?.let { names[it] } ?: off },
            onPick = { onChange(shaping.copy(blendWith = it)) },
            help = stringResource(R.string.speech_blend_detail),
        )
        if (shaping.blendWith != null) {
            OptionDivider()
            BlendAmountSlider(shaping.blendPercent) { onChange(shaping.copy(blendPercent = it)) }
            neededBytes?.let { BlendNotApplied(it) }
        }
    }
}

@Composable
private fun BlendNotApplied(neededBytes: Long) {
    val text = if (neededBytes > 0) {
        stringResource(R.string.speech_blend_needs_space, megabytes(neededBytes))
    } else {
        stringResource(R.string.speech_blend_refused)
    }
    OptionDivider()
    LinkOption(stringResource(R.string.speech_blend), onClick = null, subtitle = text, subtitleIsError = true)
}

@Composable
private fun BlendAmountSlider(percent: Int, onChange: (Int) -> Unit) {
    var dragged by remember(percent) { mutableFloatStateOf(percent.toFloat()) }
    val range = VoiceShaping.BLEND_PERCENTS
    val amount = stringResource(R.string.speech_blend_amount, dragged.roundToInt())
    val title = stringResource(R.string.speech_blend_amount_title)
    OptionBlock(
        title,
        value = stringResource(R.string.speech_blend_percent, dragged.roundToInt()),
    ) {
        Slider(
            modifier = Modifier.semantics {
                contentDescription = title
                stateDescription = amount
            },
            value = dragged,
            onValueChange = { dragged = it },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            onValueChangeFinished = { dragged.roundToInt().takeIf { it != percent }?.let(onChange) },
        )
    }
}
