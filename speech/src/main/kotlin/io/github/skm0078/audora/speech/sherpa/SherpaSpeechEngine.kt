// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/sherpa/SherpaSpeechEngine.kt
// Purpose: SpeechEngine over sherpa-onnx voices: keeps a few models loaded, serializes renders, honours cancellation.

package io.github.skm0078.audora.speech.sherpa

import io.github.skm0078.audora.core.config.SpeechConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Feeling
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
 * At most [maxLoadedVoices] models are kept loaded (a Piper voice is ~60 MB of weights plus ONNX Runtime arenas, a
 * premium one ~150 MB), so a bilingual book does not reload a model at every language switch. Past the limit, the
 * least recently used model is released before the next one loads. [trimMemory] drops all but the most recently
 * used one. Loading happens on the first render of a voice, not at construction.
 *
 * The voice list comes from [source] on every call, so voices downloaded or deleted while the engine lives (P4-01)
 * are seen at once. A voice whose files changed (an update: new `contentVersion`) is reloaded on its next render.
 *
 * Cancellation: sherpa-onnx can only stop between its internal sentences, so a cancelled render returns as soon as
 * the sentence being computed finishes (a fraction of a second for Piper on the Moto) and its audio is discarded.
 *
 * @param dispatcher where model loads and renders run. One thread: sherpa-onnx uses its own worker threads inside a
 *   call, and two renders at once would only compete for the same cores.
 * @param maxLoadedVoices models kept loaded at once ([SpeechConfig.maxLoadedVoices]).
 */
@Suppress("TooManyFunctions") // SpeechEngine's members (two render overloads) and the memory hooks.
class SherpaSpeechEngine(
    internal val source: SherpaVoiceSource,
    private val factory: SherpaSynthesizerFactory,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
    private val maxLoadedVoices: Int = SpeechConfig().maxLoadedVoices,
) : SpeechEngine {
    /** A fixed set of voices (tests, and the bundled voice alone). */
    constructor(
        voices: List<SherpaVoiceSpec>,
        factory: SherpaSynthesizerFactory,
        dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
        maxLoadedVoices: Int = SpeechConfig().maxLoadedVoices,
    ) : this(fixedVoices(voices), factory, dispatcher, maxLoadedVoices)

    init {
        require(maxLoadedVoices >= 1) { "maxLoadedVoices must be >= 1, was $maxLoadedVoices" }
    }

    override val id: EngineId = EngineId.SHERPA

    private val mutex = Mutex()
    private val released = AtomicBoolean(false)
    private val trimRequested = AtomicBoolean(false)

    /** Guarded by [mutex]. */
    private val loaded = LoadedVoices(maxLoadedVoices)

    override fun supports(lang: Lang): Boolean = source.voices().any { it.lang == lang }

    override suspend fun voices(): Outcome<List<VoiceInfo>, SpeechError> =
        Outcome.Success(source.byId().values.map { it.toVoiceInfo() })

    override suspend fun render(text: String, voice: VoiceId, rate: Float): RenderResult =
        render(text, voice, rate, Feeling.NEUTRAL)

    /**
     * [voice] may name another speaker of an installed model ([CastSpeakers.variant]): same model, its speaker. A
     * model with styles reads [feeling] in its style ([SherpaVoiceSpec.styleFor]), every other model ignores it.
     */
    override suspend fun render(text: String, voice: VoiceId, rate: Float, feeling: Feeling): RenderResult {
        val (base, requested) = CastSpeakers.parse(voice)
        val spec = source.byId()[base]
            ?: return Outcome.Failure(
                SpeechError.VoiceUnavailable(id, SpeechError.VoiceUnavailable.Reason.UNKNOWN_VOICE, voice),
            )
        return try {
            val take = Take(text, rate, requested, spec.styleFor(feeling))
            mutex.withLock { withContext(dispatcher) { renderLocked(spec, take) } }
        } finally {
            // release() or trimMemory() may have found the lock taken by this render and left their work to whoever
            // holds it next.
            settle()
        }
    }

    override fun actsFeelings(voice: VoiceId): Boolean =
        source.byId()[CastSpeakers.parse(voice).first]?.styles?.isNotEmpty() == true

    override fun release() {
        if (!released.compareAndSet(false, true)) return
        AudoraLog.i(TAG, "Released")
        settle()
    }

    /**
     * Releases every loaded model except the most recently used one, to give memory back when Android asks for it.
     * Any thread. Never frees a model mid-render: if a render holds the engine, its exit does the trimming.
     */
    fun trimMemory() {
        trimRequested.set(true)
        settle()
    }

    /**
     * Does what [release] (free every model) or [trimMemory] asked for, once nothing is rendering. They and every
     * render's exit call it after setting their flag, so whichever of them gets the lock last does the work and a
     * model is never freed mid-render. Looks again after a trim, so one asked for meanwhile is not lost.
     */
    private fun settle() {
        while ((released.get() || trimRequested.get()) && mutex.tryLock()) {
            try {
                if (released.get()) {
                    loaded.releaseAll("engine released")
                    return
                }
                if (trimRequested.getAndSet(false)) loaded.trim()
            } finally {
                mutex.unlock()
            }
        }
    }

    /**
     * One render's asks: [requested], another speaker of the model, checked against the model once loaded; [style],
     * the model's style for the sentence's feeling, null for a model without styles.
     */
    private class Take(val text: String, val rate: Float, val requested: Int?, val style: Int?)

    private suspend fun renderLocked(spec: SherpaVoiceSpec, take: Take): RenderResult {
        if (released.get()) return Outcome.Failure(SpeechError.EngineUnavailable(id, "engine was released"))
        return synthesizerFor(spec).flatMap { synthesizer ->
            val speaker = CastSpeakers.speakerFor(spec, take.requested, synthesizer.speakerCount)
            if (take.text.isBlank()) {
                Outcome.Success(
                    PcmAudio.silence(synthesizer.sampleRate),
                )
            } else {
                generate(spec, synthesizer, take, speaker)
            }
        }
    }

    private suspend fun generate(
        spec: SherpaVoiceSpec,
        synthesizer: SherpaSynthesizer,
        take: Take,
        speaker: Int,
    ): RenderResult {
        val job = coroutineContext[Job]
        val started = System.nanoTime()
        val text = when {
            take.style != null -> StyledText.ending(take.text, spec.lang)
            else -> ClosingMarks.inside(take.text)
        }
        val rate = take.rate
        val samples: Outcome<FloatArray, SpeechError> = try {
            Outcome.Success(synthesizer.generate(text, speaker, rate, take.style) { job?.isActive != false })
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
                    "Rendered voice=${spec.voiceId} speaker=$speaker style=${take.style} rate=$rate " +
                        "chars=${text.length} audioMs=${audio.durationMs} renderMs=$renderMs",
                )
            }
            audio
        }
    }

    private fun synthesizerFor(spec: SherpaVoiceSpec): Outcome<SherpaSynthesizer, SpeechError> {
        loaded.take(spec)?.let { return Outcome.Success(it) }
        // Free a slot before loading, so the models in memory never exceed the limit, even for a moment.
        loaded.makeRoom()
        val started = System.nanoTime()
        return when (val result = factory.load(spec)) {
            is Outcome.Success -> {
                loaded.add(spec, result.value)
                val loadMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
                AudoraLog.i(
                    TAG,
                    "Loaded voice=${spec.voiceId} sampleRate=${result.value.sampleRate} loadMs=$loadMs " +
                        "loaded=${loaded.size}",
                )
                if (!CastSpeakers.tableFits(spec, result.value.speakerCount)) {
                    AudoraLog.w(
                        TAG,
                        "voice=${spec.voiceId} holds ${result.value.speakerCount} speakers, not the ones its " +
                            "character table lists; characters read with the voice's own speaker",
                    )
                }
                result
            }

            is Outcome.Failure -> {
                AudoraLog.e(TAG, "Could not load voice=${spec.voiceId}: ${result.error.message}")
                result
            }
        }
    }

    private fun failed(spec: SherpaVoiceSpec, e: RuntimeException): Outcome.Failure<SpeechError> {
        AudoraLog.e(TAG, "Render failed for voice=${spec.voiceId}", e)
        return Outcome.Failure(SpeechError.SynthesisFailed(spec.voiceId, e.message ?: e.javaClass.simpleName, e))
    }

    /** The loaded models, least recently used first, at most [limit]. Not thread-safe: the engine's mutex guards it. */
    private class LoadedVoices(private val limit: Int) {
        private val voices = LinkedHashMap<VoiceId, LoadedVoice>()

        val size: Int get() = voices.size

        /**
         * The model loaded for exactly [spec], now the most recently used; null if there is none. A model of the
         * same voice with other files (an update) or other noise values (expressiveness) is released, not kept
         * beside the new one.
         */
        fun take(spec: SherpaVoiceSpec): SherpaSynthesizer? {
            val cached = voices.remove(spec.voiceId)
            return when {
                cached == null -> null

                cached.spec != spec -> {
                    release(cached, "voice files or expressiveness changed")
                    null
                }

                else -> {
                    voices[spec.voiceId] = cached // re-inserted at the end: most recently used
                    cached.synthesizer
                }
            }
        }

        /** Releases the least recently used models until one more fits. */
        fun makeRoom() {
            while (voices.size >= limit) releaseEldest("least recently used")
        }

        fun add(spec: SherpaVoiceSpec, synthesizer: SherpaSynthesizer) {
            voices[spec.voiceId] = LoadedVoice(spec, synthesizer)
        }

        /** Keeps only the most recently used model. */
        fun trim() {
            while (voices.size > 1) releaseEldest("memory trim")
        }

        fun releaseAll(reason: String) {
            while (voices.isNotEmpty()) releaseEldest(reason)
        }

        private fun releaseEldest(reason: String) {
            val eldest = voices.entries.first()
            voices.remove(eldest.key)
            release(eldest.value, reason)
        }

        private fun release(voice: LoadedVoice, reason: String) {
            voice.synthesizer.release()
            AudoraLog.i(TAG, "Unloaded voice=${voice.spec.voiceId} ($reason)")
        }
    }

    private class LoadedVoice(val spec: SherpaVoiceSpec, val synthesizer: SherpaSynthesizer)

    private companion object {
        const val TAG = "Speech.Sherpa"

        fun fixedVoices(voices: List<SherpaVoiceSpec>): SherpaVoiceSource {
            val ids = voices.map { it.id }
            require(ids.toSet().size == ids.size) { "Duplicate voice ids in $ids" }
            return SherpaVoiceSource { voices }
        }
    }
}

/** The voices an engine can render right now; called on every lookup, so it must be cheap (no disk I/O). */
fun interface SherpaVoiceSource {
    fun voices(): List<SherpaVoiceSpec>
}

/** The first voice with an id wins, so the bundled voice shadows a download that later shares its id. */
private fun SherpaVoiceSource.byId(): Map<VoiceId, SherpaVoiceSpec> = buildMap {
    voices().forEach { if (it.voiceId !in this) put(it.voiceId, it) }
}

/**
 * The other speakers [voice]'s model holds, its own speaker left out, for character voices (plan item 63); empty for
 * a one-speaker model or a voice not installed. Cheap: reads the voice list, never loads a model.
 */
fun SherpaSpeechEngine.castSpeakers(voice: VoiceId): List<CastSpeaker> {
    val spec = source.byId()[CastSpeakers.parse(voice).first] ?: return emptyList()
    return CastSpeakers.of(spec).filter { it.speakerId != spec.speakerId }
}

/**
 * Whether [voice] honours expressiveness (plan item 8): only Piper and MMS (VITS) voices have noise values to change.
 * False for any other voice, or one not installed. Cheap: reads the voice list, never loads a model.
 */
fun SherpaSpeechEngine.isExpressive(voice: VoiceId): Boolean =
    source.byId()[CastSpeakers.parse(voice).first]?.family == SherpaFamily.VITS

/**
 * [voice]'s own speaker when a narrator blend can change it (plan item 75): a Kokoro voice with English speakers to
 * mix in; null for any other voice, or one not installed. Cheap: reads the voice list, never loads a model.
 */
fun SherpaSpeechEngine.blendableSpeaker(voice: VoiceId): Int? = source.byId()[CastSpeakers.parse(voice).first]
    ?.takeIf { it.family == SherpaFamily.KOKORO && CastSpeakers.of(it).isNotEmpty() }
    ?.speakerId
