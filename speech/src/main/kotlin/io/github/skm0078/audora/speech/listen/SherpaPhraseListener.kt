// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/listen/SherpaPhraseListener.kt
// Purpose: A phrase-by-phrase listener: Silero VAD cuts phrases, Dolphin's CTC model reads each (Hindi; item 67).

package io.github.skm0078.audora.speech.listen

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineDolphinModelConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import io.github.skm0078.audora.core.config.TutorConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.File

/**
 * No free streaming Hindi model exists (ADR-050), so the voice detector waits for a pause and the whole phrase is read
 * at once: feedback comes a phrase at a time (capped at `vadMaxSpeechSec`), always final. The detector takes whole
 * windows of `vadWindowSamples`, so samples are buffered to that size.
 */
class SherpaPhraseListener private constructor(
    private var vad: Vad?,
    private var recognizer: OfflineRecognizer?,
    private val sampleRate: Int,
    private val window: Int,
) : Listener {
    private var pending = FloatArray(0)

    override fun accept(samples: FloatArray): List<Heard> {
        val detector = vad ?: return emptyList()
        pending += samples
        var offset = 0
        while (pending.size - offset >= window) {
            detector.acceptWaveform(pending.copyOfRange(offset, offset + window))
            offset += window
        }
        pending = pending.copyOfRange(offset, pending.size)
        return phrases(detector)
    }

    override fun finish(): List<Heard> {
        val detector = vad ?: return emptyList()
        detector.flush()
        pending = FloatArray(0)
        return phrases(detector).also { detector.reset() }
    }

    override fun close() {
        vad?.release()
        recognizer?.release()
        vad = null
        recognizer = null
    }

    private fun phrases(detector: Vad): List<Heard> = buildList {
        val model = recognizer ?: return@buildList
        while (!detector.empty()) {
            val segment = detector.front()
            detector.pop()
            val stream = model.createStream()
            try {
                stream.acceptWaveform(segment.samples, sampleRate)
                model.decode(stream)
                model.getResult(stream).text.trim().takeIf { it.isNotEmpty() }?.let { add(Heard(it, final = true)) }
            } finally {
                stream.release()
            }
        }
    }

    companion object {
        private const val TAG = "Speech.Phrase"
        private const val FEATURE_DIM = 80

        private fun features(config: TutorConfig) =
            FeatureConfig(sampleRate = config.sampleRateHz, featureDim = FEATURE_DIM, dither = 0f)

        /** Loads the detector and model in [dir]; null (logged) when sherpa-onnx refuses either. */
        fun open(dir: File, config: TutorConfig): SherpaPhraseListener? {
            var vad: Vad? = null
            return try {
                vad = Vad(
                    null,
                    VadModelConfig(
                        sileroVadModelConfig = SileroVadModelConfig(
                            model = File(dir, "vad.onnx").path,
                            threshold = config.vadThreshold,
                            minSilenceDuration = config.vadMinSilenceSec,
                            minSpeechDuration = config.vadMinSpeechSec,
                            windowSize = config.vadWindowSamples,
                            maxSpeechDuration = config.vadMaxSpeechSec,
                        ),
                        sampleRate = config.sampleRateHz,
                        numThreads = 1,
                    ),
                )
                val recognizer = OfflineRecognizer(
                    null,
                    OfflineRecognizerConfig(
                        featConfig = features(config),
                        modelConfig = OfflineModelConfig(
                            dolphin = OfflineDolphinModelConfig(model = File(dir, "model.onnx").path),
                            tokens = File(dir, "tokens.txt").path,
                            numThreads = config.numThreads,
                        ),
                    ),
                )
                AudoraLog.i(TAG, "Loaded a phrase speech model")
                SherpaPhraseListener(vad, recognizer, config.sampleRateHz, config.vadWindowSamples)
            } catch (e: IllegalArgumentException) {
                vad?.release()
                AudoraLog.e(TAG, "Phrase speech model not loaded: ${e.javaClass.simpleName}")
                null
            } catch (e: IllegalStateException) {
                vad?.release()
                AudoraLog.e(TAG, "Phrase speech model not loaded: ${e.javaClass.simpleName}")
                null
            }
        }
    }
}
