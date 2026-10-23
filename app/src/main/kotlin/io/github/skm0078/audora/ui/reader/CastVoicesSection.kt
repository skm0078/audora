// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/CastVoicesSection.kt
// Purpose: The speech sheet's cast: a second voice for quoted speech, a third for headings (plan item 7) and a voice
// per character (item 63).

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.model.VoiceShaping
import io.github.skm0078.audora.speech.voices.VoiceRow
import io.github.skm0078.audora.ui.theme.switchRow

/**
 * Only languages with a second voice to pick from show up; with none, the switches say to add one in Settings →
 * Voices. Switching a role on picks, per language, the first voice that is not the language's usual one; a chip
 * changes it. Every voice used costs its model's memory while the book plays.
 */
@Composable
fun CastVoicesSection(rows: List<VoiceRow>, shaping: VoiceShaping, onChange: (VoiceShaping) -> Unit) {
    val choices = bookVoiceChoices(rows).filterValues { it.size > 1 }
    Text(stringResource(R.string.speech_cast), style = MaterialTheme.typography.titleMedium)
    CastRole(
        stringResource(R.string.speech_cast_dialogue),
        stringResource(
            if (choices.isEmpty()) R.string.speech_cast_needs_voice else R.string.speech_cast_dialogue_detail,
        ),
        choices,
        shaping.dialogueVoices,
    ) { onChange(shaping.copy(dialogueVoices = it)) }
    CastRole(
        stringResource(R.string.speech_cast_headings),
        stringResource(
            if (choices.isEmpty()) R.string.speech_cast_needs_voice else R.string.speech_cast_headings_detail,
        ),
        choices,
        shaping.headingVoices,
    ) { onChange(shaping.copy(headingVoices = it)) }
    CharacterVoicesRow(shaping.characterVoices) { onChange(shaping.copy(characterVoices = it)) }
}

/** Always offered: the narrator's own model supplies the voices, so no voice needs adding first. */
@Composable
private fun CharacterVoicesRow(on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.switchRow(on, onChange = onChange), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.speech_cast_characters), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.speech_cast_characters_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = on, onCheckedChange = null)
    }
}

@Composable
private fun CastRole(
    title: String,
    detail: String,
    choices: Map<Lang, List<VoiceRow>>,
    cast: Map<Lang, VoiceId>,
    onCast: (Map<Lang, VoiceId>) -> Unit,
) {
    val castOn = cast.isNotEmpty()
    val castable = choices.isNotEmpty() || castOn
    val onCastChange: (Boolean) -> Unit = { on -> onCast(if (on) firstCast(choices) else emptyMap()) }
    Row(Modifier.switchRow(castOn, castable, onCastChange), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = castOn, onCheckedChange = null, enabled = castable)
    }
    if (cast.isEmpty()) return
    choices.forEach { (lang, voices) ->
        Text(stringResource(castLangLabel(lang)), style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            voices.forEach { voice ->
                FilterChip(
                    selected = voice.id == cast[lang],
                    onClick = { onCast(cast + (lang to voice.id)) },
                    label = { Text(voice.label) },
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
