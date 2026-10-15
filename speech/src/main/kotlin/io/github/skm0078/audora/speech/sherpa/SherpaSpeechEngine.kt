// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/sherpa/SherpaSpeechEngine.kt
// Purpose: SpeechEngine over sherpa-onnx voices: loads one model at a time, serializes renders, honours cancellation.

package io.github.skm0078.audora.speech.sherpa

import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.flatMap
import io.github.skm0078.audora.core.result.map
import io.github.skm0078.audora.speech.RenderResult
import io.github.skm0078.audora.speech.SpeechEngine
import io.github.skm0078.audora.speech.SpeechError
import io.github.skm0078.audora.speech.VoiceInfo
import io.github.skm0078.audora.speech.audio.PcmAudio
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Renders with sherpa-onnx voices.
 *
 * Only one model is kept loaded (a Piper voice is ~60 MB of weights plus ONNX Runtime arenas); asking for another
 * voice releases the current one first. Loading happens on the first render of a voice, not at construction.
 *
 * Cancellation: sherpa-onnx can only stop between its internal sentences, so a cancelled render returns as soon as
 * the sentence being computed finishes (a fraction of a second for Piper on the Moto) and its audio is discarded.
 *
 * @param dispatcher where model loads and renders run. One thread: sherpa-onnx uses its own worker threads inside a
 *   call, and two renders at once would only compete for the same cores.
 */
class SherpaSpeechEngine(
    voices: List<SherpaVoiceSpec>,
    private val factory: SherpaSynthesizerFactory,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
) : SpeechEngine {
    override val id: EngineId = EngineId.SHERPA

    private val voicesById: Map<VoiceId, SherpaVoiceSpec> = voices.associateBy { it.voiceId }
    private val mutex = Mutex()
    private val released = AtomicBoolean(false)

    /** Guarded by [mutex]. */
    private var loaded: LoadedVoice? = null

    init {
        require(voicesById.size == voices.size) { "Duplicate voice ids in ${voices.map { it.id }}" }
    }

    override fun supports(lang: Lang): Boolean = voicesById.values.any { it.lang == lang }

    override suspend fun voices(): Outcome<List<VoiceInfo>, SpeechError> =
        Outcome.Success(voicesById.values.map { it.toVoiceInfo() })

    override suspend fun render(text: String, voice: VoiceId): RenderResult {
        val spec = voicesById[voice]
            ?: return Outcome.Failure(
                SpeechError.VoiceUnavailable(id, SpeechError.VoiceUnavailable.Reason.UNKNOWN_VOICE, voice),
            )
        return try {
            mutex.withLock { withContext(dispatcher) { renderLocked(spec, text) } }
        } finally {
            // release() may have found the lock taken by this render and left the unloading to whoever holds it next.
            unloadIfReleased()
        }
    }

    override fun release() {
        if (!released.compareAndSet(false, true)) return
        AudoraLog.i(TAG, "Released")
        unloadIfReleased()
    }

    /**
     * Frees the model once nothing is rendering. Both [release] and every render's exit call it after the flag is
     * set, so whichever of them gets the lock last does the unloading and a model is never freed mid-render.
     */
    private fun unloadIfReleased() {
        if (released.get() && mutex.tryLock()) {
            try {
                unloadLocked()
            } finally {
                mutex.unlock()
            }
        }
    }

    private suspend fun renderLocked(spec: SherpaVoiceSpec, text: String): RenderResult {
        if (released.get()) return Outcome.Failure(SpeechError.EngineUnavailable(id, "engine was released"))
        return synthesizerFor(spec).flatMap { synthesizer ->
            if (text.isBlank()) {
                Outcome.Success(
                    PcmAudio.silence(synthesizer.sampleRate),
                )
            } else {
                generate(spec, synthesizer, text)
            }
        }
    }

    private suspend fun generate(spec: SherpaVoiceSpec, synthesizer: SherpaSynthesizer, text: String): RenderResult {
        val job = coroutineContext[Job]
        val started = System.nanoTime()
        val samples: Outcome<FloatArray, SpeechError> = try {
            Outcome.Success(synthesizer.generate(text, spec.speakerId) { job?.isActive != false })
        } catch (e: IllegalStateException) {
            failed(spec, e)
        } catch (e: IllegalArgumentException) {
            failed(spec, e)
        }
        coroutineContext.ensureActive() // a cancelled render's partial audio is never returned

        return samples.map { floats ->
            val audio = PcmAudio.fromFloats(floats, synthesizer.sampleRate)
            val renderMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            if (floats.isEmpty()) {
                AudoraLog.w(TAG, "No audio from voice=${spec.voiceId} for ${text.length} chars; treated as silence")
            } else {
                AudoraLog.d(
                    TAG,
                    "Rendered voice=${spec.voiceId} chars=${text.length} audioMs=${audio.durationMs} " +
                        "renderMs=$renderMs",
                )
            }
            audio
        }
    }

    private fun synthesizerFor(spec: SherpaVoiceSpec): Outcome<SherpaSynthesizer, SpeechError> {
        loaded?.takeIf { it.spec.id == spec.id }?.let { return Outcome.Success(it.synthesizer) }
        unloadLocked()
        val started = System.nanoTime()
        return when (val result = factory.load(spec)) {
            is Outcome.Success -> {
                loaded = LoadedVoice(spec, result.value)
                val loadMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
                AudoraLog.i(TAG, "Loaded voice=${spec.voiceId} sampleRate=${result.value.sampleRate} loadMs=$loadMs")
                result
            }

            is Outcome.Failure -> {
                AudoraLog.e(TAG, "Could not load voice=${spec.voiceId}: ${result.error.message}")
                result
            }
        }
    }

    private fun unloadLocked() {
        val current = loaded ?: return
        loaded = null
        current.synthesizer.release()
        AudoraLog.i(TAG, "Unloaded voice=${current.spec.voiceId}")
    }

    private fun failed(spec: SherpaVoiceSpec, e: RuntimeException): Outcome.Failure<SpeechError> {
        AudoraLog.e(TAG, "Render failed for voice=${spec.voiceId}", e)
        return Outcome.Failure(SpeechError.SynthesisFailed(spec.voiceId, e.message ?: e.javaClass.simpleName, e))
    }

    private class LoadedVoice(val spec: SherpaVoiceSpec, val synthesizer: SherpaSynthesizer)

    private companion object {
        const val TAG = "Speech.Sherpa"
    }
}
