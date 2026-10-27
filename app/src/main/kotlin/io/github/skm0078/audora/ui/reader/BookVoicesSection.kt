// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/BookVoicesSection.kt
// Purpose: The speech sheet's voice choice: the open book's own (plan item 4), or every book's (UI audit), the open
//          book's language first.

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
import io.github.skm0078.audora.ui.theme.LinkOption
import io.github.skm0078.audora.ui.theme.OptionDivider
import io.github.skm0078.audora.ui.theme.OptionGroup

/**
 * One row per language that has more than one voice to pick from, showing the voice that reads it; a tap opens the
 * choices. The open book's language ([bookLang]) comes first and is always shown, even with one voice, so a Bengali
 * book never shows only English's choices (phone test). Downloads stay in Settings → Voices.
 */
@Composable
fun BookVoicesSection(
    rows: List<VoiceRow>,
    bookVoices: Map<Lang, VoiceId>,
    title: String,
    bookLang: Lang?,
    onVoice: (Lang, VoiceId) -> Unit,
) {
    val choices = shownVoiceChoices(bookVoiceChoices(rows), bookLang)
    if (choices.isEmpty()) return
    OptionGroup(title) {
        choices.entries.forEachIndexed { index, (lang, voices) ->
            if (index > 0) OptionDivider()
            val names = voices.associate { it.id to it.label }
            val reading = bookVoices[lang] ?: voices.firstOrNull { it.readsLanguage }?.id ?: voices.first().id
            if (voices.size == 1) {
                LinkOption(
                    stringResource(langLabel(lang)),
                    onClick = null,
                    subtitle = stringResource(R.string.speech_voices_only_one),
                    value = names[reading].orEmpty(),
                )
            } else {
                ChoiceOption(
                    stringResource(langLabel(lang)),
                    voices.map { it.id },
                    reading,
                    label = { names[it].orEmpty() },
                    onPick = { onVoice(lang, it) },
                )
            }
        }
    }
}

/**
 * The languages the sheet shows, in order: [bookLang] first whenever it has a voice, then every other language with
 * more than one voice to pick from.
 */
internal fun shownVoiceChoices(choices: Map<Lang, List<VoiceRow>>, bookLang: Lang?): Map<Lang, List<VoiceRow>> {
    val first = bookLang?.let { lang -> choices[lang]?.takeIf { it.isNotEmpty() }?.let { lang to it } }
    val others = choices.filter { (lang, voices) -> lang != bookLang && voices.size > 1 }
    return listOfNotNull(first).toMap() + others
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
