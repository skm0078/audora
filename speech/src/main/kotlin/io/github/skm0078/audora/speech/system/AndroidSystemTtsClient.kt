// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/system/AndroidSystemTtsClient.kt
// Purpose: Thin adapter from SystemTtsClient to Android's TextToSpeech (synthesizeToFile into the cache, read back).

package io.github.skm0078.audora.speech.system

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import io.github.skm0078.audora.core.config.SpeechConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.flatMap
import io.github.skm0078.audora.speech.SpeechError
import io.github.skm0078.audora.speech.audio.PcmAudio
import io.github.skm0078.audora.speech.audio.WavReader
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One `TextToSpeech` connection. Callers serialize (SystemSpeechEngine holds a lock), so at most one utterance is in
 * flight; the continuation map still keys by utterance id so a late callback from a stopped utterance is ignored.
 *
 * @param workDir where `synthesizeToFile` writes; each file is deleted as soon as it is read.
 */
class AndroidSystemTtsClient(
    private val context: Context,
    private val workDir: File,
    private val config: SpeechConfig,
) : SystemTtsClient {
    private var tts: TextToSpeech? = null
    private val pending = ConcurrentHashMap<String, CancellableContinuation<String?>>()

    override var engineName: String = "unknown"
        private set

    override val maxInputChars: Int get() = TextToSpeech.getMaxSpeechInputLength()

    override suspend fun connect(): Outcome<Unit, SpeechError> {
        if (tts != null) return Outcome.Success(Unit)
        var created: TextToSpeech? = null
        val status: Int? = try {
            withTimeoutOrNull(config.systemConnectTimeoutMs) {
                suspendCancellableCoroutine<Int> { continuation ->
                    created = TextToSpeech(context.applicationContext) { code -> continuation.resume(code) }
                }
            }
        } catch (cancelled: CancellationException) {
            // Stopped while binding (Stop during prepare): the half-built engine would stay bound to the TTS service.
            created?.shutdown()
            throw cancelled
        }
        val engine = created
        return if (engine != null && status == TextToSpeech.SUCCESS) {
            engine.setOnUtteranceProgressListener(Listener())
            engineName = engine.defaultEngine ?: "unknown"
            tts = engine
            AudoraLog.i(TAG, "Connected to $engineName (max input $maxInputChars chars)")
            Outcome.Success(Unit)
        } else {
            engine?.shutdown()
            val detail = if (status == null) {
                "the phone's text-to-speech engine did not start within ${config.systemConnectTimeoutMs} ms"
            } else {
                "no text-to-speech engine is installed or it failed to start (status $status)"
            }
            Outcome.Failure(SpeechError.EngineUnavailable(EngineId.SYSTEM, detail))
        }
    }

    override fun voices(): List<SystemVoice> = tts?.voices.orEmpty().map { it.toSystemVoice() }

    override suspend fun synthesize(text: String, voiceName: String, rate: Float): Outcome<PcmAudio, String> {
        val engine = tts ?: return Outcome.Failure("not connected")
        return selectVoice(engine, voiceName).flatMap {
            if (engine.setSpeechRate(rate) == TextToSpeech.SUCCESS) {
                synthesizeToFile(engine, text)
            } else {
                Outcome.Failure("engine refused speech rate $rate")
            }
        }
    }

    private fun selectVoice(engine: TextToSpeech, voiceName: String): Outcome<Unit, String> {
        val voice = engine.voices.orEmpty().firstOrNull { it.name == voiceName }
        return when {
            voice == null -> Outcome.Failure("voice $voiceName disappeared")
            engine.setVoice(voice) != TextToSpeech.SUCCESS -> Outcome.Failure("engine refused voice $voiceName")
            else -> Outcome.Success(Unit)
        }
    }

    private suspend fun synthesizeToFile(engine: TextToSpeech, text: String): Outcome<PcmAudio, String> {
        val utteranceId = UUID.randomUUID().toString()
        val file = File(workDir, "$utteranceId.wav")
        return try {
            if (!workDir.isDirectory && !workDir.mkdirs()) {
                Outcome.Failure("cannot create $workDir")
            } else {
                when (
                    val error =
                        withTimeoutOrNull(config.systemChunkTimeoutMs) { awaitFile(engine, text, file, utteranceId) }
                ) {
                    null -> Outcome.Failure("timed out after ${config.systemChunkTimeoutMs} ms").also { engine.stop() }
                    NO_ERROR -> withContext(Dispatchers.IO) { readWav(file) }
                    else -> Outcome.Failure(error)
                }
            }
        } finally {
            pending.remove(utteranceId)
            file.delete()
        }
    }

    override fun shutdown() {
        pending.values.forEach { it.cancel() }
        pending.clear()
        tts?.shutdown()
        tts = null
    }

    /** Returns [NO_ERROR] once the file is written, or the engine's error. */
    private suspend fun awaitFile(engine: TextToSpeech, text: String, file: File, utteranceId: String): String =
        suspendCancellableCoroutine { continuation ->
            pending[utteranceId] = continuation
            continuation.invokeOnCancellation {
                pending.remove(utteranceId)
                engine.stop()
            }
            val queued = engine.synthesizeToFile(text, Bundle(), file, utteranceId)
            if (queued != TextToSpeech.SUCCESS) {
                pending.remove(utteranceId)
                continuation.resume("synthesizeToFile refused the request ($queued)")
            }
        } ?: NO_ERROR

    private fun readWav(file: File): Outcome<PcmAudio, String> = try {
        Outcome.Success(WavReader.read(file.readBytes()))
    } catch (e: IOException) {
        AudoraLog.e(TAG, "Unreadable output from $engineName", e)
        Outcome.Failure("unreadable output: ${e.message}")
    }

    private fun finish(utteranceId: String?, error: String?) {
        val continuation = utteranceId?.let { pending.remove(it) } ?: return
        if (continuation.isActive) continuation.resume(error)
    }

    private inner class Listener : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) = finish(utteranceId, null)

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = finish(utteranceId, "synthesis error")

        override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId, "synthesis error $errorCode")

        override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId, "stopped")
    }

    private fun Voice.toSystemVoice() = SystemVoice(
        name = name,
        language = locale.language,
        requiresNetwork = isNetworkConnectionRequired,
        installed = TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in features.orEmpty(),
        quality = quality,
        latency = latency,
    )

    private companion object {
        const val TAG = "Speech.SystemTts"
        const val NO_ERROR = ""
    }
}
