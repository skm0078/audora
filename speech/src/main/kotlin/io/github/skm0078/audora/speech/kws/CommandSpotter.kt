// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/kws/CommandSpotter.kt
// Purpose: The installed voice-command model, run through sherpa-onnx's keyword spotter (plan item 38).

package io.github.skm0078.audora.speech.kws

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import io.github.skm0078.audora.core.config.VoiceCommandConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.File
import java.io.IOException

/**
 * One spotter and its stream, fed by the listener's thread only. [release] may come from another thread (the reader
 * leaving the screen), so both are synchronized and a call after it returns nothing.
 */
class CommandSpotter private constructor(
    private var spotter: KeywordSpotter?,
    private var stream: OnlineStream?,
    private val sampleRate: Int,
) : KeywordDecoder {
    /** Feeds [samples] (mono floats in -1..1 at the config's rate); the command heard, if one ended in them. */
    @Synchronized
    override fun accept(samples: FloatArray): VoiceCommand? {
        val kws = spotter
        val input = stream
        var heard: VoiceCommand? = null
        if (kws != null && input != null) {
            input.acceptWaveform(samples, sampleRate)
            while (kws.isReady(input)) {
                kws.decode(input)
                val keyword = kws.getResult(input).keyword
                if (keyword.isNotEmpty()) {
                    // Reset at once, so the phrase is not reported again from the frames still buffered.
                    kws.reset(input)
                    heard = VoiceCommand.of(keyword) ?: heard
                }
            }
        }
        return heard
    }

    @Synchronized
    fun release() {
        stream?.release()
        spotter?.release()
        stream = null
        spotter = null
    }

    companion object {
        private const val TAG = "Speech.Commands"
        private const val KEYWORDS_FILE = "audora-keywords.txt"
        private const val MODEL_TYPE = "zipformer2"

        /**
         * Loads the model from [dir] with this build's phrases (written beside it each time, so an app update's new
         * phrase is used without a new download); null (logged) when the files or sherpa-onnx refuse, or when a
         * phrase names a piece the model lacks ([KeywordTokens]: sherpa-onnx would end the process).
         */
        fun load(dir: File, model: KeywordModel, config: VoiceCommandConfig): CommandSpotter? = try {
            val unknown = File(dir, model.tokens).useLines { KeywordTokens.unknown(KeywordTokens.vocabulary(it)) }
            if (unknown.isEmpty()) {
                loadChecked(dir, model, config)
            } else {
                null.also {
                    AudoraLog.e(TAG, "Voice-command model ${model.id} lacks the pieces $unknown; not loaded")
                }
            }
        } catch (e: IOException) {
            AudoraLog.e(TAG, "Reading the tokens of voice-command model ${model.id} failed", e)
            null
        }

        private fun loadChecked(dir: File, model: KeywordModel, config: VoiceCommandConfig): CommandSpotter? = try {
            val keywords = File(dir, KEYWORDS_FILE)
            keywords.writeText(VoiceCommand.keywordsFile(), Charsets.UTF_8)
            val spotter = KeywordSpotter(null, spotterConfig(dir, model, keywords, config))
            CommandSpotter(spotter, spotter.createStream(), config.sampleRate).also {
                AudoraLog.i(TAG, "Loaded voice-command model ${model.id}")
            }
        } catch (e: IOException) {
            AudoraLog.e(TAG, "Writing the voice-command phrases for ${model.id} failed", e)
            null
        } catch (e: IllegalArgumentException) {
            AudoraLog.e(TAG, "Loading voice-command model ${model.id} failed", e)
            null
        }

        private fun spotterConfig(dir: File, model: KeywordModel, keywords: File, config: VoiceCommandConfig) =
            KeywordSpotterConfig(
                featConfig = FeatureConfig(sampleRate = config.sampleRate),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = File(dir, model.encoder).path,
                        decoder = File(dir, model.decoder).path,
                        joiner = File(dir, model.joiner).path,
                    ),
                    tokens = File(dir, model.tokens).path,
                    numThreads = config.numThreads,
                    modelType = MODEL_TYPE,
                ),
                keywordsFile = keywords.path,
                keywordsScore = config.keywordsScore,
                keywordsThreshold = config.keywordsThreshold,
            )
    }
}
