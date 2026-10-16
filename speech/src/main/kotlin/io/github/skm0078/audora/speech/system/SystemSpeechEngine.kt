// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/system/SystemSpeechEngine.kt
// Purpose: SpeechEngine over the phone's TTS: offline voices only, text chunked to the engine's limit, typed errors.

package io.github.skm0078.audora.speech.system

import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.flatMap
import io.github.skm0078.audora.speech.RenderResult
import io.github.skm0078.audora.speech.SpeechEngine
import io.github.skm0078.audora.speech.SpeechError
import io.github.skm0078.audora.speech.SpeechError.VoiceUnavailable.Reason
import io.github.skm0078.audora.speech.VoiceInfo
import io.github.skm0078.audora.speech.audio.PcmAudio
import io.github.skm0078.audora.speech.text.TextChunker
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Renders with the phone's own TTS engine (Build Spec §3 fallback; the likely Hindi/Bengali default).
 *
 * Voices that need the network or whose data is not downloaded are never used: Audora reads offline, and a voice
 * pack that is missing is reported as [Reason.NOT_INSTALLED] or [Reason.NETWORK_ONLY] so the UI can send the user
 * to Android's TTS settings instead of failing silently.
 */
class SystemSpeechEngine(private val client: SystemTtsClient) : SpeechEngine {
    override val id: EngineId = EngineId.SYSTEM

    private val mutex = Mutex()
    private val released = AtomicBoolean(false)

    /** Android's engines cover every language Audora reads; whether a voice is installed is a separate question. */
    override fun supports(lang: Lang): Boolean = true

    override suspend fun voices(): Outcome<List<VoiceInfo>, SpeechError> = mutex.withLock {
        connected { _ ->
            Outcome.Success(
                client.voices().filter { usable(it) }.mapNotNull { voice ->
                    langOf(voice)?.let { VoiceInfo(VoiceId(id, voice.name), it, "Phone voice (${voice.name})", null) }
                },
            )
        }
    }

    /** The best offline voice for [lang], or why there is none. What the voice settings (P3-01) offer by default. */
    suspend fun bestVoice(lang: Lang): Outcome<VoiceId, SpeechError> = mutex.withLock {
        connected { _ ->
            val candidates = client.voices().filter { it.language == lang.bcp47Tag }
            val best = candidates.filter { usable(it) }.maxWithOrNull(compareBy({ it.quality }, { -it.latency }))
            when {
                best != null -> Outcome.Success(VoiceId(id, best.name))
                candidates.any { it.requiresNetwork && it.installed } -> unavailable(Reason.NETWORK_ONLY, lang = lang)
                else -> unavailable(Reason.NOT_INSTALLED, lang = lang)
            }
        }
    }

    override suspend fun render(text: String, voice: VoiceId, rate: Float): RenderResult = mutex.withLock {
        if (voice.engine != id) return@withLock unavailable(Reason.UNKNOWN_VOICE, voice)
        connected { engine -> renderConnected(engine, text, voice, rate) }
    }

    override fun release() {
        if (!released.compareAndSet(false, true)) return
        client.shutdown()
        AudoraLog.i(TAG, "Released")
    }

    private suspend fun renderConnected(engine: String, text: String, voice: VoiceId, rate: Float): RenderResult =
        checkVoice(engine, voice).flatMap { synthesizeChunks(engine, text, voice, rate) }

    private fun checkVoice(engine: String, voice: VoiceId): Outcome<Unit, SpeechError> {
        val match = client.voices().firstOrNull { it.name == voice.name }
        return when {
            match == null -> unavailable(Reason.UNKNOWN_VOICE, voice, detail = "not offered by $engine")
            !match.installed -> unavailable(Reason.NOT_INSTALLED, voice, langOf(match), engine)
            match.requiresNetwork -> unavailable(Reason.NETWORK_ONLY, voice, langOf(match), engine)
            else -> Outcome.Success(Unit)
        }
    }

    private suspend fun synthesizeChunks(engine: String, text: String, voice: VoiceId, rate: Float): RenderResult {
        val chunks = TextChunker.chunk(text, client.maxInputChars)
        if (chunks.isEmpty()) return Outcome.Success(PcmAudio.silence(SILENCE_SAMPLE_RATE))
        val parts = ArrayList<PcmAudio>(chunks.size)
        var failure: String? = null
        for ((index, chunk) in chunks.withIndex()) {
            when (val result = client.synthesize(chunk, voice.name, rate)) {
                is Outcome.Success -> parts += result.value

                is Outcome.Failure -> {
                    failure = "chunk ${index + 1}/${chunks.size}: ${result.error}"
                    break
                }
            }
        }
        val rates = parts.map { it.sampleRate }.distinct()
        val problem = failure ?: "chunks came back at different sample rates $rates".takeIf { rates.size > 1 }
        return if (problem != null) {
            AudoraLog.e(TAG, "Render failed for voice=$voice engine=$engine: $problem")
            Outcome.Failure(SpeechError.SynthesisFailed(voice, problem))
        } else {
            val audio = PcmAudio.concat(parts)
            AudoraLog.d(
                TAG,
                "Rendered voice=$voice chars=${text.length} chunks=${chunks.size} audioMs=${audio.durationMs}",
            )
            Outcome.Success(audio)
        }
    }

    /** Runs [block] once the engine is connected; turns a release or a failed connection into a typed failure. */
    private suspend fun <T> connected(
        block: suspend (engine: String) -> Outcome<T, SpeechError>,
    ): Outcome<T, SpeechError> {
        if (released.get()) return Outcome.Failure(SpeechError.EngineUnavailable(id, "engine was released"))
        return when (val connection = client.connect()) {
            is Outcome.Success -> block(client.engineName)

            is Outcome.Failure -> {
                AudoraLog.e(TAG, "System TTS unavailable: ${connection.error.message}")
                connection
            }
        }
    }

    private fun <T> unavailable(
        reason: Reason,
        voice: VoiceId? = null,
        lang: Lang? = null,
        engine: String = "",
        detail: String = if (engine.isEmpty()) "" else "engine $engine",
    ): Outcome<T, SpeechError> {
        val error = SpeechError.VoiceUnavailable(id, reason, voice, lang, detail)
        AudoraLog.w(TAG, error.message)
        return Outcome.Failure(error)
    }

    private companion object {
        const val TAG = "Speech.System"

        /** Rate for the silence a blank text renders to; the player resamples whatever it gets. */
        const val SILENCE_SAMPLE_RATE = 24_000
    }
}

private fun usable(voice: SystemVoice): Boolean = voice.installed && !voice.requiresNetwork

private fun langOf(voice: SystemVoice): Lang? = Lang.entries.firstOrNull { it.bcp47Tag == voice.language }
