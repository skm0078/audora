// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/route/CastSpeech.kt
// Purpose: Reads quoted speech and headings with their own voices around the narrator's (plan item 7).

package io.github.skm0078.audora.speech.route

import io.github.skm0078.audora.core.config.DialogueConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Feeling
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.SpeechEngine
import io.github.skm0078.audora.speech.SpeechError
import io.github.skm0078.audora.speech.audio.PcmAudio
import io.github.skm0078.audora.speech.normalize.TextNormalizer
import io.github.skm0078.audora.speech.normalize.TextRole

/** Each character's own voice (plan item 63): null keeps the [Cast.dialogue] voice for that speaker. */
fun interface CharacterVoices {
    fun voiceFor(lang: Lang, speaker: Speaker): VoiceId?

    /**
     * These voices as they stand now, taken before the sentence's speaker is looked up: a book opened in between
     * makes the snapshot give no voice (and keep nothing), so one book's character never lands in another's cast.
     */
    fun current(): CharacterVoices = this
}

/**
 * The voices that stand in for the narrator's, per language: [dialogue] reads quoted speech, [headings] headings. A
 * language missing from a map keeps the narrator's voice for that role. With [characters], a quote whose speaker is
 * known ([SpeechRequest.speaker]) is read in that character's voice, before the dialogue voice.
 */
data class Cast(
    val dialogue: Map<Lang, VoiceId> = emptyMap(),
    val headings: Map<Lang, VoiceId> = emptyMap(),
    val characters: CharacterVoices? = null,
) {
    val isEmpty: Boolean get() = dialogue.isEmpty() && headings.isEmpty() && characters == null
}

/**
 * How one sentence is spoken, beyond its text and language: the defaults of a plain read. [startsInQuote]: an earlier
 * sentence of its paragraph opened a quote that is still open ([QuoteSplitter.endsInQuote]). [speaker]: who says the
 * sentence's quotes, from its tag or the sentences around it ([SpeakerTags]); null when unknown. [feeling]: how the
 * sentence asks to be read (ADR-056), passed to a voice with speaking styles (ADR-057). [pitch]: that feeling's pitch
 * as a ratio (ADR-076), for voices without styles. [bookLanguage]: the book's language tag, which has the phone's own
 * voice read the letters of its script when it is none of the three [Lang]s ([ScriptRouter], ADR 2026-10-26); null
 * reads every part with the three languages' voices.
 */
@Suppress("LongParameterList") // All optional, with the defaults of a plain read.
class SpeechRequest(
    val role: TextRole = TextRole.BODY,
    val rate: Float = SpeechStyle.NORMAL_RATE,
    val prosody: ClauseProsody? = null,
    val shape: (PcmAudio) -> PcmAudio = { it },
    val startsInQuote: Boolean = false,
    val speaker: Speaker? = null,
    val feeling: Feeling = Feeling.NEUTRAL,
    val pitch: Float = 1f,
    val bookLanguage: String? = null,
) {
    /** The request for narration around a sentence's quotes: no feeling, the voice's own pitch. */
    fun plain(): SpeechRequest =
        SpeechRequest(role, rate, prosody, shape, startsInQuote, speaker, bookLanguage = bookLanguage)
}

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
        // An acting voice (ADR-059) reads the sentence whole, quotes and headings in its own voice.
        if (narratorEngine(lang)?.let { (engine, voice) -> engine.keepsSentencesWhole(voice) } == true) {
            return narrator.render(text, lang, request)
        }
        val heading = request.role == TextRole.HEADING && cast.headings.isNotEmpty()
        // Split only when the quotes get a voice of their own: pieces joined with pauses would change the prosody
        // of a sentence read by the narrator alone anyway (an unknown speaker with no dialogue voice, say).
        val dialogue = if (heading) emptyMap() else quoteVoices(lang, request, cast)
        // A voice that acts feelings (ADR-057) acts only the quoted words: "she sobbed" is said plainly, the plea in
        // its quotes sadly, as a reader does. The sentence is split for it even with no dialogue voice.
        // A feeling's pitch (ADR-076) is the same: the quotes are shifted, the narration around them is not.
        val acted = !heading && request.feeling != Feeling.NEUTRAL && (request.pitch != 1f || narratorActs(lang))
        val parts = if (lang in dialogue || acted) {
            quotes.split(text, request.startsInQuote).takeIf { split -> split.any { it.quoted } }
        } else {
            null
        }
        return when {
            heading -> castOrNarrator(text, lang, request, cast.headings)
            parts != null -> renderParts(parts, lang, request, dialogue, Join(quotePauseMs, level))
            else -> narrator.render(text, lang, request)
        }
    }

    /** Whether [lang]'s narrator voice reads feelings in a style of its own ([SpeechEngine.actsFeelings]). */
    private suspend fun narratorActs(lang: Lang): Boolean =
        narratorEngine(lang)?.let { (engine, voice) -> engine.actsFeelings(voice) } == true

    /** [lang]'s narrator voice and its engine; null when either cannot be had. */
    private suspend fun narratorEngine(lang: Lang): Pair<SpeechEngine, VoiceId>? =
        (voices.voiceFor(lang) as? Outcome.Success)?.value?.let { voice ->
            (voices.engine(voice.engine) as? Outcome.Success)?.value?.let { engine -> engine to voice }
        }

    /** The character's voice for this sentence's quotes when known, else the dialogue voice. */
    private fun quoteVoices(lang: Lang, request: SpeechRequest, cast: Cast): Map<Lang, VoiceId> {
        val character = request.speaker?.let { cast.characters?.voiceFor(lang, it) }
        return if (character != null) cast.dialogue + (lang to character) else cast.dialogue
    }

    private suspend fun renderParts(
        parts: List<QuotePart>,
        lang: Lang,
        request: SpeechRequest,
        dialogue: Map<Lang, VoiceId>,
        join: Join,
    ): Outcome<RoutedAudio, SpeechError> {
        val rendered = ArrayList<RoutedAudio>(parts.size)
        val narration = request.plain()
        for (part in parts) {
            val audio = if (part.quoted) {
                castOrNarrator(part.text, lang, request, dialogue)
            } else {
                narrator.render(part.text, lang, narration)
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

    override suspend fun phoneVoiceFor(language: String): Outcome<VoiceId, SpeechError> = base.phoneVoiceFor(language)

    override fun engine(id: EngineId): Outcome<SpeechEngine, SpeechError> = base.engine(id)
}

/** [RoutedSpeech.render] with a [SpeechRequest]. */
private suspend fun RoutedSpeech.render(text: String, lang: Lang, request: SpeechRequest) = render(
    text,
    lang,
    request.role,
    request.rate,
    request.prosody,
    request.shape,
    request.feeling,
    request.pitch,
    request.bookLanguage,
)
