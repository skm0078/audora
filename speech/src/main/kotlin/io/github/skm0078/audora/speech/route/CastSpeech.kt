// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/route/CastSpeech.kt
// Purpose: Reads quoted speech and headings with their own voices around the narrator's (plan item 7).

package io.github.skm0078.audora.speech.route

import io.github.skm0078.audora.core.config.DialogueConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.SpeechEngine
import io.github.skm0078.audora.speech.SpeechError
import io.github.skm0078.audora.speech.audio.PcmAudio
import io.github.skm0078.audora.speech.normalize.TextNormalizer
import io.github.skm0078.audora.speech.normalize.TextRole

/**
 * The voices that stand in for the narrator's, per language: [dialogue] reads quoted speech, [headings] headings. A
 * language missing from a map keeps the narrator's voice for that role.
 */
data class Cast(val dialogue: Map<Lang, VoiceId> = emptyMap(), val headings: Map<Lang, VoiceId> = emptyMap()) {
    val isEmpty: Boolean get() = dialogue.isEmpty() && headings.isEmpty()
}

/** How one sentence is spoken, beyond its text and language: the defaults of a plain read. */
class SpeechRequest(
    val role: TextRole = TextRole.BODY,
    val rate: Float = SpeechStyle.NORMAL_RATE,
    val prosody: ClauseProsody? = null,
    val shape: (PcmAudio) -> PcmAudio = { it },
)

/**
 * Wraps [RoutedSpeech]: a heading is read whole by the heading voice; a sentence with quoted speech
 * ([QuoteSplitter]) is read part by part, the quotes by the dialogue voice, with [quotePauseMs] of silence where the
 * voice changes (the quote marks are gone, so this is their pause). Each part still goes through script routing, so
 * a Hindi word inside an English quote keeps the Hindi voice. With more than one part, each is passed through
 * [level] before the join, so two models of different loudness meet at one level.
 *
 * A cast voice that cannot speak (uninstalled since it was picked, say) never fails the sentence: the narrator reads
 * that part instead, and the failure is logged with the voice and language.
 */
class CastSpeech(
    private val voices: VoiceSource,
    private val router: ScriptRouter,
    private val normalizers: Map<Lang, TextNormalizer> = speechNormalizers(),
    private val quotes: QuoteSplitter = QuoteSplitter(DialogueConfig()),
) {
    private val narrator = RoutedSpeech(voices, router, normalizers)

    @Suppress("LongParameterList") // The sentence, its request, and the cast's two settings.
    suspend fun render(
        text: String,
        lang: Lang,
        request: SpeechRequest = SpeechRequest(),
        cast: Cast = Cast(),
        quotePauseMs: Int = 0,
        level: (PcmAudio) -> PcmAudio = { it },
    ): Outcome<RoutedAudio, SpeechError> {
        val parts = if (cast.dialogue.isEmpty()) {
            null
        } else {
            quotes.split(text).takeIf { split ->
                split.any { it.quoted }
            }
        }
        return when {
            request.role == TextRole.HEADING && cast.headings.isNotEmpty() ->
                castOrNarrator(text, lang, request, cast.headings)

            parts != null -> renderParts(parts, lang, request, cast.dialogue, Join(quotePauseMs, level))

            else -> narrator.render(text, lang, request)
        }
    }

    private suspend fun renderParts(
        parts: List<QuotePart>,
        lang: Lang,
        request: SpeechRequest,
        dialogue: Map<Lang, VoiceId>,
        join: Join,
    ): Outcome<RoutedAudio, SpeechError> {
        val rendered = ArrayList<RoutedAudio>(parts.size)
        for (part in parts) {
            val audio = if (part.quoted) {
                castOrNarrator(part.text, lang, request, dialogue)
            } else {
                narrator.render(part.text, lang, request)
            }
            when (audio) {
                is Outcome.Success -> rendered.add(audio.value)
                is Outcome.Failure -> return audio
            }
        }
        return Outcome.Success(join.of(rendered))
    }

    private suspend fun castOrNarrator(
        text: String,
        lang: Lang,
        request: SpeechRequest,
        cast: Map<Lang, VoiceId>,
    ): Outcome<RoutedAudio, SpeechError> {
        val voice = cast[lang] ?: return narrator.render(text, lang, request)
        val castSpeech = RoutedSpeech(CastVoices(voices, cast), router, normalizers)
        return when (val audio = castSpeech.render(text, lang, request)) {
            is Outcome.Success -> audio

            is Outcome.Failure -> {
                AudoraLog.w(
                    TAG,
                    "Cast voice $voice for ${lang.name} could not speak, narrator instead: ${audio.error.message}",
                )
                narrator.render(text, lang, request)
            }
        }
    }

    private companion object {
        const val TAG = "Speech.Cast"
    }
}

/**
 * Joins the parts at the highest sample rate among them, as [RoutedSpeech] joins its own, each [level]led, with
 * [pauseMs] of silence between them.
 */
private class Join(private val pauseMs: Int, private val level: (PcmAudio) -> PcmAudio) {
    fun of(parts: List<RoutedAudio>): RoutedAudio {
        val sampleRate = parts.maxOf { it.audio.sampleRate }
        val pieces = ArrayList<PcmAudio>(parts.size * 2)
        parts.forEachIndexed { i, part ->
            if (i > 0 && pauseMs > 0) pieces.add(PcmAudio.silence(sampleRate, pauseMs))
            pieces.add(level(part.audio).resampled(sampleRate))
        }
        val added = if (pauseMs > 0) pauseMs.toLong() * (parts.size - 1) else 0L
        return RoutedAudio(
            PcmAudio.concat(pieces),
            parts.flatMap { it.langs },
            parts.flatMap { it.voices },
            parts.firstNotNullOfOrNull { it.fallback },
            parts.sumOf { it.pausesMs } + added,
        )
    }
}

/** [base] with [cast]'s voices in place of its own for the languages it names. */
private class CastVoices(private val base: VoiceSource, private val cast: Map<Lang, VoiceId>) : VoiceSource {
    override suspend fun voiceFor(lang: Lang): Outcome<VoiceId, SpeechError> =
        cast[lang]?.let { Outcome.Success(it) } ?: base.voiceFor(lang)

    override fun engine(id: EngineId): Outcome<SpeechEngine, SpeechError> = base.engine(id)
}

/** [RoutedSpeech.render] with a [SpeechRequest]. */
private suspend fun RoutedSpeech.render(text: String, lang: Lang, request: SpeechRequest) =
    render(text, lang, request.role, request.rate, request.prosody, request.shape)
