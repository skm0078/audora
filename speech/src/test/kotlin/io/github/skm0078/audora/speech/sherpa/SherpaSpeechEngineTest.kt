// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/sherpa/SherpaSpeechEngineTest.kt
// Purpose: The sherpa engine's own logic over a fake model: loading, the loaded-voice limit, trimming, errors,
//          cancellation, release.

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
    private val hindi = spec("supertonic-hi", Lang.HI)
    private val loaded = mutableListOf<Pair<String, FakeSynthesizer>>()
    private var loadFailure: SpeechError? = null

    private val factory = SherpaSynthesizerFactory { spec ->
        loadFailure?.let { return@SherpaSynthesizerFactory Outcome.Failure(it) }
        val synthesizer = FakeSynthesizer()
        loaded += spec.id to synthesizer
        Outcome.Success(synthesizer)
    }

    private val engine = SherpaSpeechEngine(listOf(english, other), factory, Dispatchers.IO.limitedParallelism(1))

    /** Two voices kept loaded, the default. */
    private val bilingual =
        SherpaSpeechEngine(listOf(english, other, hindi), factory, Dispatchers.IO.limitedParallelism(1), 2)

    private fun synthesizerOf(id: String): FakeSynthesizer = loaded.single { it.first == id }.second

    @Test
    fun `renders with the voice's speaker and returns 16-bit PCM at the model's rate`() = runBlocking {
        val audio = requireNotNull(engine.render("Hello there.", english.voiceId).getOrNull())

        assertArrayEquals(shortArrayOf(16_384, -16_383), audio.samples)
        assertEquals(22_050, audio.sampleRate)
        assertEquals(listOf("Hello there." to 3), loaded.single().second.calls)
        assertEquals(listOf(1f), loaded.single().second.rates)
    }

    @Test
    fun `the speaking rate goes to the model`() = runBlocking {
        engine.render("Hello there.", english.voiceId, rate = 1.75f)

        assertEquals(listOf(1.75f), loaded.single().second.rates)
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
    fun `with one voice allowed, switching voices releases the previous model first`() = runBlocking {
        val single = SherpaSpeechEngine(listOf(english, other), factory, Dispatchers.IO.limitedParallelism(1), 1)

        single.render("One.", english.voiceId)
        single.render("Two.", other.voiceId)

        assertEquals(listOf("piper-en-ljspeech", "piper-en-cori"), loaded.map { it.first })
        assertTrue(loaded[0].second.released)
        assertFalse(loaded[1].second.released)
    }

    @Test
    fun `two voices alternating are each loaded once`() = runBlocking {
        repeat(3) {
            bilingual.render("English.", english.voiceId)
            bilingual.render("Hindi.", hindi.voiceId)
        }

        assertEquals(listOf("piper-en-ljspeech", "supertonic-hi"), loaded.map { it.first })
        assertTrue(loaded.none { it.second.released })
        assertEquals(3, synthesizerOf("supertonic-hi").calls.size)
    }

    @Test
    fun `a third voice evicts the least recently used one`() = runBlocking {
        bilingual.render("One.", english.voiceId)
        bilingual.render("Two.", hindi.voiceId)
        bilingual.render("Three.", english.voiceId) // English is now the most recently used

        bilingual.render("Four.", other.voiceId)

        assertTrue(synthesizerOf("supertonic-hi").released)
        assertFalse(synthesizerOf("piper-en-ljspeech").released)
        assertFalse(synthesizerOf("piper-en-cori").released)
        bilingual.render("Five.", english.voiceId)
        assertEquals(3, loaded.size) // English was still loaded
    }

    @Test
    fun `trimming memory keeps only the most recently used voice`() = runBlocking {
        bilingual.render("One.", hindi.voiceId)
        bilingual.render("Two.", english.voiceId)

        bilingual.trimMemory()

        assertTrue(synthesizerOf("supertonic-hi").released)
        assertFalse(synthesizerOf("piper-en-ljspeech").released)
        bilingual.render("Three.", english.voiceId)
        assertEquals(2, loaded.size)
    }

    @Test
    fun `a trim during a render waits for it, and keeps the voice it used`() = runBlocking {
        bilingual.render("Warm up.", english.voiceId)
        bilingual.render("Warm up.", hindi.voiceId)
        val synthesizer = synthesizerOf("piper-en-ljspeech")
        val gate = CountDownLatch(1)
        synthesizer.gate = gate

        val render = async(Dispatchers.Default) { bilingual.render("In flight.", english.voiceId) }
        assertTrue(synthesizer.entered.await(5, TimeUnit.SECONDS))
        bilingual.trimMemory()
        assertFalse("trimmed under a running render", synthesizerOf("supertonic-hi").released)

        gate.countDown()
        assertTrue(withTimeout(5_000) { render.await() } is Outcome.Success)
        assertTrue(synthesizerOf("supertonic-hi").released)
        assertFalse(synthesizer.released)
    }

    @Test
    fun `release unloads every loaded voice`() = runBlocking {
        bilingual.render("One.", english.voiceId)
        bilingual.render("Two.", hindi.voiceId)

        bilingual.release()

        assertEquals(2, loaded.size)
        assertTrue(loaded.all { it.second.released })
    }

    @Test
    fun `a voice in use is never released mid-render, even when another voice is evicted`() = runBlocking {
        val single = SherpaSpeechEngine(listOf(english, hindi), factory, Dispatchers.IO.limitedParallelism(1), 1)
        single.render("Warm up.", english.voiceId)
        val synthesizer = loaded.single().second
        val gate = CountDownLatch(1)
        synthesizer.gate = gate

        val inFlight = async(Dispatchers.Default) { single.render("In flight.", english.voiceId) }
        assertTrue(synthesizer.entered.await(5, TimeUnit.SECONDS))
        val next = async(Dispatchers.Default) { single.render("Next.", hindi.voiceId) }
        assertFalse("evicted under a running render", synthesizer.released)

        gate.countDown()
        assertTrue(withTimeout(5_000) { inFlight.await() } is Outcome.Success)
        assertTrue(withTimeout(5_000) { next.await() } is Outcome.Success)
        assertTrue(synthesizer.released) // evicted for Hindi only once its render was done
        assertFalse(synthesizerOf("supertonic-hi").released)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a limit below one voice is refused`() {
        SherpaSpeechEngine(listOf(english), factory, Dispatchers.IO.limitedParallelism(1), 0)
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
        assertTrue(loaded[0].second.released) // not kept beside the new files, though two voices fit
        assertEquals(other.label, requireNotNull(live.voices().getOrNull()).single { it.id == other.voiceId }.label)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a fixed list with duplicate ids is refused`() {
        SherpaSpeechEngine(listOf(english, english.copy(label = "again")), factory)
    }
}
