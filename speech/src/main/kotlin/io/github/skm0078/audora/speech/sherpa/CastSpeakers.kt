// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/sherpa/CastSpeakers.kt
// Purpose: The other speakers inside multi-speaker voice models, so characters can each have one (plan item 63).

package io.github.skm0078.audora.speech.sherpa

import io.github.skm0078.audora.core.model.VoiceId

enum class SpeakerGender { FEMALE, MALE, UNKNOWN }

/** One speaker of a model: its sherpa-onnx speaker id, a short name for the screen, and how it sounds. */
data class CastSpeaker(val speakerId: Int, val label: String, val gender: SpeakerGender)

/**
 * A voice id can name another speaker of the same model: `kokoro-en-heart#11` is speaker 11 (Adam) of the Kokoro
 * model the `kokoro-en-heart` voice installed. The engine loads the model once and picks the speaker per render, so
 * a cast of characters costs no more memory than one voice. Only speakers listed here are ever used: a speaker id
 * past a model's range is undefined behaviour in sherpa-onnx.
 *
 * The lists are the models' own speaker tables (sherpa-onnx's packaging of Kokoro v1.0 multi-lang and KittenTTS
 * v0.8 nano), English speakers only; a model not listed has one speaker.
 */
object CastSpeakers {
    private const val SEPARATOR = '#'

    /** Kokoro v1.0 multi-lang: the American (af_, am_) and British (bf_, bm_) English speakers. */
    private val KOKORO_ENGLISH = listOf(
        "Alloy", "Aoede", "Bella", "Heart", "Jessica", "Kore", "Nicole", "Nova", "River", "Sarah", "Sky",
    ).mapIndexed { i, name -> CastSpeaker(i, name, SpeakerGender.FEMALE) } +
        listOf("Adam", "Echo", "Eric", "Fenrir", "Liam", "Michael", "Onyx", "Puck", "Santa")
            .mapIndexed { i, name -> CastSpeaker(KOKORO_FIRST_MALE + i, name, SpeakerGender.MALE) } +
        listOf("Alice", "Emma", "Isabella", "Lily")
            .mapIndexed { i, name -> CastSpeaker(KOKORO_FIRST_BRITISH + i, name, SpeakerGender.FEMALE) } +
        listOf("Daniel", "Fable", "George", "Lewis")
            .mapIndexed { i, name -> CastSpeaker(KOKORO_FIRST_BRITISH_MALE + i, name, SpeakerGender.MALE) }

    /** KittenTTS v0.8 nano: four voices, each male then female. */
    private val KITTEN = (0 until KITTEN_SPEAKERS).map { i ->
        val gender = if (i % 2 == 0) SpeakerGender.MALE else SpeakerGender.FEMALE
        CastSpeaker(i, "Kitten ${i / 2 + KITTEN_FIRST_NUMBER}${if (gender == SpeakerGender.MALE) "M" else "F"}", gender)
    }

    /** Kokoro's English speakers, for the narrator blend's choices (plan item 75). */
    val kokoroEnglish: List<CastSpeaker> get() = KOKORO_ENGLISH

    /** The speakers [spec]'s model holds; empty for a one-speaker model. */
    fun of(spec: SherpaVoiceSpec): List<CastSpeaker> = when {
        spec.family == SherpaFamily.KOKORO && spec.engineLang?.startsWith(ENGLISH) == true -> KOKORO_ENGLISH
        spec.family == SherpaFamily.KITTEN -> KITTEN
        else -> emptyList()
    }

    /** [base]'s model read by its speaker [speakerId]. */
    fun variant(base: VoiceId, speakerId: Int): VoiceId = VoiceId(base.engine, "${base.name}$SEPARATOR$speakerId")

    /** The voice a [voice] id names and the speaker it asks for; null when it asks for none. */
    fun parse(voice: VoiceId): Pair<VoiceId, Int?> {
        val cut = voice.name.lastIndexOf(SEPARATOR)
        val speaker = voice.name.substring(cut + 1).toIntOrNull()?.takeIf { cut > 0 && it >= 0 }
            ?: return voice to null
        return VoiceId(voice.engine, voice.name.substring(0, cut)) to speaker
    }

    /** The speaker id [spec] reads with for [requested]: a listed one, else the voice's own. */
    fun speakerFor(spec: SherpaVoiceSpec, requested: Int?): Int =
        requested?.takeIf { id -> of(spec).any { it.speakerId == id } } ?: spec.speakerId

    private const val ENGLISH = "en"
    private const val KOKORO_FIRST_MALE = 11
    private const val KOKORO_FIRST_BRITISH = 20
    private const val KOKORO_FIRST_BRITISH_MALE = 24
    private const val KITTEN_SPEAKERS = 8
    private const val KITTEN_FIRST_NUMBER = 2
}
