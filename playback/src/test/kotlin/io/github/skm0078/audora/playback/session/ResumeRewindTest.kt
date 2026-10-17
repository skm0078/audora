// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/session/ResumeRewindTest.kt
// Purpose: Resuming after a pause steps back by whole sentences, more the longer the pause (ADR-033).

package io.github.skm0078.audora.playback.session

import io.github.skm0078.audora.core.config.ResumeRewindConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class ResumeRewindTest {
    private var now = 1_000L
    private val config = ResumeRewindConfig(restartSentenceAfterMs = 20_000, previousSentenceAfterMs = 300_000)
    private val rewind = ResumeRewind(config) { now }

    private fun pauseFor(ms: Long, atSentenceEnd: Boolean = false): RewindStep {
        rewind.onPaused(atSentenceEnd)
        now += ms
        return rewind.onResumed()
    }

    @Test
    fun `the step grows with the pause`() {
        val cases = listOf(
            0L to RewindStep.NONE,
            19_999L to RewindStep.NONE,
            20_000L to RewindStep.RESTART_SENTENCE,
            299_999L to RewindStep.RESTART_SENTENCE,
            300_000L to RewindStep.PREVIOUS_SENTENCE,
            8 * 3_600_000L to RewindStep.PREVIOUS_SENTENCE,
        )
        for ((ms, expected) in cases) assertEquals("paused $ms ms", expected, pauseFor(ms))
    }

    @Test
    fun `a pause at a sentence end steps one sentence less`() {
        assertEquals(RewindStep.NONE, pauseFor(60_000, atSentenceEnd = true))
        assertEquals(RewindStep.RESTART_SENTENCE, pauseFor(600_000, atSentenceEnd = true))
    }

    @Test
    fun `resuming without a pause, twice, or after a new position does nothing`() {
        assertEquals(RewindStep.NONE, rewind.onResumed())

        assertEquals(RewindStep.RESTART_SENTENCE, pauseFor(60_000))
        assertEquals(RewindStep.NONE, rewind.onResumed())

        rewind.onPaused(atSentenceEnd = false)
        now += 600_000
        rewind.forget()
        assertEquals(RewindStep.NONE, rewind.onResumed())
    }
}
