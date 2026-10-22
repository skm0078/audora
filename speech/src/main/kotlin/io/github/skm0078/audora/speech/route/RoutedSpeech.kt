// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/route/RoutedSpeech.kt
// Purpose: Renders one sentence part by part, each with its language's voice, into one clip (P3-01, ADR-026).

package io.github.skm0078.audora.speech.route

import io.github.skm0078.audora.core.config.NormalizerConfig
import io.github.skm0078.audora.core.model.ClauseMark
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.flatMap
import io.github.skm0078.audora.core.result.map
import io.github.skm0078.audora.speech.SpeechEngine
import io.github.skm0078.audora.speech.SpeechError
import io.github.skm0078.audora.speech.audio.PcmAudio
import io.github.skm0078.audora.speech.normalize.EnglishNormalizer
import io.github.skm0078.audora.speech.normalize.IndicNormalizer
import io.github.skm0078.audora.speech.normalize.TextNormalizer
import io.github.skm0078.audora.speech.normalize.TextRole
import kotlin.math.pow

/** Which voice reads a language and which engine runs it: the playback service's per-language voice settings. */
interface VoiceSource {
    suspend fun voiceFor(lang: Lang): Outcome<VoiceId, SpeechError>

    fun engine(id: EngineId): Outcome<SpeechEngine, SpeechError>
}

/**
 * One sentence's audio and how it was voiced, for the caller's log (which knows the book and page).
 *
 * @property langs the voices used, in reading order; more than one when the sentence switched voice.
 * @property voices the voice that read each of [langs], in the same order (render-speed measurement, P4-02).
 * @property fallback set when a part's voice could not speak and the whole sentence was read with the sentence's
 *   own language instead (the behaviour before P3-01); the error says why.
 * @property pausesMs silence put between clauses ([ClauseProsody]): not the voice's, so render speed leaves it out.
 */
class RoutedAudio(
    val audio: PcmAudio,
    val langs: List<Lang>,
    val voices: List<VoiceId>,
    val fallback: SpeechError?,
    val pausesMs: Long = 0,
)

/**
 * Pauses at strong punctuation inside a sentence (RI-6): [silenceMs] per mark (already scaled for pace and speed)
 * and the loudness of bracketed words, [asideGainDb] (negative: quieter).
 */
class ClauseProsody(val silenceMs: (ClauseMark) -> Int, asideGainDb: Float) {
    val asideGain: Float = DECIBEL_BASE.pow(asideGainDb / DECIBELS_PER_TENFOLD)

    private companion object {
        const val DECIBEL_BASE = 10f
        const val DECIBELS_PER_TENFOLD = 20f
    }
}

/**
 * A mixed sentence is rendered as one clip so the sentence stays the unit of playback, highlight and resume
 * (ADR-015). Parts are joined at the highest sample rate among them, so no part is downsampled.
 *
 * If a part in another language than the sentence's own cannot be voiced (no Hindi voice on the phone, say), the
 * sentence is read whole with its own language's voice rather than failing: an English book with one Hindi word
 * keeps playing on a phone without Hindi. A failure of the sentence's own language fails the sentence, as before.
 *
 * Each part is normalized for its voice's language right before synthesis ([normalizers], ADR-019): English
 * numbers, dates, money, abbreviations and links become words (P2-04), Hindi and Bengali numbers too (P3-02). A
 * language without a normalizer is read as written. [TextRole] tells the normalizer what the sentence is on the page.
 *
 * With [ClauseProsody], the routed parts are also cut at dashes, colons, semicolons, brackets, quotes and ellipses
 * ([ClauseSplitter]); each piece is voiced on its own and the mark's silence goes between them, bracketed pieces a
 * little quieter (RI-6). The fallback reads the whole sentence uncut.
 */
class RoutedSpeech(
    private val voices: VoiceSource,
    private val router: ScriptRouter,
    private val normalizers: Map<Lang, TextNormalizer> = speechNormalizers(),
) {
    suspend fun render(
        text: String,
        lang: Lang,
        role: TextRole = TextRole.BODY,
        rate: Float = SpeechStyle.NORMAL_RATE,
        prosody: ClauseProsody? = null,
    ): Outcome<RoutedAudio, SpeechError> {
        val segments = router.route(text, lang).ifEmpty { listOf(ScriptSegment(text, lang)) }
        val uncut = segments.map { Clause(it.text, it.lang, pauseBefore = null, aside = false) }
        val clauses = prosody?.let { ClauseSplitter.split(segments).ifEmpty { uncut } } ?: uncut
        val parts = ArrayList<Voiced>(clauses.size)
        for (clause in clauses) {
            when (val part = renderWith(clause.text, clause.lang, role, rate)) {
                is Outcome.Success -> parts.add(part.value)
                is Outcome.Failure -> return fallBack(Part(text, lang, role, rate), clause.lang, part.error)
            }
        }
        return Outcome.Success(join(clauses, parts, prosody))
    }

    /** Joins at the highest sample rate, with each clause's pause before it and asides at their gain. */
    private fun join(clauses: List<Clause>, parts: List<Voiced>, prosody: ClauseProsody?): RoutedAudio {
        val sampleRate = parts.maxOf { it.audio.sampleRate }
        val pieces = ArrayList<PcmAudio>(parts.size * 2)
        var pausesMs = 0L
        clauses.zip(parts).forEach { (clause, part) ->
            val pause = clause.pauseBefore?.let { mark -> prosody?.silenceMs?.invoke(mark) } ?: 0
            if (pause > 0) {
                pieces.add(PcmAudio.silence(sampleRate, pause))
                pausesMs += pause
            }
            val gain = if (clause.aside) prosody?.asideGain ?: 1f else 1f
            pieces.add(part.audio.resampled(sampleRate).withGain(gain))
        }
        // One entry per voice change, as before the cut: the pieces of one voice are one part to the caller.
        val voiced = clauses.map { it.lang }.zip(parts.map { it.voice })
            .filterIndexed { i, pair -> i == 0 || pair != clauses[i - 1].lang to parts[i - 1].voice }
        return RoutedAudio(PcmAudio.concat(pieces), voiced.map { it.first }, voiced.map { it.second }, null, pausesMs)
    }

    /** A part in [failedLang] could not be voiced: read the whole sentence with [lang]'s voice, unless that failed. */
    private suspend fun fallBack(
        sentence: Part,
        failedLang: Lang,
        error: SpeechError,
    ): Outcome<RoutedAudio, SpeechError> = if (failedLang == sentence.lang) {
        Outcome.Failure(error)
    } else {
        renderWith(sentence.text, sentence.lang, sentence.role, sentence.rate)
            .map { RoutedAudio(it.audio, listOf(sentence.lang), listOf(it.voice), error) }
    }

    private suspend fun renderWith(
        text: String,
        lang: Lang,
        role: TextRole,
        rate: Float,
    ): Outcome<Voiced, SpeechError> = voices.voiceFor(lang).flatMap { voice ->
        when (val engine = voices.engine(voice.engine)) {
            is Outcome.Success ->
                engine.value.render(spoken(text, lang, role), voice, rate).map { Voiced(it, voice) }

            is Outcome.Failure -> engine
        }
    }

    /** The whole sentence as asked for, for the fallback. */
    private class Part(val text: String, val lang: Lang, val role: TextRole, val rate: Float)

    private class Voiced(val audio: PcmAudio, val voice: VoiceId)

    private fun spoken(text: String, lang: Lang, role: TextRole): String =
        normalizers[lang]?.normalize(text, role)?.takeIf { it.isNotBlank() } ?: text
}

/** Every language's normalizer, keyed by language: English (P2-04), Hindi and Bengali (P3-02). */
fun speechNormalizers(config: NormalizerConfig = NormalizerConfig()): Map<Lang, TextNormalizer> =
    mapOf(Lang.EN to EnglishNormalizer(config)) +
        Lang.entries.mapNotNull { lang -> IndicNormalizer.forLang(lang)?.let { lang to it } }
