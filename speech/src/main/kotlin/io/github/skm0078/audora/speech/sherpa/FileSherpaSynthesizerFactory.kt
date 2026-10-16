// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/sherpa/FileSherpaSynthesizerFactory.kt
// Purpose: Thin adapter that loads a downloaded or imported voice from its folder on disk into sherpa-onnx.

package io.github.skm0078.audora.speech.sherpa

import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.SpeechError
import io.github.skm0078.audora.speech.SpeechError.VoiceUnavailable.Reason
import java.io.File

/**
 * Loads voices installed by the voice manager (P4-01): every file is a plain file in [voiceDir]`(id)`, so sherpa-onnx
 * reads them directly and espeak-ng-data needs no copy. Files are checked first because sherpa-onnx aborts the whole
 * process on some missing ones; a deleted voice fails here as MODEL_FILES_MISSING.
 */
class FileSherpaSynthesizerFactory(private val voiceDir: (String) -> File, private val threads: Int) :
    SherpaSynthesizerFactory {
    override fun load(spec: SherpaVoiceSpec): Outcome<SherpaSynthesizer, SpeechError> {
        val dir = voiceDir(spec.id)
        val absent = spec.requiredFiles.filterNot { File(dir, it).isFile } +
            listOfNotNull(spec.dataDir).filterNot { File(dir, it).isDirectory }
        if (absent.isNotEmpty()) {
            return Outcome.Failure(
                SpeechError.VoiceUnavailable(
                    EngineId.SHERPA,
                    Reason.MODEL_FILES_MISSING,
                    spec.voiceId,
                    spec.lang,
                    "${dir.name} lacks $absent",
                ),
            )
        }
        val dataDir = spec.dataDir?.let { File(dir, it).path }.orEmpty()
        val model = sherpaModelConfig(spec, threads, dataDir) { File(dir, it).path }
        return try {
            val config = OfflineTtsConfig(model = model, maxNumSentences = 1)
            Outcome.Success(OfflineTtsSynthesizer(OfflineTts(config = config)))
        } catch (e: IllegalArgumentException) {
            Outcome.Failure(SpeechError.SynthesisFailed(spec.voiceId, "sherpa-onnx rejected the model config", e))
        } catch (e: UnsatisfiedLinkError) {
            Outcome.Failure(SpeechError.EngineUnavailable(EngineId.SHERPA, "native library not loaded: ${e.message}"))
        }
    }
}
