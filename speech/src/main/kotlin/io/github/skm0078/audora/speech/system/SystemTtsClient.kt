// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/system/SystemTtsClient.kt
// Purpose: The seam between SystemSpeechEngine's logic (JVM-tested) and Android's TextToSpeech.

package io.github.skm0078.audora.speech.system

import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.SpeechError
import io.github.skm0078.audora.speech.audio.PcmAudio

/**
 * A voice of the phone's TTS engine, as Android reports it.
 *
 * @property language ISO 639 code of the voice's locale ("en", "hi", "bn").
 * @property installed false when the engine lists the voice but its data is not downloaded
 *   (`KEY_FEATURE_NOT_INSTALLED`).
 */
data class SystemVoice(
    val name: String,
    val language: String,
    val requiresNetwork: Boolean,
    val installed: Boolean,
    val quality: Int,
    val latency: Int,
)

interface SystemTtsClient {
    /** Binds to the phone's default TTS engine and waits until it is ready. Idempotent; the first call is slow. */
    suspend fun connect(): Outcome<Unit, SpeechError>

    /** The engine's package, for logs ("com.google.android.tts"). Valid after [connect]. */
    val engineName: String

    /** Longest text one synthesis call accepts (`TextToSpeech.getMaxSpeechInputLength()`). */
    val maxInputChars: Int

    /** Every voice the engine lists, installed or not. Valid after [connect]. */
    fun voices(): List<SystemVoice>

    /**
     * Synthesizes [text] (at most [maxInputChars]) with the voice named [voiceName]. Cancelling the caller stops the
     * engine (`TextToSpeech.stop()`) and throws `CancellationException`.
     */
    suspend fun synthesize(text: String, voiceName: String): Outcome<PcmAudio, String>

    fun shutdown()
}
