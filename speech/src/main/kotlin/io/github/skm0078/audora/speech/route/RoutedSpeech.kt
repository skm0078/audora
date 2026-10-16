// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/route/RoutedSpeech.kt
// Purpose: Renders one sentence part by part, each with its language's voice, into one clip (P3-01, ADR-026).

package io.github.skm0078.audora.speech.route

import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.flatMap
import io.github.skm0078.audora.core.result.map
import io.github.skm0078.audora.speech.SpeechEngine
import io.github.skm0078.audora.speech.SpeechError
import io.github.skm0078.audora.speech.audio.PcmAudio
import io.github.skm0078.audora.speech.normalize.IndicNormalizer
import io.github.skm0078.audora.speech.normalize.TextNormalizer
import io.github.skm0078.audora.speech.normalize.TextRole

/** Which voice reads a language and which engine runs it: the playback service's per-language voice settings. */
interface VoiceSource {
    suspend fun voiceFor(lang: Lang): Outcome<VoiceId, SpeechError>

    fun engine(id: EngineId): Outcome<SpeechEngine, SpeechError>
}

/**
 * One sentence's audio and how it was voiced, for the caller's log (which knows the book and page).
 *
 * @property langs the voices used, in reading order; more than one when the sentence switched voice.
 * @property fallback set when a part's voice could not speak and the whole sentence was read with the sentence's
 *   own language instead (the behaviour before P3-01); the error says why.
 */
class RoutedAudio(val audio: PcmAudio, val langs: List<Lang>, val fallback: SpeechError?)

/**
 * A mixed sentence is rendered as one clip so the sentence stays the unit of playback, highlight and resume
 * (ADR-015). Parts are joined at the highest sample rate among them, so no part is downsampled.
 *
 * If a part in another language than the sentence's own cannot be voiced (no Hindi voice on the phone, say), the
 * sentence is read whole with its own language's voice rather than failing: an English book with one Hindi word
 * keeps playing on a phone without Hindi. A failure of the sentence's own language fails the sentence, as before.
 *
 * Each part is normalized for its voice's language right before synthesis ([normalizers], ADR-019): Hindi and
 * Bengali numbers become words (P3-02). A language without a normalizer is read as written.
 */
class RoutedSpeech(
    private val voices: VoiceSource,
    private val router: ScriptRouter,
    private val normalizers: Map<Lang, TextNormalizer> = indicNormalizers(),
) {
    suspend fun render(text: String, lang: Lang): Outcome<RoutedAudio, SpeechError> {
        val segments = router.route(text, lang).ifEmpty { listOf(ScriptSegment(text, lang)) }
        val parts = ArrayList<PcmAudio>(segments.size)
        for (segment in segments) {
            when (val part = renderWith(segment.text, segment.lang)) {
                is Outcome.Success -> parts.add(part.value)
                is Outcome.Failure -> return fallBack(text, lang, segment.lang, part.error)
            }
        }
        val rate = parts.maxOf { it.sampleRate }
        val joined = PcmAudio.concat(parts.map { it.resampled(rate) })
        return Outcome.Success(RoutedAudio(joined, segments.map { it.lang }, null))
    }

    /** A part in [failedLang] could not be voiced: read the whole sentence with [lang]'s voice, unless that failed. */
    private suspend fun fallBack(
        text: String,
        lang: Lang,
        failedLang: Lang,
        error: SpeechError,
    ): Outcome<RoutedAudio, SpeechError> = if (failedLang == lang) {
        Outcome.Failure(error)
    } else {
        renderWith(text, lang).map { RoutedAudio(it, listOf(lang), error) }
    }

    private suspend fun renderWith(text: String, lang: Lang): Outcome<PcmAudio, SpeechError> =
        voices.voiceFor(lang).flatMap { voice ->
            when (val engine = voices.engine(voice.engine)) {
                is Outcome.Success -> engine.value.render(spoken(text, lang), voice)
                is Outcome.Failure -> engine
            }
        }

    private fun spoken(text: String, lang: Lang): String =
        normalizers[lang]?.normalize(text, TextRole.BODY)?.takeIf { it.isNotBlank() } ?: text
}

/** The Hindi and Bengali normalizers, keyed by language. */
fun indicNormalizers(): Map<Lang, TextNormalizer> =
    Lang.entries.mapNotNull { lang -> IndicNormalizer.forLang(lang)?.let { lang to it } }.toMap()
