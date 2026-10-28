// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/route/RoutedSpeech.kt
// Purpose: Renders one sentence part by part, each with its language's voice, into one clip (P3-01, ADR-026).

package io.github.skm0078.audora.speech.route

import io.github.skm0078.audora.core.config.NormalizerConfig
import io.github.skm0078.audora.core.model.ClauseMark
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Feeling
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

    /**
     * The phone's offline voice for [language], a BCP 47 primary tag beyond the three [Lang]s ("fr", "ta"), or why
     * there is none (ADR 2026-10-26). Never a voice that needs the network.
     */
    suspend fun phoneVoiceFor(language: String): Outcome<VoiceId, SpeechError>

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
 * A part for the phone's voice ([ScriptSegment.phone]: the book's language, or a script none of the three is written
 * in) that has no voice on the phone falls back the same way, so a French book on a phone without French reads as it
 * did before phone languages: whole, with the English voice.
 *
 * Each part is normalized for its voice's language right before synthesis ([normalizers], ADR-019): English
 * numbers, dates, money, abbreviations and links become words (P2-04), Hindi and Bengali numbers too (P3-02). A
 * language without a normalizer is read as written, and so is a phone-voice part: the phone's engine says numbers
 * and dates in its own language. [TextRole] tells the normalizer what the sentence is on the page.
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
    @Suppress("LongParameterList") // All but the text and language optional, with the defaults of a plain read.
    suspend fun render(
        text: String,
        lang: Lang,
        role: TextRole = TextRole.BODY,
        rate: Float = SpeechStyle.NORMAL_RATE,
        prosody: ClauseProsody? = null,
        shape: (PcmAudio) -> PcmAudio = { it },
        feeling: Feeling = Feeling.NEUTRAL,
        pitch: Float = 1f,
        bookLanguage: String? = null,
    ): Outcome<RoutedAudio, SpeechError> {
        val segments = router.route(text, lang, bookLanguage).ifEmpty { listOf(ScriptSegment(text, lang)) }
        val uncut = segments.map { Clause(it.text, it.lang, pauseBefore = null, aside = false, it.phone) }
        val clauses = prosody?.let { ClauseSplitter.split(segments).ifEmpty { uncut } } ?: uncut
        val parts = ArrayList<Voiced>(clauses.size)
        val tone = Tone(rate, feeling, pitch)
        for (clause in clauses) {
            when (val part = renderWith(clause.text, Reader(clause.lang, clause.phone), role, tone)) {
                is Outcome.Success -> parts.add(part.value.let { Voiced(shape(it.audio), it.voice) })
                is Outcome.Failure -> return fallBack(Part(text, lang, role, tone), clause, part.error, shape)
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

    /**
     * [failed] could not be voiced: read the whole sentence with its language's own voice, unless that is the voice
     * that failed.
     */
    private suspend fun fallBack(
        sentence: Part,
        failed: Clause,
        error: SpeechError,
        shape: (PcmAudio) -> PcmAudio,
    ): Outcome<RoutedAudio, SpeechError> = if (failed.phone == null && failed.lang == sentence.lang) {
        Outcome.Failure(error)
    } else {
        renderWith(sentence.text, Reader(sentence.lang, phone = null), sentence.role, sentence.tone)
            .map { RoutedAudio(shape(it.audio), listOf(sentence.lang), listOf(it.voice), error) }
    }

    /**
     * A voice that acts feelings in a style of its own (ADR-057) is not shifted as well. Every other voice reads a
     * feeling's [Tone.pitch] as a tape played faster or slower: it is asked for the speed that comes back to [Tone.rate]
     * once shifted, so the words keep their length.
     */
    private suspend fun renderWith(
        text: String,
        reader: Reader,
        role: TextRole,
        tone: Tone,
    ): Outcome<Voiced, SpeechError> {
        val chosen = reader.phone?.let { voices.phoneVoiceFor(it) } ?: voices.voiceFor(reader.lang)
        return chosen.flatMap { voice ->
            when (val engine = voices.engine(voice.engine)) {
                is Outcome.Success -> {
                    val pitch = if (engine.value.actsFeelings(voice)) 1f else tone.pitch
                    engine.value.render(spoken(text, reader, role), voice, tone.rate / pitch, tone.feeling)
                        .map { Voiced(it.pitched(pitch), voice) }
                }

                is Outcome.Failure -> engine
            }
        }
    }

    /** Who reads a part: [lang]'s voice, or the phone's voice for [phone] when set. */
    private class Reader(val lang: Lang, val phone: String?)

    /** How a part is voiced: its speed, its feeling, and its pitch as a ratio (1: the voice's own). */
    private class Tone(val rate: Float, val feeling: Feeling, val pitch: Float)

    /** The whole sentence as asked for, for the fallback. */
    private class Part(val text: String, val lang: Lang, val role: TextRole, val tone: Tone)

    private class Voiced(val audio: PcmAudio, val voice: VoiceId)

    private fun spoken(text: String, reader: Reader, role: TextRole): String = if (reader.phone != null) {
        text
    } else {
        // Blank when the text was all symbols no voice can say ("★★★", "* * *"): the engine then gives silence.
        normalizers[reader.lang]?.normalize(text, role) ?: text
    }
}

/** Every language's normalizer, keyed by language: English (P2-04), Hindi and Bengali (P3-02). */
fun speechNormalizers(config: NormalizerConfig = NormalizerConfig()): Map<Lang, TextNormalizer> =
    mapOf(Lang.EN to EnglishNormalizer(config)) +
        Lang.entries.mapNotNull { lang -> IndicNormalizer.forLang(lang)?.let { lang to it } }
