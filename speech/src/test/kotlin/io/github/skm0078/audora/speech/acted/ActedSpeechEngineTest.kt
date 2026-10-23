// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/acted/ActedSpeechEngineTest.kt
// Purpose: The acted voice's engine: takes, retries, pieces and failures, with a fake model (ADR-059).

package io.github.skm0078.audora.speech.acted

import io.github.skm0078.audora.core.config.ActedConfig
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Feeling
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.SpeechError
import java.io.StringReader
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActedSpeechEngineTest {
    private val tokenizer = ChatterboxTokenizer.read(
        StringReader("template\t9\t8\nunknown\t1\nadded\t2\t1\t[SPACE]\nadded\t3\t0\t[hi]\nvocab\t10\tक\n"),
    )

    @Test
    fun `a good take is decoded and returned at 24 kHz`() = runTest {
        val model = FakeModel(listOf(Script(tokens = 50, stopped = true)))
        val result = engine(model).render(TEXT, ActedSpeechEngine.HINDI, 1f, Feeling.NEUTRAL)

        val audio = (result as Outcome.Success).value
        assertEquals(ActedModel.SAMPLE_RATE, audio.sampleRate)
        assertEquals(50 * SAMPLES_PER_TOKEN, audio.samples.size)
        assertEquals(listOf(0.5f), model.strengths)
    }

    @Test
    fun `a runaway take is tried again plainer, and the retry is used`() = runTest {
        val model = FakeModel(listOf(Script(tokens = 200, stopped = false), Script(tokens = 50, stopped = true)))
        val result = engine(model).render(TEXT, ActedSpeechEngine.HINDI, 1f, Feeling.SAD)

        assertTrue(result is Outcome.Success)
        assertEquals(listOf(0.65f, 0.4f), model.strengths)
        assertEquals(1, model.decoded)
    }

    @Test
    fun `when every take fails the sentence fails, for the live voice to read`() = runTest {
        val model = FakeModel(listOf(Script(tokens = 3, stopped = true), Script(tokens = 3, stopped = true)))
        val result = engine(model).render(TEXT, ActedSpeechEngine.HINDI, 1f, Feeling.NEUTRAL)

        assertTrue((result as Outcome.Failure).error is SpeechError.SynthesisFailed)
        assertEquals(0, model.decoded)
    }

    @Test
    fun `long text is read in pieces joined by a pause`() = runTest {
        val config = ActedConfig(maxPieceChars = 40, pieceGapMs = 100)
        val text = "क".repeat(30) + ", " + "क".repeat(30)
        val model = FakeModel(List(2) { Script(tokens = 50, stopped = true) })
        val result = engine(model, config).render(text, ActedSpeechEngine.HINDI, 1f, Feeling.NEUTRAL)

        val audio = (result as Outcome.Success).value
        assertEquals(2 * 50 * SAMPLES_PER_TOKEN + ActedModel.SAMPLE_RATE / 10, audio.samples.size)
    }

    @Test
    fun `another voice, a missing pack or a broken model fail without throwing`() = runTest {
        val other = engine(FakeModel(emptyList())).render(TEXT, VoiceId(EngineId.ACTED, "other"), 1f)
        val missing = ActedSpeechEngine({ null }, dispatcher = StandardTestDispatcher(testScheduler))
            .render(TEXT, ActedSpeechEngine.HINDI, 1f)
        val broken = engine(FakeModel(emptyList(), failSpeak = true)).render(TEXT, ActedSpeechEngine.HINDI, 1f)

        assertTrue((other as Outcome.Failure).error is SpeechError.VoiceUnavailable)
        assertTrue((missing as Outcome.Failure).error is SpeechError.EngineUnavailable)
        assertTrue((broken as Outcome.Failure).error is SpeechError.SynthesisFailed)
    }

    @Test
    fun `it reads Hindi only, and whole sentences`() {
        val engine = ActedSpeechEngine({ null })
        assertTrue(engine.keepsSentencesWhole(ActedSpeechEngine.HINDI))
        assertTrue(engine.supports(Lang.HI))
    }

    private fun TestScope.engine(model: FakeModel, config: ActedConfig = ActedConfig()) = ActedSpeechEngine(
        { Outcome.Success(LoadedActed(model, tokenizer)) },
        config,
        StandardTestDispatcher(testScheduler),
    )

    private class Script(val tokens: Int, val stopped: Boolean)

    private class FakeModel(private val scripts: List<Script>, private val failSpeak: Boolean = false) : ActedModel {
        val strengths = ArrayList<Float>()
        var decoded = 0
        private var next = 0

        override fun speak(
            textTokens: LongArray,
            strength: Float,
            maxTokens: Int,
            repetitionPenalty: Float,
        ): ActedSpeech {
            check(!failSpeak) { "onnx runtime failed" }
            strengths.add(strength)
            val script = scripts[next++]
            return ActedSpeech(LongArray(minOf(script.tokens, maxTokens)) { 7L }, script.stopped)
        }

        override fun decode(speech: LongArray): FloatArray {
            decoded++
            return FloatArray(speech.size * SAMPLES_PER_TOKEN) { 0.1f }
        }

        override fun cancel() = Unit

        override fun close() = Unit
    }

    private companion object {
        val TEXT = "क".repeat(50)
        const val SAMPLES_PER_TOKEN = ActedModel.SAMPLE_RATE / ActedModel.TOKENS_PER_SECOND
    }
}
