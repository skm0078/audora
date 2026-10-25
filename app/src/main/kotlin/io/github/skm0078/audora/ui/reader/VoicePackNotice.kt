// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/VoicePackNotice.kt
// Purpose: Offers a Hindi or Bengali book's language pack in the reader when the phone's voice would read it.

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.speech.voices.LanguagePacks
import io.github.skm0078.audora.speech.voices.VoiceRow
import io.github.skm0078.audora.speech.voices.VoiceStatus
import io.github.skm0078.audora.ui.theme.Dimens
import io.github.skm0078.audora.ui.voices.PackStatus
import io.github.skm0078.audora.ui.voices.languageName
import io.github.skm0078.audora.ui.voices.megabytes

/**
 * Shown while the page is mostly in a language whose pack is not on the phone: what it is, its size, one Download
 * tap and its progress. "Not now" hides it for that language until the book is opened again; once a voice of the
 * language is in, the card goes by itself (the library picks the new voice when the language has none).
 */
@Composable
internal fun VoicePackNotice(units: List<ReadingUnit>, offer: VoicePackOffer) {
    val lang = pageLanguage(units) ?: return
    var dismissed by rememberSaveable(lang) { mutableStateOf(false) }
    val pack = LanguagePacks.pack(offer.voices, lang)?.takeIf { !it.ready && !dismissed } ?: return
    val language = stringResource(languageName(lang))
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceS)) {
        Column(Modifier.padding(Dimens.SpaceL), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
            Text(
                stringResource(R.string.reader_voice_pack_title, language),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            if (pack.downloading || pack.voice.status is VoiceStatus.Failed) {
                PackStatus(pack, language)
            } else {
                Text(
                    stringResource(R.string.reader_voice_pack_body, language, megabytes(pack.voice.sizeBytes)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
                if (pack.downloading) {
                    TextButton(onClick = {
                        offer.actions.onCancel(pack.voice)
                    }) { Text(stringResource(R.string.voices_cancel)) }
                } else {
                    Button(onClick = { offer.actions.onDownload(pack.voice) }) {
                        Text(stringResource(R.string.voice_pack_download))
                    }
                    TextButton(onClick = { dismissed = true }) {
                        Text(stringResource(R.string.reader_voice_pack_later))
                    }
                }
            }
        }
    }
}

/** What a tap on the reader's language-pack card does; the defaults do nothing (previews, tests). */
class VoicePackActions(val onDownload: (VoiceRow) -> Unit = {}, val onCancel: (VoiceRow) -> Unit = {})

/** The voices the card reads its pack from, and its [actions]. */
internal class VoicePackOffer(val voices: List<VoiceRow>, val actions: VoicePackActions)

/** The language most of the page's letters are in, when that is Hindi or Bengali; English has a built-in voice. */
internal fun pageLanguage(units: List<ReadingUnit>): Lang? {
    val byLang = units.groupBy { it.lang }.mapValues { (_, list) -> list.sumOf { it.text.length } }
    val total = byLang.values.sum()
    val (lang, chars) = byLang.maxByOrNull { it.value } ?: return null
    return lang.takeIf { it != Lang.EN && chars * 2 > total }
}
