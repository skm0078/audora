// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/sherpa/SherpaVoiceSpec.kt
// Purpose: Describes one sherpa-onnx voice (model family and file names) and parses it from a voice.properties file.

package io.github.skm0078.audora.speech.sherpa

import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.VoiceInfo
import java.util.Properties

/** The sherpa-onnx model families Audora can bundle or download (ADR-012). */
enum class SherpaFamily {
    /** Piper and MMS: model + tokens (+ espeak-ng-data for Piper). */
    VITS,

    /** KittenTTS: model + voices.bin + tokens + espeak-ng-data. */
    KITTEN,

    /** Kokoro (premium English, P4-02): model + voices.bin + tokens + espeak-ng-data + a lexicon, and a language. */
    KOKORO,

    /**
     * Supertonic (premium Hindi, P4-02): `model` names its `tts.json`; the four ONNX parts and the unicode indexer
     * sit beside it under fixed names ([SupertonicParts]); `voices` is the voice-style file. No tokens or espeak.
     */
    SUPERTONIC,
}

/** The files of a Supertonic voice besides `tts.json` and its voice style, as sherpa-onnx's archives name them. */
object SupertonicParts {
    const val DURATION_PREDICTOR = "duration_predictor.int8.onnx"
    const val TEXT_ENCODER = "text_encoder.int8.onnx"
    const val VECTOR_ESTIMATOR = "vector_estimator.int8.onnx"
    const val VOCODER = "vocoder.int8.onnx"
    const val UNICODE_INDEXER = "unicode_indexer.bin"
    val ALL = listOf(DURATION_PREDICTOR, TEXT_ENCODER, VECTOR_ESTIMATOR, VOCODER, UNICODE_INDEXER)
}

/** Piper's `noise_scale` (pitch and energy variation) and `noise_w` (length variation), set when a model loads. */
data class VitsNoise(val scale: Float, val width: Float)

/**
 * One voice's files, relative to its folder.
 *
 * @property contentVersion changes whenever the files change (the archive's SHA-256); decides when unpacked data
 *   such as espeak-ng-data must be refreshed.
 * @property lexicon Kokoro's pronunciation lexicon (P4-02), if the voice uses one.
 * @property engineLang the language code the model itself is told: Kokoro's `lang` ("en-us"), Supertonic's `lang`
 *   generation option ("hi"). Required for those two families.
 * @property numSteps Supertonic's flow-matching steps per render (speed against quality); null: sherpa's default.
 * @property noise a VITS voice's noise values (expressiveness, plan item 8); null: sherpa's defaults. Not in
 *   `voice.properties`: the engine's voice source sets it from the listener's style, and a change reloads the model,
 *   as any change to the spec does.
 * @property blend a Kokoro voice's narrator blend (plan item 75), set by the engine's voice source the same way;
 *   null: the voice as installed.
 */
data class SherpaVoiceSpec(
    val id: String,
    val label: String,
    val lang: Lang,
    val family: SherpaFamily,
    val model: String,
    val tokens: String,
    val voices: String?,
    val dataDir: String?,
    val speakerId: Int,
    val licence: String,
    val contentVersion: String,
    val lexicon: String? = null,
    val engineLang: String? = null,
    val numSteps: Int? = null,
    val noise: VitsNoise? = null,
    val blend: StyleBlend? = null,
) {
    val voiceId: VoiceId get() = VoiceId(EngineId.SHERPA, id)

    /** Single files that must exist before sherpa-onnx is handed the config (it aborts the process otherwise). */
    val requiredFiles: List<String> get() = when (family) {
        SherpaFamily.SUPERTONIC -> listOfNotNull(model, voices) + SupertonicParts.ALL
        else -> listOfNotNull(model, tokens, voices, lexicon)
    }

    fun toVoiceInfo(): VoiceInfo = VoiceInfo(voiceId, lang, label, licence)
}

/**
 * Reads the `voice.properties` the build writes next to a bundled voice (speech/build.gradle.kts) and that
 * downloaded voices will carry too (P4-01). Plain properties, so it parses on the JVM without Android's JSON.
 */
object SherpaVoiceManifest {
    const val FILE_NAME = "voice.properties"

    fun parse(properties: Properties): Outcome<SherpaVoiceSpec, String> {
        fun text(key: String): String? = properties.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }

        val missing = REQUIRED_KEYS.filter { text(it) == null }
        val lang = Lang.entries.firstOrNull { it.bcp47Tag == text(KEY_LANG) }
        val family = SherpaFamily.entries.firstOrNull { it.name.equals(text(KEY_FAMILY), ignoreCase = true) }
        val speakerId = (text(KEY_SPEAKER) ?: "0").toIntOrNull()?.takeIf { it >= 0 }
        val voices = text(KEY_VOICES)
        val engineLang = text(KEY_ENGINE_LANG)
        val numSteps = text(KEY_NUM_STEPS)?.let { raw -> raw.toIntOrNull()?.takeIf { it > 0 } ?: INVALID }
        val problem = when {
            missing.isNotEmpty() -> "is missing $missing"
            lang == null -> "has unknown lang '${text(KEY_LANG)}'"
            family == null -> "has unknown family '${text(KEY_FAMILY)}'"
            speakerId == null -> "has invalid speakerId '${text(KEY_SPEAKER)}'"
            numSteps == INVALID -> "has invalid $KEY_NUM_STEPS '${text(KEY_NUM_STEPS)}'"
            else -> familyProblem(family, voices, engineLang)
        }
        if (problem != null) return Outcome.Failure("$FILE_NAME $problem")

        return Outcome.Success(
            SherpaVoiceSpec(
                id = requireNotNull(text(KEY_ID)),
                label = requireNotNull(text(KEY_LABEL)),
                lang = requireNotNull(lang),
                family = requireNotNull(family),
                model = requireNotNull(text(KEY_MODEL)),
                tokens = text(KEY_TOKENS) ?: DEFAULT_TOKENS,
                voices = voices,
                dataDir = text(KEY_DATA_DIR),
                speakerId = requireNotNull(speakerId),
                licence = requireNotNull(text(KEY_LICENCE)),
                contentVersion = requireNotNull(text(KEY_CONTENT_VERSION)),
                lexicon = text(KEY_LEXICON),
                engineLang = engineLang,
                numSteps = numSteps,
            ),
        )
    }

    /** Kitten, Kokoro and Supertonic need a voices file; Kokoro and Supertonic need their model's language. */
    private fun familyProblem(family: SherpaFamily, voices: String?, engineLang: String?): String? {
        val name = family.name.lowercase()
        return when {
            family in NEEDS_VOICES && voices == null -> "needs '$KEY_VOICES' for a $name voice"
            family in NEEDS_ENGINE_LANG && engineLang == null -> "needs '$KEY_ENGINE_LANG' for a $name voice"
            else -> null
        }
    }

    private const val KEY_ID = "id"
    private const val KEY_LABEL = "label"
    private const val KEY_LANG = "lang"
    private const val KEY_FAMILY = "family"
    private const val KEY_MODEL = "model"
    private const val KEY_TOKENS = "tokens"
    private const val KEY_VOICES = "voices"
    private const val KEY_DATA_DIR = "dataDir"
    private const val KEY_SPEAKER = "speakerId"
    private const val KEY_LICENCE = "licence"
    private const val KEY_CONTENT_VERSION = "contentVersion"
    private const val KEY_LEXICON = "lexicon"
    private const val KEY_ENGINE_LANG = "engineLang"
    private const val KEY_NUM_STEPS = "numSteps"
    private const val INVALID = -1
    private val NEEDS_VOICES = setOf(SherpaFamily.KITTEN, SherpaFamily.KOKORO, SherpaFamily.SUPERTONIC)
    private val NEEDS_ENGINE_LANG = setOf(SherpaFamily.KOKORO, SherpaFamily.SUPERTONIC)
    private const val DEFAULT_TOKENS = "tokens.txt"
    private val REQUIRED_KEYS =
        listOf(KEY_ID, KEY_LABEL, KEY_LANG, KEY_FAMILY, KEY_MODEL, KEY_LICENCE, KEY_CONTENT_VERSION)
}
