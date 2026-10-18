// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/sherpa/FileSherpaSynthesizerFactory.kt
// Purpose: Thin adapter that loads a downloaded or imported voice from its folder on disk into sherpa-onnx.

package io.github.skm0078.audora.speech.sherpa

import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.SpeechError
import io.github.skm0078.audora.speech.SpeechError.VoiceUnavailable.Reason
import java.io.File

/**
 * Loads voices installed by the voice manager (P4-01): every file is a plain file in [voiceDir]`(id)`, so sherpa-onnx
 * reads them directly and espeak-ng-data needs no copy. Files are checked first because sherpa-onnx aborts the whole
 * process on some missing ones; a deleted voice fails here as MODEL_FILES_MISSING.
 *
 * A Kokoro voice with a narrator blend ([SherpaVoiceSpec.blend], plan item 75) reads a blended copy of its voices
 * file from [blends]; if the copy can't be made, or sherpa-onnx refuses it, the voice loads as installed and the
 * reason is logged and reported, so a full disk costs the blend, never the reading. A Kokoro voice loading with no
 * blend drops its old copy. Building and loading a blended voice hold [BlendCache.locked] together.
 */
class FileSherpaSynthesizerFactory(
    private val voiceDir: (String) -> File,
    private val threads: Int,
    private val blends: BlendCache? = null,
) : SherpaSynthesizerFactory {
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
        // Only a blend, or a copy to clear, needs the blend lock; any other voice loads without waiting on a build.
        val cache = blends?.takeIf { spec.family == SherpaFamily.KOKORO && (spec.blend != null || it.holds(spec.id)) }
        return if (cache == null) create(spec, dir, null) else loadKokoro(spec, dir, cache)
    }

    /**
     * Under the blend lock, so no other build or clear of this voice's copy runs between its write and its load: the
     * blend's copy when it has one (an old copy cleared when it has none), else the voice as installed.
     */
    private fun loadKokoro(
        spec: SherpaVoiceSpec,
        dir: File,
        cache: BlendCache,
    ): Outcome<SherpaSynthesizer, SpeechError> = BlendCache.locked {
        if (spec.blend == null) cache.clear(spec.id)
        val blended = spec.blend?.let { cache.voicesFor(spec, dir) }
        loadPreferringBlend(blended, { voices -> create(spec, dir, voices) }) {
            AudoraLog.w(TAG, "sherpa-onnx refused the blend of ${spec.voiceId}, loading it as installed")
            cache.report.neededBytes = 0
        }
    }

    /** [voices]: the blended voices file to read instead of the installed one, if any. */
    private fun create(spec: SherpaVoiceSpec, dir: File, voices: File?): Outcome<SherpaSynthesizer, SpeechError> {
        val dataDir = spec.dataDir?.let { File(dir, it).path }.orEmpty()
        val model = sherpaModelConfig(spec, threads, dataDir) { file ->
            if (voices != null && file == spec.voices) voices.path else File(dir, file).path
        }
        return try {
            val config = OfflineTtsConfig(model = model, maxNumSentences = 1)
            Outcome.Success(OfflineTtsSynthesizer(OfflineTts(config = config), spec))
        } catch (e: IllegalArgumentException) {
            Outcome.Failure(SpeechError.SynthesisFailed(spec.voiceId, "sherpa-onnx rejected the model config", e))
        } catch (e: UnsatisfiedLinkError) {
            Outcome.Failure(SpeechError.EngineUnavailable(EngineId.SHERPA, "native library not loaded: ${e.message}"))
        }
    }

    private companion object {
        const val TAG = "Speech.Blend"
    }
}
