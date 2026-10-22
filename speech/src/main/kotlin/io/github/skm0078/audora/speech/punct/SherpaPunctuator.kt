// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/punct/SherpaPunctuator.kt
// Purpose: The installed punctuation model, run through sherpa-onnx's online (CNN-BiLSTM) punctuation API (RI-11).

package io.github.skm0078.audora.speech.punct

import com.k2fsa.sherpa.onnx.OnlinePunctuation
import com.k2fsa.sherpa.onnx.OnlinePunctuationConfig
import com.k2fsa.sherpa.onnx.OnlinePunctuationModelConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import java.io.File

/**
 * Called on the render thread, released from Settings' delete on another: both are synchronized, so the native
 * object is never freed mid-call, and a call after [release] fails cleanly (the text is then read as written).
 */
class SherpaPunctuator private constructor(private var native: OnlinePunctuation?) : Punctuator {
    @Synchronized
    override fun punctuate(text: String): Outcome<String, String> {
        val model = native ?: return Outcome.Failure("the punctuation model was removed")
        return try {
            Outcome.Success(model.addPunctuation(text))
        } catch (e: IllegalStateException) {
            Outcome.Failure("the punctuation model failed: ${e.javaClass.simpleName}")
        } catch (e: IllegalArgumentException) {
            Outcome.Failure("the punctuation model failed: ${e.javaClass.simpleName}")
        }
    }

    @Synchronized
    fun release() {
        native?.release()
        native = null
    }

    companion object {
        private const val TAG = "Speech.PunctModel"

        /** Loads the model from [dir]; null (logged) when sherpa-onnx refuses it, e.g. a damaged file. */
        fun load(dir: File, model: PunctuationModel, numThreads: Int): SherpaPunctuator? = try {
            val config = OnlinePunctuationModelConfig(
                cnnBilstm = File(dir, model.modelFile).path,
                bpeVocab = File(dir, model.vocabFile).path,
                numThreads = numThreads,
            )
            SherpaPunctuator(OnlinePunctuation(config = OnlinePunctuationConfig(config))).also {
                AudoraLog.i(TAG, "Loaded punctuation model ${model.id}")
            }
        } catch (e: IllegalArgumentException) {
            AudoraLog.e(TAG, "Loading punctuation model ${model.id} failed", e)
            null
        }
    }
}
