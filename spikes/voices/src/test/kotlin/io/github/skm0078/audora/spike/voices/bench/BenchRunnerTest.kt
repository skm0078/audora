// File: spikes/voices/src/test/kotlin/io/github/skm0078/audora/spike/voices/bench/BenchRunnerTest.kt
// Purpose: The runner times load, the cold first sentence and warm renders, survives engine failures, always
//          releases the engine, and saves one joined listening sample.

package io.github.skm0078.audora.spike.voices.bench

import io.github.skm0078.audora.spike.voices.catalog.BenchText
import io.github.skm0078.audora.spike.voices.catalog.TextKind
import io.github.skm0078.audora.spike.voices.engine.BenchEngine
import io.github.skm0078.audora.spike.voices.engine.EngineException
import io.github.skm0078.audora.spike.voices.engine.Rendered
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchRunnerTest {
    private var now = 0L
    private val clock = BenchClock { now }
    private val probe = object : DeviceProbe {
        override fun rssMb(): Long = 100

        override fun thermalStatus(): String = "none"
    }
    private val texts = listOf(
        BenchText("en-first", "en", TextKind.FIRST, "First."),
        BenchText("en-novel", "en", TextKind.PROSE, "Prose text here."),
        BenchText("en-formal", "en", TextKind.FORMAL, "Formal text."),
        BenchText("en-hard", "en", TextKind.HARD, "Hard 123."),
    )

    /** Each render takes 10 ms per character and returns 50 ms of audio per character at 1 kHz. */
    private inner class FakeEngine(private val loadMs: Long = 300, private val failOn: String? = null) : BenchEngine {
        override val voiceId = "fake"
        override val label = "Fake voice"
        override val lang = "en"
        override val threads: Int = 2
        override val details = listOf("fake details")
        var released = false

        override fun load() {
            now += loadMs
        }

        override fun render(text: String): Rendered {
            if (text == failOn) throw EngineException("boom")
            now += text.length * 10L
            return Rendered(ShortArray(text.length * 50), 1_000)
        }

        override fun release() {
            released = true
        }
    }

    @Test
    fun `measures load, cold first sentence and warm renders`() {
        val engine = FakeEngine()

        val run = BenchRunner(clock, probe).run(engine, texts, sampleSink = null) { false }

        assertNull(run.error)
        assertEquals(300L, run.loadMs)
        assertEquals(60L, run.coldFirstMs) // "First." = 6 chars
        assertEquals(360L, run.firstSentenceMs)
        assertEquals(texts.map { it.id }, run.warm.map { it.textId })
        assertEquals(0.2, run.rtfMean!!, 1e-9) // 10 ms render per 50 ms audio
        assertTrue(engine.released)
        assertEquals("none", run.thermalEnd)
    }

    @Test
    fun `reports an engine failure and still releases the engine`() {
        val engine = FakeEngine(failOn = "Formal text.")

        val run = BenchRunner(clock, probe).run(engine, texts, sampleSink = null) { false }

        assertEquals("boom", run.error)
        assertEquals(2, run.warm.size)
        assertTrue(engine.released)
        assertEquals(listOf("fake details"), run.details)
    }

    @Test
    fun `stops at cancellation without saving a sample`() {
        var saved = 0
        var renders = 0
        val engine = FakeEngine()

        val run = BenchRunner(clock, probe).run(engine, texts, sampleSink = { _, _ -> saved++ }) { renders++ >= 2 }

        assertEquals("cancelled", run.error)
        assertEquals(0, saved)
        assertTrue(engine.released)
    }

    @Test
    fun `saves prose then hard text joined by a silence`() {
        var sample: Rendered? = null
        val config = BenchConfig(sampleGapMs = 100)

        BenchRunner(clock, probe, config).run(FakeEngine(), texts, sampleSink = { _, s -> sample = s }) { false }

        val prose = "Prose text here.".length * 50
        val hard = "Hard 123.".length * 50
        assertEquals(prose + 100 + hard, sample!!.pcm.size)
    }

    @Test
    fun `fails cleanly without a first-sentence text`() {
        val run = BenchRunner(clock, probe).run(FakeEngine(), texts.drop(1), sampleSink = null) { false }

        assertTrue(run.error!!.contains("first-sentence"))
    }
}
