// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/CastVoicesSection.kt
// Purpose: The speech sheet's cast: a second voice for quoted speech, a third for headings (plan item 7) and a voice
// per character (item 63).

package io.github.skm0078.audora.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.model.VoiceShaping
import io.github.skm0078.audora.speech.voices.VoiceRow
import io.github.skm0078.audora.ui.theme.ChoiceOption
import io.github.skm0078.audora.ui.theme.OptionDivider
import io.github.skm0078.audora.ui.theme.OptionGroup
import io.github.skm0078.audora.ui.theme.SwitchOption

/**
 * Only languages with a second voice to pick from show up; with none, the switches say to add one in Settings →
 * Voices. Switching a role on picks, per language, the first voice that is not the language's usual one; the row
 * under it shows that voice and changes it. Every voice used costs its model's memory while the book plays.
 */
@Composable
fun CastVoicesSection(rows: List<VoiceRow>, shaping: VoiceShaping, onChange: (VoiceShaping) -> Unit) {
    val choices = bookVoiceChoices(rows).filterValues { it.size > 1 }
    CastRole(
        stringResource(R.string.speech_cast_dialogue),
        stringResource(R.string.speech_cast_dialogue_detail),
        choices,
        shaping.dialogueVoices,
    ) { onChange(shaping.copy(dialogueVoices = it)) }
    CastRole(
        stringResource(R.string.speech_cast_headings),
        stringResource(R.string.speech_cast_headings_detail),
        choices,
        shaping.headingVoices,
    ) { onChange(shaping.copy(headingVoices = it)) }
    // Always offered: the narrator's own model supplies the voices, so no voice needs adding first.
    OptionGroup(title = null) {
        SwitchOption(
            stringResource(R.string.speech_cast_characters),
            shaping.characterVoices,
            { onChange(shaping.copy(characterVoices = it)) },
            help = stringResource(R.string.speech_cast_characters_detail),
        )
    }
}

@Composable
private fun CastRole(
    title: String,
    help: String,
    choices: Map<Lang, List<VoiceRow>>,
    cast: Map<Lang, VoiceId>,
    onCast: (Map<Lang, VoiceId>) -> Unit,
) {
    val castOn = cast.isNotEmpty()
    val castable = choices.isNotEmpty() || castOn
    OptionGroup(title = null) {
        SwitchOption(
            title,
            castOn,
            { on -> onCast(if (on) firstCast(choices) else emptyMap()) },
            subtitle = if (choices.isEmpty()) stringResource(R.string.speech_cast_needs_voice) else null,
            help = help,
            enabled = castable,
        )
        if (castOn) {
            choices.forEach { (lang, voices) ->
                OptionDivider()
                val names = voices.associate { it.id to it.label }
                ChoiceOption(
                    stringResource(R.string.speech_cast_voice_for, stringResource(castLangLabel(lang))),
                    voices.map { it.id },
                    cast[lang] ?: voices.first().id,
                    label = { names[it].orEmpty() },
                    onPick = { onCast(cast + (lang to it)) },
                )
            }
        }
    }
}

/** Per language, the first voice that is not the one that reads it by default. */
internal fun firstCast(choices: Map<Lang, List<VoiceRow>>): Map<Lang, VoiceId> = choices.mapNotNull { (lang, voices) ->
    val usual = voices.firstOrNull { it.readsLanguage }?.id
    voices.firstOrNull { it.id != usual }?.let { lang to it.id }
}.toMap()

private fun castLangLabel(lang: Lang): Int = when (lang) {
    Lang.EN -> R.string.voices_lang_en
    Lang.HI -> R.string.voices_lang_hi
    Lang.BN -> R.string.voices_lang_bn
}
