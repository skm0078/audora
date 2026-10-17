// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/BookVoicesSection.kt
// Purpose: The speech sheet's voice choice for the open book, while it has its own settings (plan item 4).

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.speech.voices.VoiceKind
import io.github.skm0078.audora.speech.voices.VoiceRow
import io.github.skm0078.audora.speech.voices.VoiceStatus

/** One row of voices per language that has more than one to pick from; downloads stay in Settings → Voices. */
@Composable
fun BookVoicesSection(rows: List<VoiceRow>, bookVoices: Map<Lang, VoiceId>, onVoice: (Lang, VoiceId) -> Unit) {
    val choices = bookVoiceChoices(rows).filterValues { it.size > 1 }
    if (choices.isEmpty()) return
    Text(stringResource(R.string.speech_book_voices), style = MaterialTheme.typography.titleMedium)
    choices.forEach { (lang, voices) ->
        val selected = bookVoices[lang] ?: voices.firstOrNull { it.readsLanguage }?.id
        Text(stringResource(langLabel(lang)), style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            voices.forEach { voice ->
                FilterChip(
                    selected = voice.id == selected,
                    onClick = { onVoice(lang, voice.id) },
                    label = { Text(voice.label) },
                )
            }
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
