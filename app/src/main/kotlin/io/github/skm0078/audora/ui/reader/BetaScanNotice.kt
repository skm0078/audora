// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/BetaScanNotice.kt
// Purpose: Says plainly that a scanned page in a beta language (Bengali) is early work, and offers the phone's own
//          voice for it when the phone has one and ours reads it now (2026-10-31).

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.SpeechEngines
import io.github.skm0078.audora.speech.voices.VoiceRow
import io.github.skm0078.audora.ui.theme.Dimens
import io.github.skm0078.audora.ui.voices.languageName

private const val TAG = "UI.BetaScan"

/**
 * The beta language a page is a scan of, or null. Only an OCR page counts: a Bengali text PDF reads its own letters,
 * and it is OCR's slips on older print that the voices read aloud (a book whose font drops joined letters is read by
 * OCR on every page, #130).
 */
internal fun betaScanLanguage(origin: PageOrigin, units: List<ReadingUnit>, betaLanguages: Set<Lang>): Lang? =
    if (origin != PageOrigin.OCR) null else pageLanguage(units)?.takeIf { it in betaLanguages }

/**
 * Whether to offer the phone's voice for [lang]: the phone has one offline ([phoneVoice]) and one of our voices reads
 * [lang] now, the book's own ([own], while it keeps its own settings) or every book's ([rows]). Nothing is offered
 * when the phone's voice already reads it, or when no voice of ours is in (the phone's voice is the default then).
 */
internal fun offersPhoneVoice(
    lang: Lang,
    phoneVoice: VoiceId?,
    own: Map<Lang, VoiceId>,
    rows: List<VoiceRow>,
): Boolean {
    if (phoneVoice == null) return false
    val reading = own[lang] ?: rows.firstOrNull { it.lang == lang && it.readsLanguage }?.id
    return reading != null && reading.engine != EngineId.SYSTEM
}

/**
 * What the notice needs beyond the page: the voices, the phone's voice for the language once asked, the book's own
 * picks, and the tap that switches. "Not now" hides it for this book until the reader is opened again.
 */
internal class BetaScanOffer(
    val voices: List<VoiceRow>,
    val own: Map<Lang, VoiceId>,
    val phoneVoices: Map<Lang, VoiceId>,
    val hidden: Boolean,
    val onHide: () -> Unit,
    val onUsePhoneVoice: (Lang, VoiceId) -> Unit,
)

/**
 * Asks the phone once per beta language for its offline voice (it binds the phone's TTS, so only while such a page
 * is open), and keeps "Not now" per book.
 */
@Composable
internal fun rememberBetaScanOffer(
    bookId: BookId,
    betaLang: Lang?,
    voices: List<VoiceRow>,
    own: Map<Lang, VoiceId>,
    onUsePhoneVoice: (Lang, VoiceId) -> Unit,
): BetaScanOffer {
    val context = LocalContext.current.applicationContext
    var hidden by rememberSaveable(bookId) { mutableStateOf(false) }
    var phoneVoices by remember { mutableStateOf(emptyMap<Lang, VoiceId>()) }
    LaunchedEffect(betaLang) {
        val lang = betaLang?.takeIf { it !in phoneVoices } ?: return@LaunchedEffect
        when (val found = SpeechEngines.phoneVoice(context, lang)) {
            is Outcome.Success -> phoneVoices = phoneVoices + (lang to found.value)

            // The usual case on a phone without the language's voice pack: the notice then only explains.
            is Outcome.Failure -> AudoraLog.i(TAG, "No phone voice to offer for ${lang.name}: ${found.error}")
        }
    }
    return BetaScanOffer(voices, own, phoneVoices, hidden, { hidden = true }, onUsePhoneVoice)
}

/** The card under the OCR badge on a beta language's scanned page; nothing elsewhere or once hidden. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BetaScanNotice(lang: Lang?, offer: BetaScanOffer) {
    if (lang == null || offer.hidden) return
    val language = stringResource(languageName(lang))
    val phoneVoice = offer.phoneVoices[lang]?.takeIf { offersPhoneVoice(lang, it, offer.own, offer.voices) }
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceS)) {
        Column(Modifier.padding(Dimens.SpaceL), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
            Text(
                stringResource(R.string.reader_beta_scan_title, language),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.reader_beta_scan_body, language), style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
                if (phoneVoice != null) {
                    OutlinedButton(onClick = { offer.onUsePhoneVoice(lang, phoneVoice) }) {
                        Text(stringResource(R.string.reader_beta_scan_phone_voice, language))
                    }
                }
                TextButton(onClick = offer.onHide) { Text(stringResource(R.string.reader_voice_pack_later)) }
            }
        }
    }
}

/**
 * What the notices at the top of an open page need beyond the page: the language-pack offer while it is the notice
 * shown ([VoicePackNotice]), and the voices, for [BetaScanNotice].
 */
internal class PageTopNotices(val voicePack: VoicePackOffer?, val voices: List<VoiceRow>)
