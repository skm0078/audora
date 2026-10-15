// File: spikes/voices/src/main/kotlin/io/github/skm0078/audora/spike/voices/engine/SherpaEngine.kt
// Purpose: Runs one catalog voice through sherpa-onnx 1.13.8 (VITS/Piper/MMS, Kitten, Kokoro, Supertonic).

package io.github.skm0078.audora.spike.voices.engine

import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKittenModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.spike.voices.catalog.ModelFamily
import io.github.skm0078.audora.spike.voices.catalog.VoiceSpec
import java.io.File

class SherpaEngine(private val spec: VoiceSpec, private val modelDir: File, override val threads: Int) : BenchEngine {
    override val voiceId: String get() = spec.id
    override val label: String get() = spec.label
    override val lang: String get() = spec.lang
    override val details: List<String> get() = listOf("licence: ${spec.license}")

    private var tts: OfflineTts? = null

    override fun load() {
        // sherpa-onnx aborts the whole process on some missing files, so check them here and fail typed instead.
        val missing = requiredFiles().filterNot { it.exists() }
        if (missing.isNotEmpty()) throw EngineException("${spec.id}: missing model files ${missing.map { it.name }}")
        tts = try {
            OfflineTts(assetManager = null, config = OfflineTtsConfig(model = modelConfig(), maxNumSentences = 1))
        } catch (e: IllegalArgumentException) {
            throw EngineException("${spec.id}: sherpa-onnx rejected the config", e)
        }
        AudoraLog.i(TAG, "Loaded ${spec.id} with $threads threads, sample rate ${tts?.sampleRate()}")
    }

    override fun render(text: String): Rendered {
        val engine = tts ?: throw EngineException("${spec.id}: render before load")
        val config = GenerationConfig(
            sid = spec.sid,
            speed = 1f,
            numSteps = spec.numSteps,
            extra = if (spec.extraLang.isNotEmpty()) mapOf(EXTRA_LANG to spec.extraLang) else null,
        )
        val audio = engine.generateWithConfig(text, config)
        if (audio.samples.isEmpty()) throw EngineException("${spec.id}: produced no audio")
        return Rendered(WavFile.floatToPcm16(audio.samples), audio.sampleRate)
    }

    override fun release() {
        tts?.release()
        tts = null
    }

    private fun path(name: String) = if (name.isEmpty()) "" else File(modelDir, name).path

    private fun requiredFiles(): List<File> = when (spec.family) {
        ModelFamily.SUPERTONIC -> SUPERTONIC_FILES.map { File(modelDir, it) }

        else -> listOfNotNull(
            File(modelDir, spec.model),
            File(modelDir, TOKENS),
            spec.voices.takeIf { it.isNotEmpty() }?.let { File(modelDir, it) },
            spec.dataDir.takeIf { it.isNotEmpty() }?.let { File(modelDir, it) },
            spec.lexicon.takeIf { it.isNotEmpty() }?.let { File(modelDir, it) },
        )
    }

    private fun modelConfig(): OfflineTtsModelConfig {
        val base = OfflineTtsModelConfig(numThreads = threads, debug = false, provider = PROVIDER)
        return when (spec.family) {
            ModelFamily.VITS -> base.copy(
                vits = OfflineTtsVitsModelConfig(
                    model = path(spec.model),
                    tokens = path(TOKENS),
                    dataDir = path(spec.dataDir),
                ),
            )

            ModelFamily.KITTEN -> base.copy(
                kitten = OfflineTtsKittenModelConfig(
                    model = path(spec.model),
                    voices = path(spec.voices),
                    tokens = path(TOKENS),
                    dataDir = path(spec.dataDir),
                ),
            )

            ModelFamily.KOKORO -> base.copy(
                kokoro = OfflineTtsKokoroModelConfig(
                    model = path(spec.model),
                    voices = path(spec.voices),
                    tokens = path(TOKENS),
                    dataDir = path(spec.dataDir),
                    lexicon = path(spec.lexicon),
                    lang = spec.kokoroLang,
                ),
            )

            ModelFamily.SUPERTONIC -> base.copy(
                supertonic = OfflineTtsSupertonicModelConfig(
                    durationPredictor = path(SUPERTONIC_DURATION),
                    textEncoder = path(SUPERTONIC_ENCODER),
                    vectorEstimator = path(SUPERTONIC_ESTIMATOR),
                    vocoder = path(SUPERTONIC_VOCODER),
                    ttsJson = path(SUPERTONIC_JSON),
                    unicodeIndexer = path(SUPERTONIC_INDEXER),
                    voiceStyle = path(SUPERTONIC_VOICE),
                ),
            )
        }
    }

    private companion object {
        const val TAG = "S1.Sherpa"
        const val TOKENS = "tokens.txt"
        const val PROVIDER = "cpu"
        const val EXTRA_LANG = "lang"

        const val SUPERTONIC_DURATION = "duration_predictor.int8.onnx"
        const val SUPERTONIC_ENCODER = "text_encoder.int8.onnx"
        const val SUPERTONIC_ESTIMATOR = "vector_estimator.int8.onnx"
        const val SUPERTONIC_VOCODER = "vocoder.int8.onnx"
        const val SUPERTONIC_JSON = "tts.json"
        const val SUPERTONIC_INDEXER = "unicode_indexer.bin"
        const val SUPERTONIC_VOICE = "voice.bin"
        val SUPERTONIC_FILES = listOf(
            SUPERTONIC_DURATION,
            SUPERTONIC_ENCODER,
            SUPERTONIC_ESTIMATOR,
            SUPERTONIC_VOCODER,
            SUPERTONIC_JSON,
            SUPERTONIC_INDEXER,
            SUPERTONIC_VOICE,
        )
    }
}
