// File: speech/src/androidTest/kotlin/io/github/skm0078/audora/speech/SpeechEnginesDeviceTest.kt
// Purpose: Both engines on a real Android runtime: the bundled voice renders real speech from the APK's assets,
//          cancellation works on the native engine, and the system engine renders or says clearly why it cannot.

package io.github.skm0078.audora.speech

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.getOrNull
import io.github.skm0078.audora.speech.audio.PcmAudio
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpeechEnginesDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val engines = mutableListOf<SpeechEngine>()

    @After
    fun releaseEngines() = engines.forEach { it.release() }

    @Test
    fun bundledVoiceRendersASentenceToSpeech() = runBlocking {
        val bundled = bundled()
        assertEquals(Lang.EN, bundled.voice.lang)

        val audio = render(bundled.engine, "Chapter One. The house stood at the end of a quiet street.", bundled)

        Log.i(TAG, "bundled ${bundled.voice.id}: $audio, rms ${rms(audio)}")
        assertTrue("sample rate ${audio.sampleRate}", audio.sampleRate in 16_000..48_000)
        assertTrue("duration ${audio.durationMs} ms", audio.durationMs in 2_000L..10_000L)
        assertTrue("silent output, rms ${rms(audio)}", rms(audio) > MIN_SPEECH_RMS)
    }

    @Test
    fun cancellingABundledRenderLeavesTheEngineUsable() = runBlocking {
        val bundled = bundled()
        render(bundled.engine, "Warm up.", bundled) // loads the model

        val long = List(SENTENCES) { "This is sentence number ${it + 1} of a long paragraph." }.joinToString(" ")
        val job = async(Dispatchers.Default) { bundled.engine.render(long, bundled.voice.id) }
        delay(CANCEL_AFTER_MS)
        withTimeout(CANCEL_DEADLINE_MS) { job.cancelAndJoin() }

        assertTrue(job.isCancelled)
        assertTrue(render(bundled.engine, "Still here.", bundled).durationMs > 0)
    }

    @Test
    fun systemEngineRendersOrExplainsWhyNot() = runBlocking {
        val engine = SpeechEngines.system(context).also { engines += it }
        when (val best = engine.bestVoice(Lang.EN)) {
            is Outcome.Failure -> {
                // CI's emulator image may ship no TTS engine or no English data: the reason must be typed and clear.
                val error = best.error
                Log.i(TAG, "system engine unavailable: ${error.message}")
                assertTrue(
                    "unexpected $error",
                    error is SpeechError.EngineUnavailable || error is SpeechError.VoiceUnavailable,
                )
                assertTrue(error.message.isNotBlank())
            }

            is Outcome.Success -> {
                val audio = engine.render("The house stood at the end of a quiet street.", best.value)
                val pcm = requireNotNull(audio.getOrNull()) { "system render failed: $audio" }
                Log.i(TAG, "system ${best.value}: $pcm")
                assertTrue(pcm.durationMs > 0)
            }
        }
    }

    private fun bundled(): BundledSpeech {
        val bundled = SpeechEngines.bundled(context).getOrNull()
            ?: error("bundled voice missing from the APK: ${SpeechEngines.bundled(context)}")
        engines += bundled.engine
        return bundled
    }

    private suspend fun render(engine: SpeechEngine, text: String, bundled: BundledSpeech): PcmAudio =
        when (val result = engine.render(text, bundled.voice.id)) {
            is Outcome.Success -> result.value
            is Outcome.Failure -> error("render failed: ${result.error.message}")
        }

    private fun rms(audio: PcmAudio): Double {
        if (audio.samples.isEmpty()) return 0.0
        val sum = audio.samples.sumOf { it.toDouble() * it }
        return sqrt(sum / audio.samples.size) / Short.MAX_VALUE
    }

    private companion object {
        const val TAG = "AudoraSpeechTest"
        const val MIN_SPEECH_RMS = 0.01
        const val SENTENCES = 12
        const val CANCEL_AFTER_MS = 200L
        const val CANCEL_DEADLINE_MS = 10_000L
    }
}
