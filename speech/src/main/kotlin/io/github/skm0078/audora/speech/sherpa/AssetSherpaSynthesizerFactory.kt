// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/sherpa/AssetSherpaSynthesizerFactory.kt
// Purpose: Thin adapter that loads a voice packed in the APK's assets into sherpa-onnx's OfflineTts.

package io.github.skm0078.audora.speech.sherpa

import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKittenModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
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
            Outcome.Success(OfflineTtsSynthesizer(OfflineTts(assetManager = assets, config = config), spec))
        } catch (e: IllegalArgumentException) {
            Outcome.Failure(SpeechError.SynthesisFailed(spec.voiceId, "sherpa-onnx rejected the model config", e))
        } catch (e: UnsatisfiedLinkError) {
            Outcome.Failure(SpeechError.EngineUnavailable(EngineId.SHERPA, "native library not loaded: ${e.message}"))
        }
    }

    private fun modelConfig(spec: SherpaVoiceSpec, dataDir: String): OfflineTtsModelConfig =
        sherpaModelConfig(spec, threads, dataDir, ::asset)

    private fun asset(name: String) = "$assetDir/$name"

    private fun missing(spec: SherpaVoiceSpec, detail: String): Outcome.Failure<SpeechError> = Outcome.Failure(
        SpeechError.VoiceUnavailable(EngineId.SHERPA, Reason.MODEL_FILES_MISSING, spec.voiceId, spec.lang, detail),
    )
}

/** sherpa-onnx's model config for [spec]; [path] turns a file name from the voice's folder into what sherpa opens. */
internal fun sherpaModelConfig(
    spec: SherpaVoiceSpec,
    threads: Int,
    dataDir: String,
    path: (String) -> String,
): OfflineTtsModelConfig {
    val base = OfflineTtsModelConfig(numThreads = threads, debug = false, provider = SHERPA_PROVIDER)
    return when (spec.family) {
        SherpaFamily.VITS -> base.copy(
            vits = OfflineTtsVitsModelConfig(model = path(spec.model), tokens = path(spec.tokens), dataDir = dataDir),
        )

        SherpaFamily.KITTEN -> base.copy(
            kitten = OfflineTtsKittenModelConfig(
                model = path(spec.model),
                voices = path(requireNotNull(spec.voices) { "kitten voice without voices file" }),
                tokens = path(spec.tokens),
                dataDir = dataDir,
            ),
        )

        SherpaFamily.KOKORO -> base.copy(
            kokoro = OfflineTtsKokoroModelConfig(
                model = path(spec.model),
                voices = path(requireNotNull(spec.voices) { "kokoro voice without voices file" }),
                tokens = path(spec.tokens),
                dataDir = dataDir,
                lexicon = spec.lexicon?.let(path).orEmpty(),
                lang = requireNotNull(spec.engineLang) { "kokoro voice without engineLang" },
            ),
        )

        SherpaFamily.SUPERTONIC -> base.copy(
            supertonic = OfflineTtsSupertonicModelConfig(
                durationPredictor = path(SupertonicParts.DURATION_PREDICTOR),
                textEncoder = path(SupertonicParts.TEXT_ENCODER),
                vectorEstimator = path(SupertonicParts.VECTOR_ESTIMATOR),
                vocoder = path(SupertonicParts.VOCODER),
                ttsJson = path(spec.model),
                unicodeIndexer = path(SupertonicParts.UNICODE_INDEXER),
                voiceStyle = path(requireNotNull(spec.voices) { "supertonic voice without voice style" }),
            ),
        )
    }
}

/**
 * Per-render options for [spec]: Supertonic is told its language and step count on every call (it is one
 * multilingual model); the other families ignore both.
 */
internal fun sherpaGenerationConfig(spec: SherpaVoiceSpec, speakerId: Int): GenerationConfig {
    val defaults = GenerationConfig()
    val extra = spec.engineLang?.takeIf { spec.family == SherpaFamily.SUPERTONIC }?.let { mapOf(EXTRA_LANG to it) }
    return GenerationConfig(
        sid = speakerId,
        speed = 1f,
        numSteps = spec.numSteps ?: defaults.numSteps,
        extra = extra ?: defaults.extra,
    )
}

private const val SHERPA_PROVIDER = "cpu"
private const val EXTRA_LANG = "lang"

internal class OfflineTtsSynthesizer(private val tts: OfflineTts, private val spec: SherpaVoiceSpec) :
    SherpaSynthesizer {
    override val sampleRate: Int = tts.sampleRate()

    override fun generate(text: String, speakerId: Int, keepGoing: () -> Boolean): FloatArray {
        val config = sherpaGenerationConfig(spec, speakerId)
        return tts.generateWithConfigAndCallback(text, config, SherpaStopCallback(keepGoing)).samples
    }

    override fun release() = tts.release()
}

private class AndroidAssetTree(private val assets: AssetManager) : AssetTree {
    override fun list(path: String): List<String> = assets.list(path).orEmpty().toList()

    override fun open(path: String) = assets.open(path)
}
