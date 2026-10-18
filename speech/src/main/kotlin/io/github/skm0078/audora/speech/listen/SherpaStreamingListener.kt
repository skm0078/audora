// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/listen/SherpaStreamingListener.kt
// Purpose: A word-by-word listener over a streaming Zipformer transducer in sherpa-onnx (English, Bengali; item 67).

package io.github.skm0078.audora.speech.listen

import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import io.github.skm0078.audora.core.config.TutorConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.File

/**
 * Decodes as samples arrive and reports the growing stretch; sherpa-onnx's endpoint rules (a pause after speech, a
 * long quiet, a cap on length; [TutorConfig]) end a stretch, which is then reported final and the stream reset.
 */
class SherpaStreamingListener private constructor(
    private var recognizer: OnlineRecognizer?,
    private var stream: OnlineStream?,
    private val sampleRate: Int,
) : Listener {
    private var last = ""

    override fun accept(samples: FloatArray): List<Heard> {
        val model = recognizer
        val current = stream
        if (model == null || current == null) return emptyList()
        current.acceptWaveform(samples, sampleRate)
        while (model.isReady(current)) model.decode(current)
        val text = model.getResult(current).text.trim()
        return when {
            model.isEndpoint(current) -> {
                model.reset(current)
                last = ""
                if (text.isEmpty()) emptyList() else listOf(Heard(text, final = true))
            }

            text != last -> {
                last = text
                listOf(Heard(text, final = false))
            }

            else -> emptyList()
        }
    }

    override fun finish(): List<Heard> {
        val model = recognizer
        val current = stream
        if (model == null || current == null) return emptyList()
        current.inputFinished()
        while (model.isReady(current)) model.decode(current)
        val text = model.getResult(current).text.trim()
        model.reset(current)
        last = ""
        return if (text.isEmpty()) emptyList() else listOf(Heard(text, final = true))
    }

    override fun close() {
        stream?.release()
        recognizer?.release()
        stream = null
        recognizer = null
    }

    companion object {
        private const val TAG = "Speech.Streaming"
        private const val FEATURE_DIM = 80

        private fun features(config: TutorConfig) =
            FeatureConfig(sampleRate = config.sampleRateHz, featureDim = FEATURE_DIM, dither = 0f)
        private const val NO_LENGTH_RULE = 0f

        /** Loads the model in [dir]; null (logged) when sherpa-onnx refuses it, e.g. a damaged file. */
        fun open(dir: File, config: TutorConfig): SherpaStreamingListener? = try {
            val model = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = File(dir, "encoder.onnx").path,
                    decoder = File(dir, "decoder.onnx").path,
                    joiner = File(dir, "joiner.onnx").path,
                ),
                tokens = File(dir, "tokens.txt").path,
                numThreads = config.numThreads,
            )
            val endpoints = EndpointConfig(
                // Quiet with nothing said, a pause after speech, and a cap on one stretch.
                rule1 = EndpointRule(false, config.endpointQuietSec, NO_LENGTH_RULE),
                rule2 = EndpointRule(true, config.endpointSilenceSec, NO_LENGTH_RULE),
                rule3 = EndpointRule(false, 0f, config.endpointMaxSec),
            )
            val recognizer = OnlineRecognizer(
                null,
                OnlineRecognizerConfig(
                    featConfig = features(config),
                    modelConfig = model,
                    endpointConfig = endpoints,
                    enableEndpoint = true,
                ),
            )
            val stream = try {
                recognizer.createStream()
            } catch (e: IllegalStateException) {
                recognizer.release()
                throw e
            }
            AudoraLog.i(TAG, "Loaded a streaming speech model")
            SherpaStreamingListener(recognizer, stream, config.sampleRateHz)
        } catch (e: IllegalArgumentException) {
            AudoraLog.e(TAG, "Streaming speech model not loaded: ${e.javaClass.simpleName}")
            null
        } catch (e: IllegalStateException) {
            AudoraLog.e(TAG, "Streaming speech model not loaded: ${e.javaClass.simpleName}")
            null
        }
    }
}
