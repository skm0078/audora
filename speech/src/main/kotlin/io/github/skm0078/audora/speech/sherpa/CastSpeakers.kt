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
 * v0.8 nano and micro, which share one table), English speakers only, and Rasa's two Bengali speakers. They are
 * keyed on the catalog voice that installs that exact archive, never on the model family: a voice imported from
 * any folder, or a family's other packaging, reads with its own speaker only.
 * A test pins each key to its archive in models.json and bundled-voices.json, so a catalog update that swaps the
 * archive under a keyed id fails the build; [tableFits] also checks the loaded model's speaker count at runtime.
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

    /**
     * KittenTTS v0.8: four voices, each male then female (the model's `expr-voice-2-m` … `expr-voice-5-f`), named as
     * KittenTTS names them.
     */
    private val KITTEN = listOf("Jasper", "Bella", "Bruno", "Luna", "Hugo", "Rosie", "Leo", "Kiki")
        .mapIndexed { i, name -> CastSpeaker(i, name, if (i % 2 == 0) SpeakerGender.MALE else SpeakerGender.FEMALE) }

    /**
     * AI4Bharat Rasa (ADR-057): its two Bengali speakers among the model's 1024 slots (the other languages' speakers
     * are left out), so the female narrator's characters can be read by the male speaker and back.
     */
    private val RASA_BENGALI = listOf(
        CastSpeaker(RASA_BENGALI_FEMALE, "Bengali female", SpeakerGender.FEMALE),
        CastSpeaker(RASA_BENGALI_MALE, "Bengali male", SpeakerGender.MALE),
    )

    /** Kokoro's English speakers, for the narrator blend's choices (plan item 75). */
    val kokoroEnglish: List<CastSpeaker> get() = KOKORO_ENGLISH

    /** Each keyed catalog voice's table: the archive it was written for, its speakers, the model's speaker count. */
    private val TABLES = mapOf(
        KOKORO_VOICE to CastTable(KOKORO_ARCHIVE, KOKORO_ENGLISH, KOKORO_MODEL_SPEAKERS),
        KITTEN_VOICE to CastTable(KITTEN_ARCHIVE, KITTEN, KITTEN_SPEAKERS),
        KITTEN_MICRO_VOICE to CastTable(KITTEN_MICRO_ARCHIVE, KITTEN, KITTEN_SPEAKERS),
        RASA_VOICE to CastTable(RASA_ARCHIVE, RASA_BENGALI, RASA_MODEL_SPEAKERS),
    )

    /** The archive each keyed catalog voice id was written for, so a test can check the catalogs still ship it. */
    internal val archives: Map<String, String> get() = TABLES.mapValues { it.value.archive }

    /** The speakers [spec]'s model holds; empty for a one-speaker model or a voice no table is keyed on. */
    fun of(spec: SherpaVoiceSpec): List<CastSpeaker> = TABLES[spec.id]?.speakers.orEmpty()

    /** [base]'s model read by its speaker [speakerId]. */
    fun variant(base: VoiceId, speakerId: Int): VoiceId = VoiceId(base.engine, "${base.name}$SEPARATOR$speakerId")

    /** The voice a [voice] id names and the speaker it asks for; null when it asks for none. */
    fun parse(voice: VoiceId): Pair<VoiceId, Int?> {
        val cut = voice.name.lastIndexOf(SEPARATOR)
        val speaker = voice.name.substring(cut + 1).toIntOrNull()?.takeIf { cut > 0 && it >= 0 }
            ?: return voice to null
        return VoiceId(voice.engine, voice.name.substring(0, cut)) to speaker
    }

    /**
     * Whether [spec]'s speaker table describes the model as loaded: a model swapped under a keyed voice (Kokoro v1.1
     * holds 103 speakers, in another order) would otherwise read a character with whichever speaker sits at that id.
     * True when the model's count is unknown ([loadedSpeakers] null) or no table is keyed on [spec].
     */
    fun tableFits(spec: SherpaVoiceSpec, loadedSpeakers: Int?): Boolean {
        val table = TABLES[spec.id]
        return loadedSpeakers == null || table == null || table.modelSpeakers == loadedSpeakers
    }

    /**
     * The speaker id [spec] reads with for [requested]: a listed one, else the voice's own; also the voice's own when
     * the loaded model holds [loadedSpeakers] speakers, not the count its table was written for ([tableFits]).
     */
    fun speakerFor(spec: SherpaVoiceSpec, requested: Int?, loadedSpeakers: Int? = null): Int =
        requested?.takeIf { id -> tableFits(spec, loadedSpeakers) && of(spec).any { it.speakerId == id } }
            ?: spec.speakerId

    /** [modelSpeakers]: how many speakers in all (every language) the model in [archive] holds. */
    private class CastTable(val archive: String, val speakers: List<CastSpeaker>, val modelSpeakers: Int)

    private const val KOKORO_VOICE = "kokoro-en-heart"
    private const val KOKORO_ARCHIVE = "kokoro-int8-multi-lang-v1_0"
    private const val KITTEN_VOICE = "kitten-en-nano"
    private const val KITTEN_ARCHIVE = "kitten-nano-en-v0_8-int8"

    /** The bundled English voice (ADR-052). */
    private const val KITTEN_MICRO_VOICE = "kitten-en-micro"
    private const val KITTEN_MICRO_ARCHIVE = "kitten-micro-en-v0_8"
    private const val RASA_VOICE = "rasa-bn"
    private const val RASA_ARCHIVE = "sherpa-onnx-vits-rasa-13"
    private const val RASA_BENGALI_FEMALE = 2
    private const val RASA_BENGALI_MALE = 3
    private const val RASA_MODEL_SPEAKERS = 1024
    private const val KOKORO_FIRST_MALE = 11
    private const val KOKORO_FIRST_BRITISH = 20
    private const val KOKORO_FIRST_BRITISH_MALE = 24
    private const val KITTEN_SPEAKERS = 8

    /** Kokoro v1.0 multi-lang's speakers in all languages; its English ones are the first [KOKORO_ENGLISH]. */
    private const val KOKORO_MODEL_SPEAKERS = 53
}
