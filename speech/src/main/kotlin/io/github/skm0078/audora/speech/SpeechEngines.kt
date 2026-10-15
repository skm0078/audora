// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/SpeechEngines.kt
// Purpose: Builds the two Phase 1 engines (bundled neural voice, phone's TTS) from an Android Context.

package io.github.skm0078.audora.speech

import android.content.Context
import android.content.res.AssetManager
import io.github.skm0078.audora.core.config.SpeechConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.SpeechError.VoiceUnavailable.Reason
import io.github.skm0078.audora.speech.sherpa.AssetSherpaSynthesizerFactory
import io.github.skm0078.audora.speech.sherpa.SherpaSpeechEngine
import io.github.skm0078.audora.speech.sherpa.SherpaVoiceManifest
import io.github.skm0078.audora.speech.sherpa.SherpaVoiceSpec
import io.github.skm0078.audora.speech.system.AndroidSystemTtsClient
import io.github.skm0078.audora.speech.system.SystemSpeechEngine
import java.io.File
import java.io.IOException
import java.util.Properties

/** The bundled engine together with the voice it ships, which is the English default (ADR-012, ADR-014). */
class BundledSpeech(val engine: SherpaSpeechEngine, val voice: VoiceInfo)

object SpeechEngines {
    /** Where the build puts the bundled voice inside the APK's assets (speech/build.gradle.kts). */
    const val BUNDLED_ASSET_DIR = "voices/bundled"

    private const val TAG = "Speech.Engines"
    private const val BUNDLED_DATA_DIR = "voices/bundled"
    private const val SYSTEM_WORK_DIR = "tts-system"

    /**
     * The neural English voice packed into the APK. Cheap: reads one small properties file; the model loads on the
     * first render. Fails only when the APK was built without the voice, which is a build bug worth surfacing.
     */
    fun bundled(context: Context, config: SpeechConfig = SpeechConfig()): Outcome<BundledSpeech, SpeechError> {
        val assets = context.assets
        val spec = when (val read = readBundledSpec(assets)) {
            is Outcome.Success -> read.value

            is Outcome.Failure -> {
                val error = SpeechError.VoiceUnavailable(
                    EngineId.SHERPA,
                    Reason.MODEL_FILES_MISSING,
                    detail = read.error,
                )
                AudoraLog.e(TAG, error.message)
                return Outcome.Failure(error)
            }
        }
        val factory = AssetSherpaSynthesizerFactory(
            assets = assets,
            assetDir = BUNDLED_ASSET_DIR,
            dataRoot = File(context.noBackupFilesDir, BUNDLED_DATA_DIR),
            threads = config.sherpaThreads,
        )
        AudoraLog.i(TAG, "Bundled voice ${spec.voiceId} (${spec.family}, ${spec.lang})")
        return Outcome.Success(BundledSpeech(SherpaSpeechEngine(listOf(spec), factory), spec.toVoiceInfo()))
    }

    /** The phone's own TTS engine. Connects lazily on first use. */
    fun system(context: Context, config: SpeechConfig = SpeechConfig()): SystemSpeechEngine =
        SystemSpeechEngine(AndroidSystemTtsClient(context, File(context.cacheDir, SYSTEM_WORK_DIR), config))

    private fun readBundledSpec(assets: AssetManager): Outcome<SherpaVoiceSpec, String> {
        val path = "$BUNDLED_ASSET_DIR/${SherpaVoiceManifest.FILE_NAME}"
        val properties = try {
            assets.open(path).reader(Charsets.UTF_8).use { reader -> Properties().apply { load(reader) } }
        } catch (e: IOException) {
            return Outcome.Failure("assets/$path unreadable: ${e.message}")
        }
        return SherpaVoiceManifest.parse(properties)
    }
}
