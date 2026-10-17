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
import kotlin.math.roundToInt

/**
 * Loads voices installed by the voice manager (P4-01): every file is a plain file in [voiceDir]`(id)`, so sherpa-onnx
 * reads them directly and espeak-ng-data needs no copy. Files are checked first because sherpa-onnx aborts the whole
 * process on some missing ones; a deleted voice fails here as MODEL_FILES_MISSING.
 *
 * A Kokoro voice with a narrator blend ([SherpaVoiceSpec.blend], plan item 75) reads a blended copy of its voices
 * file from [blendDir] ([KokoroBlends]); if the copy can't be made, the voice loads as installed and the reason is
 * logged, so a full disk costs the blend, never the reading.
 */
class FileSherpaSynthesizerFactory(
    private val voiceDir: (String) -> File,
    private val threads: Int,
    private val blendDir: File? = null,
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
        val dataDir = spec.dataDir?.let { File(dir, it).path }.orEmpty()
        val blended = blendedVoices(spec, dir)
        val model = sherpaModelConfig(spec, threads, dataDir) { file ->
            if (blended != null && file == spec.voices) blended.path else File(dir, file).path
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

    /** The blended voices file for [spec], made now if missing; null without a blend or when it can't be made. */
    private fun blendedVoices(spec: SherpaVoiceSpec, dir: File): File? {
        val blend = spec.blend?.takeIf { spec.family == SherpaFamily.KOKORO }
        val voices = spec.voices
        val root = blendDir
        if (blend == null || voices == null || root == null) return null
        val percent = (blend.amount * PERCENT).roundToInt()
        val target = File(root, "${spec.id}$PART${spec.speakerId}$PART${blend.withSpeaker}$PART$percent$SUFFIX")
        return when (val made = KokoroBlends.write(File(dir, voices), target, spec.speakerId, blend)) {
            is Outcome.Success -> made.value.also { kept ->
                // One blend per voice: the one before it is 28 MB of cache nobody reads again.
                root.listFiles { file ->
                    file.name.startsWith("${spec.id}$PART") && file != kept
                }?.forEach(File::delete)
            }

            is Outcome.Failure -> {
                AudoraLog.w(TAG, "Blend for ${spec.voiceId} not applied, reading the voice as installed: ${made.error}")
                null
            }
        }
    }

    private companion object {
        const val TAG = "Speech.Blend"
        const val PERCENT = 100
        const val PART = "-"
        const val SUFFIX = ".bin"
    }
}
