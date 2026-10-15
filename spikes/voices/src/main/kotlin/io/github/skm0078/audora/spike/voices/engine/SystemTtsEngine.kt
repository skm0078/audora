// File: spikes/voices/src/main/kotlin/io/github/skm0078/audora/spike/voices/engine/SystemTtsEngine.kt
// Purpose: Measures the phone's own TTS (usually Google) for one language through synthesizeToFile, the path
//          P1-03 will use. Picks an installed offline voice only; a network voice would not count.

package io.github.skm0078.audora.spike.voices.engine

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

data class SystemTtsConfig(val initTimeoutSeconds: Long = 15, val renderTimeoutSeconds: Long = 60)

class SystemTtsEngine(
    private val context: Context,
    override val lang: String,
    private val workDir: File,
    private val config: SystemTtsConfig = SystemTtsConfig(),
) : BenchEngine {
    override val voiceId: String get() = "system-$lang"
    override val label: String get() = "Phone's system voice ($lang)"
    override val threads: Int? get() = null
    override val details: List<String> get() = detailLines.toList()

    private val detailLines = mutableListOf<String>()
    private var tts: TextToSpeech? = null
    private val pending = AtomicReference<CountDownLatch?>()
    private val failure = AtomicReference<String?>()

    override fun load() {
        val ready = CountDownLatch(1)
        var status = TextToSpeech.ERROR
        val engine = TextToSpeech(context) { code ->
            status = code
            ready.countDown()
        }
        tts = engine
        if (!ready.await(config.initTimeoutSeconds, TimeUnit.SECONDS) || status != TextToSpeech.SUCCESS) {
            throw EngineException("$voiceId: system TTS did not initialise (status $status)")
        }
        detailLines += "engine: ${engine.defaultEngine}"
        val voice = pickOfflineVoice(engine.voices.orEmpty())
            ?: throw EngineException("$voiceId: no offline '$lang' voice installed in ${engine.defaultEngine}")
        engine.voice = voice
        detailLines += "voice: ${voice.name} (quality ${voice.quality}, latency ${voice.latency})"
        engine.setOnUtteranceProgressListener(Listener())
        AudoraLog.i(TAG, "System TTS ready for $lang: ${engine.defaultEngine} / ${voice.name}")
    }

    override fun render(text: String): Rendered {
        val engine = tts ?: throw EngineException("$voiceId: render before load")
        val out = File(workDir, "system-$lang.wav")
        out.delete()
        val done = CountDownLatch(1)
        pending.set(done)
        failure.set(null)
        val result = engine.synthesizeToFile(text, Bundle(), out, UUID.randomUUID().toString())
        if (result != TextToSpeech.SUCCESS) throw EngineException("$voiceId: synthesizeToFile refused ($result)")
        if (!done.await(config.renderTimeoutSeconds, TimeUnit.SECONDS)) throw EngineException("$voiceId: timed out")
        failure.get()?.let { throw EngineException("$voiceId: $it") }
        return try {
            WavFile.read(out.readBytes())
        } catch (e: IOException) {
            throw EngineException("$voiceId: unreadable output WAV", e)
        }
    }

    override fun release() {
        tts?.shutdown()
        tts = null
    }

    private fun pickOfflineVoice(voices: Set<Voice>): Voice? = voices
        .filter { it.locale.language == Locale(lang).language }
        .filterNot { it.isNetworkConnectionRequired }
        .filterNot { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED in it.features.orEmpty() }
        .maxWithOrNull(compareBy<Voice>({ it.quality }, { -it.latency }))

    private inner class Listener : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            pending.get()?.countDown()
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            failure.set("synthesis error")
            pending.get()?.countDown()
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            failure.set("synthesis error $errorCode")
            pending.get()?.countDown()
        }
    }

    private companion object {
        const val TAG = "S1.SystemTts"
    }
}
