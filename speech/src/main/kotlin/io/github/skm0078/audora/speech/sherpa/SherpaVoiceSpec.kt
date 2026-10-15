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
}

/**
 * One voice's files, relative to its folder.
 *
 * @property contentVersion changes whenever the files change (the archive's SHA-256); decides when unpacked data
 *   such as espeak-ng-data must be refreshed.
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
) {
    val voiceId: VoiceId get() = VoiceId(EngineId.SHERPA, id)

    /** Single files that must exist before sherpa-onnx is handed the config (it aborts the process otherwise). */
    val requiredFiles: List<String> get() = listOfNotNull(model, tokens, voices)

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
        val problem = when {
            missing.isNotEmpty() -> "is missing $missing"
            lang == null -> "has unknown lang '${text(KEY_LANG)}'"
            family == null -> "has unknown family '${text(KEY_FAMILY)}'"
            speakerId == null -> "has invalid speakerId '${text(KEY_SPEAKER)}'"
            family == SherpaFamily.KITTEN && voices == null -> "needs '$KEY_VOICES' for a kitten voice"
            else -> null
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
            ),
        )
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
    private const val DEFAULT_TOKENS = "tokens.txt"
    private val REQUIRED_KEYS =
        listOf(KEY_ID, KEY_LABEL, KEY_LANG, KEY_FAMILY, KEY_MODEL, KEY_LICENCE, KEY_CONTENT_VERSION)
}
