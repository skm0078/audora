// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/sherpa/SherpaSpeechEngineTest.kt
// Purpose: The sherpa engine's own logic over a fake model: loading, voice switching, errors, cancellation, release.

package io.github.skm0078.audora.speech.sherpa

import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.errorOrNull
import io.github.skm0078.audora.core.result.getOrNull
import io.github.skm0078.audora.speech.SpeechError
import io.github.skm0078.audora.speech.SpeechError.VoiceUnavailable.Reason
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SherpaSpeechEngineTest {
    private val english = spec("piper-en-ljspeech", speakerId = 3)
    private val other = spec("piper-en-cori")
    private val loaded = mutableListOf<Pair<String, FakeSynthesizer>>()
    private var loadFailure: SpeechError? = null

    private val factory = SherpaSynthesizerFactory { spec ->
        loadFailure?.let { return@SherpaSynthesizerFactory Outcome.Failure(it) }
        val synthesizer = FakeSynthesizer()
        loaded += spec.id to synthesizer
        Outcome.Success(synthesizer)
    }

    private val engine = SherpaSpeechEngine(listOf(english, other), factory, Dispatchers.IO.limitedParallelism(1))

    @Test
    fun `renders with the voice's speaker and returns 16-bit PCM at the model's rate`() = runBlocking {
        val audio = requireNotNull(engine.render("Hello there.", english.voiceId).getOrNull())

        assertArrayEquals(shortArrayOf(16_384, -16_383), audio.samples)
        assertEquals(22_050, audio.sampleRate)
        assertEquals(listOf("Hello there." to 3), loaded.single().second.calls)
    }

    @Test
    fun `lists its voices without loading a model, and loads lazily once`() = runBlocking {
        assertEquals(listOf(english.voiceId, other.voiceId), requireNotNull(engine.voices().getOrNull()).map { it.id })
        assertTrue(engine.supports(Lang.EN))
        assertFalse(engine.supports(Lang.HI))
        assertTrue(loaded.isEmpty())

        engine.render("One.", english.voiceId)
        engine.render("Two.", english.voiceId)

        assertEquals(1, loaded.size)
        assertEquals(2, loaded.single().second.calls.size)
    }

    @Test
    fun `switching voices releases the previous model first`() = runBlocking {
        engine.render("One.", english.voiceId)
        engine.render("Two.", other.voiceId)

        assertEquals(listOf("piper-en-ljspeech", "piper-en-cori"), loaded.map { it.first })
        assertTrue(loaded[0].second.released)
        assertFalse(loaded[1].second.released)
    }

    @Test
    fun `an unknown voice is a typed failure and loads nothing`() = runBlocking {
        val error = engine.render("Hi.", VoiceId(EngineId.SHERPA, "nope")).errorOrNull()

        assertEquals(Reason.UNKNOWN_VOICE, (error as SpeechError.VoiceUnavailable).reason)
        assertTrue(loaded.isEmpty())
    }

    @Test
    fun `a load failure is returned as is and the next render tries again`() = runBlocking {
        val missing = SpeechError.VoiceUnavailable(EngineId.SHERPA, Reason.MODEL_FILES_MISSING, english.voiceId)
        loadFailure = missing

        assertSame(missing, engine.render("Hi.", english.voiceId).errorOrNull())

        loadFailure = null
        assertTrue(engine.render("Hi.", english.voiceId) is Outcome.Success)
    }

    @Test
    fun `blank text renders silence without calling the model`() = runBlocking {
        val audio = requireNotNull(engine.render("  ", english.voiceId).getOrNull())

        assertEquals(0, audio.samples.size)
        assertEquals(22_050, audio.sampleRate)
        assertTrue(loaded.single().second.calls.isEmpty())
    }

    @Test
    fun `a native error becomes SynthesisFailed and the engine stays usable`() = runBlocking {
        engine.render("Warm up.", english.voiceId)
        val synthesizer = loaded.single().second
        synthesizer.failure = IllegalStateException("onnx runtime error")

        val error = engine.render("Hi.", english.voiceId).errorOrNull() as SpeechError.SynthesisFailed
        assertEquals("onnx runtime error", error.detail)
        assertTrue(error.cause is IllegalStateException)

        synthesizer.failure = null
        assertTrue(engine.render("Hi.", english.voiceId) is Outcome.Success)
    }

    @Test
    fun `cancelling a render stops the model and leaves the engine usable`() = runBlocking {
        engine.render("Warm up.", english.voiceId)
        val synthesizer = loaded.single().second
        synthesizer.gate = CountDownLatch(1)

        val render = async(Dispatchers.Default) { engine.render("A long sentence.", english.voiceId) }
        assertTrue(synthesizer.entered.await(5, TimeUnit.SECONDS))
        withTimeout(5_000) { render.cancelAndJoin() }

        assertTrue(render.isCancelled)
        assertTrue(synthesizer.stoppedEarly)
        synthesizer.gate = null
        assertTrue(engine.render("Next.", english.voiceId) is Outcome.Success)
    }

    @Test
    fun `release frees the model and later renders fail typed`() = runBlocking {
        engine.render("Hi.", english.voiceId)

        engine.release()

        assertTrue(loaded.single().second.released)
        val error = engine.render("Hi.", english.voiceId).errorOrNull()
        assertTrue(error is SpeechError.EngineUnavailable)
    }

    @Test
    fun `release during a render frees the model only after that render ends`() = runBlocking {
        engine.render("Warm up.", english.voiceId)
        val synthesizer = loaded.single().second
        val gate = CountDownLatch(1)
        synthesizer.gate = gate

        val render = async(Dispatchers.Default) { engine.render("In flight.", english.voiceId) }
        assertTrue(synthesizer.entered.await(5, TimeUnit.SECONDS))
        engine.release()
        assertFalse("freed under a running render", synthesizer.released)

        gate.countDown()
        assertTrue(withTimeout(5_000) { render.await() } is Outcome.Success)
        assertTrue(synthesizer.released)
    }

    @Test
    fun `voices from a live source are seen as they are installed and deleted`() = runBlocking {
        val installed = mutableListOf<SherpaVoiceSpec>()
        val live = SherpaSpeechEngine({ listOf(english) + installed }, factory, Dispatchers.IO.limitedParallelism(1))
        val hindi = spec("custom-hindi-1", Lang.HI)

        assertFalse(live.supports(Lang.HI))
        installed += hindi
        assertTrue(live.supports(Lang.HI))
        assertTrue(live.render("Namaste.", hindi.voiceId) is Outcome.Success)

        installed.clear()
        val error = live.render("Namaste.", hindi.voiceId).errorOrNull()
        assertEquals(Reason.UNKNOWN_VOICE, (error as SpeechError.VoiceUnavailable).reason)
    }

    @Test
    fun `an updated voice is reloaded and the first voice with an id wins`() = runBlocking {
        var current = other
        val live = SherpaSpeechEngine({ listOf(english, current, other.copy(label = "shadowed")) }, factory)

        live.render("One.", other.voiceId)
        current = other.copy(contentVersion = "v2")
        live.render("Two.", other.voiceId)

        assertEquals(2, loaded.size)
        assertTrue(loaded[0].second.released)
        assertEquals(other.label, requireNotNull(live.voices().getOrNull()).single { it.id == other.voiceId }.label)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a fixed list with duplicate ids is refused`() {
        SherpaSpeechEngine(listOf(english, english.copy(label = "again")), factory)
    }
}
