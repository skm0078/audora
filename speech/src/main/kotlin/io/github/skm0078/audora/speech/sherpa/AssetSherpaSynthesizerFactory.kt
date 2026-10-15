// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/sherpa/AssetSherpaSynthesizerFactory.kt
// Purpose: Thin adapter that loads a voice packed in the APK's assets into sherpa-onnx's OfflineTts.

package io.github.skm0078.audora.speech.sherpa

import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKittenModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.flatMap
import io.github.skm0078.audora.speech.SpeechError
import io.github.skm0078.audora.speech.SpeechError.VoiceUnavailable.Reason
import java.io.File
import java.io.IOException

/**
 * Loads voices from `assets/<assetDir>/`. Weights and tokens are read through the AssetManager (no second copy on
 * disk); espeak-ng-data is copied to `<dataRoot>/<dataDir>` once per voice version, because espeak-ng only reads
 * real files.
 */
class AssetSherpaSynthesizerFactory(
    private val assets: AssetManager,
    private val assetDir: String,
    private val dataRoot: File,
    private val threads: Int,
) : SherpaSynthesizerFactory {
    override fun load(spec: SherpaVoiceSpec): Outcome<SherpaSynthesizer, SpeechError> =
        checkFiles(spec).flatMap { installData(spec) }.flatMap { dataDir -> create(spec, dataDir) }

    /** sherpa-onnx aborts the whole process on some missing files, so check them here and fail typed instead. */
    private fun checkFiles(spec: SherpaVoiceSpec): Outcome<Unit, SpeechError> = try {
        val present = assets.list(assetDir).orEmpty().toSet()
        val absent = (spec.requiredFiles + listOfNotNull(spec.dataDir)).filterNot { it in present }
        if (absent.isEmpty()) Outcome.Success(Unit) else missing(spec, "assets/$assetDir lacks $absent")
    } catch (e: IOException) {
        missing(spec, "cannot list assets/$assetDir: ${e.message}")
    }

    /** Copies espeak-ng-data out of the APK once per voice version; returns its path ("" when the voice has none). */
    private fun installData(spec: SherpaVoiceSpec): Outcome<String, SpeechError> {
        val name = spec.dataDir ?: return Outcome.Success("")
        val installer = AssetDirectoryInstaller(AndroidAssetTree(assets))
        return when (val installed = installer.install("$assetDir/$name", File(dataRoot, name), spec.contentVersion)) {
            is Outcome.Success -> Outcome.Success(installed.value.path)
            is Outcome.Failure -> missing(spec, installed.error)
        }
    }

    private fun create(spec: SherpaVoiceSpec, dataDir: String): Outcome<SherpaSynthesizer, SpeechError> {
        val config = OfflineTtsConfig(model = modelConfig(spec, dataDir), maxNumSentences = 1)
        return try {
            Outcome.Success(OfflineTtsSynthesizer(OfflineTts(assetManager = assets, config = config)))
        } catch (e: IllegalArgumentException) {
            Outcome.Failure(SpeechError.SynthesisFailed(spec.voiceId, "sherpa-onnx rejected the model config", e))
        } catch (e: UnsatisfiedLinkError) {
            Outcome.Failure(SpeechError.EngineUnavailable(EngineId.SHERPA, "native library not loaded: ${e.message}"))
        }
    }

    private fun modelConfig(spec: SherpaVoiceSpec, dataDir: String): OfflineTtsModelConfig {
        val base = OfflineTtsModelConfig(numThreads = threads, debug = false, provider = PROVIDER)
        return when (spec.family) {
            SherpaFamily.VITS -> base.copy(
                vits = OfflineTtsVitsModelConfig(
                    model = asset(spec.model),
                    tokens = asset(spec.tokens),
                    dataDir = dataDir,
                ),
            )

            SherpaFamily.KITTEN -> base.copy(
                kitten = OfflineTtsKittenModelConfig(
                    model = asset(spec.model),
                    voices = asset(requireNotNull(spec.voices) { "kitten voice without voices file" }),
                    tokens = asset(spec.tokens),
                    dataDir = dataDir,
                ),
            )
        }
    }

    private fun asset(name: String) = "$assetDir/$name"

    private fun missing(spec: SherpaVoiceSpec, detail: String): Outcome.Failure<SpeechError> = Outcome.Failure(
        SpeechError.VoiceUnavailable(EngineId.SHERPA, Reason.MODEL_FILES_MISSING, spec.voiceId, spec.lang, detail),
    )

    private companion object {
        const val PROVIDER = "cpu"
    }
}

private class OfflineTtsSynthesizer(private val tts: OfflineTts) : SherpaSynthesizer {
    override val sampleRate: Int = tts.sampleRate()

    override fun generate(text: String, speakerId: Int, keepGoing: () -> Boolean): FloatArray {
        val config = GenerationConfig(sid = speakerId, speed = 1f)
        return tts.generateWithConfigAndCallback(text, config) { _ -> if (keepGoing()) CONTINUE else STOP }.samples
    }

    override fun release() = tts.release()

    private companion object {
        const val CONTINUE = 1
        const val STOP = 0
    }
}

private class AndroidAssetTree(private val assets: AssetManager) : AssetTree {
    override fun list(path: String): List<String> = assets.list(path).orEmpty().toList()

    override fun open(path: String) = assets.open(path)
}
