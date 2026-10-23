// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/acted/ActedSpeechEngine.kt
// Purpose: SpeechEngine over the acted Hindi voice (Chatterbox), for studio mode only (ADR-059).

package io.github.skm0078.audora.speech.acted

import io.github.skm0078.audora.core.config.ActedConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Feeling
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.RenderResult
import io.github.skm0078.audora.speech.SpeechEngine
import io.github.skm0078.audora.speech.SpeechError
import io.github.skm0078.audora.speech.VoiceInfo
import io.github.skm0078.audora.speech.audio.PcmAudio
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What the engine needs from the installed voice pack: the model and its tokenizer, loaded on first render. */
fun interface ActedModelLoader {
    /** Null with nothing installed; a failure's text is for the log. */
    fun load(): Outcome<LoadedActed, String>?
}

class LoadedActed(val model: ActedModel, val tokenizer: ChatterboxTokenizer)

/**
 * Reads Hindi as an actor does, from the text itself: Chatterbox hears a question, a plea or a shout in the words and
 * punctuation, and a sentence that asks for a feeling (ADR-056) is acted more strongly. It takes the whole sentence,
 * quotes and all ([keepsSentencesWhole]): split into narration and quotes, each piece would lose the other's context
 * and cost another pass of the decoder.
 *
 * Far slower than real time on a phone (about four to six times, measured on an ARM CI runner and scaled), so only
 * studio mode renders with it, overnight on the charger; playback plays what it rendered and reads the rest live.
 * The rate is the voice's own: Chatterbox has no speed control, and stretching audio smears words (ADR-033).
 *
 * Every take is checked ([ActedTakes]): one that runs on or stops short is tried once more, plainer, and then
 * left to the live voice ([SpeechError.SynthesisFailed]), which studio mode does for any sentence that fails.
 *
 * Cancellation stops the native token loop within one step, or ONNX Runtime's decoder at its next operator.
 */
@Suppress("TooManyFunctions") // SpeechEngine's members, and the render split into its steps.
class ActedSpeechEngine(
    private val loader: ActedModelLoader,
    private val config: ActedConfig = ActedConfig(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
) : SpeechEngine {
    override val id: EngineId = EngineId.ACTED

    private val mutex = Mutex()
    private val released = AtomicBoolean(false)
    private val takes = ActedTakes(config)

    /** Guarded by [mutex]. */
    private var loaded: LoadedActed? = null

    override fun supports(lang: Lang): Boolean = lang == Lang.HI

    override suspend fun voices(): Outcome<List<VoiceInfo>, SpeechError> =
        Outcome.Success(listOf(VoiceInfo(HINDI, Lang.HI, LABEL, LICENCE)))

    override suspend fun render(text: String, voice: VoiceId, rate: Float): RenderResult =
        render(text, voice, rate, Feeling.NEUTRAL)

    override suspend fun render(text: String, voice: VoiceId, rate: Float, feeling: Feeling): RenderResult {
        if (voice != HINDI) {
            return Outcome.Failure(
                SpeechError.VoiceUnavailable(id, SpeechError.VoiceUnavailable.Reason.UNKNOWN_VOICE, voice),
            )
        }
        return mutex.withLock { withContext(dispatcher) { renderLocked(text, feeling) } }
    }

    override fun keepsSentencesWhole(voice: VoiceId): Boolean = voice == HINDI

    override fun release() {
        if (!released.compareAndSet(false, true)) return
        // A render in flight holds the lock; cancelling it lets the lock come free soon.
        loaded?.model?.cancel()
        if (mutex.tryLock()) {
            try {
                free()
            } finally {
                mutex.unlock()
            }
        }
        AudoraLog.i(TAG, "Released")
    }

    private suspend fun renderLocked(text: String, feeling: Feeling): RenderResult = if (released.get()) {
        free()
        Outcome.Failure(SpeechError.EngineUnavailable(id, "engine was released"))
    } else {
        when (val load = modelLocked()) {
            is Outcome.Failure -> Outcome.Failure(SpeechError.EngineUnavailable(id, load.error))

            is Outcome.Success -> if (text.isBlank()) {
                Outcome.Success(PcmAudio.silence(ActedModel.SAMPLE_RATE))
            } else {
                renderText(load.value, text, feeling)
            }
        }
    }

    private suspend fun renderText(acted: LoadedActed, text: String, feeling: Feeling): RenderResult {
        val started = System.nanoTime()
        val pieces = ActedPieces.of(text, config.maxPieceChars)
        val audio = ArrayList<FloatArray>(pieces.size)
        for (piece in pieces) {
            val spoken = speak(acted, piece, feeling)
            if (spoken is Outcome.Failure) return spoken
            audio.add((spoken as Outcome.Success).value)
        }
        val pcm = PcmAudio.fromFloats(join(audio), ActedModel.SAMPLE_RATE)
        AudoraLog.d(
            TAG,
            "Rendered chars=${text.length} pieces=${pieces.size} feeling=$feeling audioMs=${pcm.durationMs} " +
                "renderMs=${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)}",
        )
        return Outcome.Success(pcm)
    }

    /** One piece: each take in turn until one passes the checks, then its audio. */
    private suspend fun speak(acted: LoadedActed, piece: String, feeling: Feeling): Outcome<FloatArray, SpeechError> {
        val tokens = acted.tokenizer.encode(piece)
        val maxTokens = takes.maxTokens(piece.length)
        var last: TakeResult = TakeResult.Failed(SpeechError.SynthesisFailed(HINDI, "no take tried"))
        for (take in takes.takesFor(feeling)) {
            last = tryTake(acted.model, tokens, take, piece.length, maxTokens)
            if (last !is TakeResult.Retry) break
        }
        return when (last) {
            is TakeResult.Good -> native(acted.model) { acted.model.decode(last.speech.tokens) }
                .also { coroutineContext.ensureActive() }

            is TakeResult.Failed -> Outcome.Failure(last.error)

            is TakeResult.Retry -> Outcome.Failure(
                SpeechError.SynthesisFailed(HINDI, "every take ${last.verdict.name.lowercase()}"),
            )
        }
    }

    private suspend fun tryTake(
        model: ActedModel,
        tokens: LongArray,
        take: ActedTake,
        chars: Int,
        maxTokens: Int,
    ): TakeResult {
        val speech = native(model) { model.speak(tokens, take.strength, maxTokens, take.repetitionPenalty) }
        coroutineContext.ensureActive()
        val spoken = (speech as? Outcome.Success)?.value
        val verdict = spoken?.let { takes.verdict(chars, it) }
        if (spoken != null && verdict != ActedTakes.Verdict.GOOD) {
            AudoraLog.w(TAG, "Take $take for $chars chars: $verdict (${spoken.tokens.size} of $maxTokens tokens)")
        }
        return when {
            speech is Outcome.Failure -> TakeResult.Failed(speech.error)
            spoken == null -> TakeResult.Failed(cancelled())
            verdict == ActedTakes.Verdict.GOOD -> TakeResult.Good(spoken)
            else -> TakeResult.Retry(requireNotNull(verdict))
        }
    }

    private sealed interface TakeResult {
        class Good(val speech: ActedSpeech) : TakeResult

        class Retry(val verdict: ActedTakes.Verdict) : TakeResult

        class Failed(val error: SpeechError) : TakeResult
    }

    /**
     * Runs [block] (blocking, on this engine's thread) with this coroutine's cancellation wired to the model's: a
     * watcher on another dispatcher, since this one is busy in [block], cancels the model when the render is
     * cancelled. ONNX Runtime errors become failures.
     */
    private suspend fun <T> native(model: ActedModel, block: () -> T): Outcome<T, SpeechError> = coroutineScope {
        val finished = AtomicBoolean(false)
        val watcher = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                if (!finished.get()) model.cancel()
            }
        }
        try {
            Outcome.Success(block())
        } catch (e: IllegalStateException) {
            AudoraLog.e(TAG, "Render failed", e)
            Outcome.Failure(SpeechError.SynthesisFailed(HINDI, e.message ?: e.javaClass.simpleName, e))
        } finally {
            finished.set(true)
            watcher.cancel()
        }
    }

    private fun modelLocked(): Outcome<LoadedActed, String> = loaded?.let { Outcome.Success(it) } ?: loadModel()

    private fun loadModel(): Outcome<LoadedActed, String> {
        val started = System.nanoTime()
        val result = loader.load() ?: Outcome.Failure("the acted Hindi voice is not installed")
        when (result) {
            is Outcome.Success -> {
                loaded = result.value
                AudoraLog.i(TAG, "Loaded loadMs=${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)}")
            }

            is Outcome.Failure -> AudoraLog.e(TAG, "Could not load the acted voice: ${result.error}")
        }
        return result
    }

    private fun free() {
        loaded?.model?.close()
        loaded = null
    }

    private fun join(pieces: List<FloatArray>): FloatArray {
        if (pieces.size == 1) return pieces[0]
        val gap = ActedModel.SAMPLE_RATE * config.pieceGapMs / MS_PER_SECOND
        val out = FloatArray(pieces.sumOf { it.size } + gap * (pieces.size - 1))
        var at = 0
        pieces.forEachIndexed { index, piece ->
            if (index > 0) at += gap
            piece.copyInto(out, at)
            at += piece.size
        }
        return out
    }

    private fun cancelled() = SpeechError.EngineUnavailable(id, "render cancelled")

    companion object {
        /** The one acted voice: Hindi, Chatterbox's built-in reference speaker. */
        val HINDI = VoiceId(EngineId.ACTED, "chatterbox-hi")
        const val LABEL = "Hindi, acted (Chatterbox, studio mode)"
        const val LICENCE = "MIT: Resemble AI Chatterbox"
        private const val TAG = "Speech.Acted"
        private const val MS_PER_SECOND = 1_000
    }
}
