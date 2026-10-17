// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/voice/StudyGapRendererTest.kt
// Purpose: "Leave a gap to say it back" adds silence as long as the sentence, within its floor and cap, only when on.

package io.github.skm0078.audora.playback.voice

import io.github.skm0078.audora.core.config.StudyConfig
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.TEST_SAMPLE_RATE
import io.github.skm0078.audora.playback.queue.SentenceRenderer
import io.github.skm0078.audora.playback.unit
import io.github.skm0078.audora.speech.SpeechError
import io.github.skm0078.audora.speech.audio.PcmAudio
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyGapRendererTest {
    private fun spoken(ms: Long) = SentenceRenderer {
        Outcome.Success(PcmAudio(ShortArray((ms * TEST_SAMPLE_RATE / MS_PER_SECOND).toInt()) { 1 }, TEST_SAMPLE_RATE))
    }

    private fun durationOf(renderer: SentenceRenderer): Long =
        (renderer.render(unit(0, 0)) as Outcome.Success).value.durationMs

    @Test
    fun `the gap is as long as the sentence`() {
        assertEquals(6_000L, durationOf(StudyGapRenderer(spoken(3_000), gaps = { true })))
    }

    @Test
    fun `a short sentence still leaves the floor and a long one stops at the cap`() {
        val config = StudyConfig()
        assertEquals(500L + config.minGapMs, durationOf(StudyGapRenderer(spoken(500), { true }, config)))
        assertEquals(40_000L + config.maxGapMs, durationOf(StudyGapRenderer(spoken(40_000), { true }, config)))
    }

    @Test
    fun `off, the audio is left as it is, and failures pass through`() {
        assertEquals(3_000L, durationOf(StudyGapRenderer(spoken(3_000), gaps = { false })))
        val failing = SentenceRenderer { Outcome.Failure(SpeechError.EngineUnavailable(EngineId.SHERPA, "broken")) }
        assertEquals(true, StudyGapRenderer(failing, gaps = { true }).render(unit(0, 0)) is Outcome.Failure)
    }

    private companion object {
        const val MS_PER_SECOND = 1_000L
    }
}
