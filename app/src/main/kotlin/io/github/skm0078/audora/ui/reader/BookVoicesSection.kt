// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/BookVoicesSection.kt
// Purpose: The speech sheet's voice choice: the open book's own (plan item 4), or every book's (UI audit).

package io.github.skm0078.audora.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.speech.voices.VoiceKind
import io.github.skm0078.audora.speech.voices.VoiceRow
import io.github.skm0078.audora.speech.voices.VoiceStatus
import io.github.skm0078.audora.ui.theme.ChoiceOption
import io.github.skm0078.audora.ui.theme.OptionDivider
import io.github.skm0078.audora.ui.theme.OptionGroup

/**
 * One row per language that has more than one voice to pick from, showing the voice that reads it; a tap opens the
 * choices. Downloads stay in Settings → Voices.
 */
@Composable
fun BookVoicesSection(
    rows: List<VoiceRow>,
    bookVoices: Map<Lang, VoiceId>,
    title: String,
    onVoice: (Lang, VoiceId) -> Unit,
) {
    val choices = bookVoiceChoices(rows).filterValues { it.size > 1 }
    if (choices.isEmpty()) return
    OptionGroup(title) {
        choices.entries.forEachIndexed { index, (lang, voices) ->
            if (index > 0) OptionDivider()
            val names = voices.associate { it.id to it.label }
            ChoiceOption(
                stringResource(langLabel(lang)),
                voices.map { it.id },
                bookVoices[lang] ?: voices.firstOrNull { it.readsLanguage }?.id ?: voices.first().id,
                label = { names[it].orEmpty() },
                onPick = { onVoice(lang, it) },
            )
        }
    }
}

/** The voices a book can use now, by language: built in or installed, and not measured too slow. */
internal fun bookVoiceChoices(rows: List<VoiceRow>): Map<Lang, List<VoiceRow>> = rows
    .filter {
        it.kind == VoiceKind.BUILT_IN || it.status == VoiceStatus.Installed ||
            it.status == VoiceStatus.UpdateAvailable
    }
    .filterNot { it.tooSlow && !it.readsLanguage }
    .distinctBy { it.id }
    .groupBy { it.lang }

private fun langLabel(lang: Lang): Int = when (lang) {
    Lang.EN -> R.string.voices_lang_en
    Lang.HI -> R.string.voices_lang_hi
    Lang.BN -> R.string.voices_lang_bn
}
