// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/tutor/TutorRunStatsTest.kt
// Purpose: The tutor's run summary reports level, decoding speed and stretches as numbers, never words (Q-5).

package io.github.skm0078.audora.playback.tutor

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.tutor.PageWords
import io.github.skm0078.audora.core.tutor.ReadAlong
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorRunStatsTest {
    private val start = ReadAlong.start(PageWords.of(listOf("The secret garden was green."), Lang.EN))

    @Test
    fun `the summary counts audio and stretches without any words`() {
        val stats = TutorRunStats(sampleRateHz = 16_000, start)
        // One second: half at a speaking level (about -26 dBFS), half silent.
        repeat(5) { stats.audio(FloatArray(1_600) { if (it % 2 == 0) 0.05f else -0.05f }) }
        repeat(5) { stats.audio(FloatArray(1_600)) }
        stats.decoded(250_000_000L)
        stats.stretch(start.follow(PageWords.heard("the secret"), final = false), final = false)
        val step = start.follow(PageWords.heard("the secret garden"), final = true)
        stats.stretch(step, final = true)
        stats.queueFull.incrementAndGet()
        stats.modelLoadMs = 1_200

        val summary = stats.summary()

        listOf(
            "audio=1.0s",
            "quiet=50%",
            "clipped=0",
            "decode=0.25x realtime",
            "queueFull=1",
            "modelLoadMs=1200",
            "final=1 partial=1",
            "moved=1 held=0 repeat=0",
            "heardWords=3",
            "place=3/5",
            "finished=false",
        ).forEach { assertTrue("$it in $summary", it in summary) }
        listOf("secret", "garden", "green").forEach { assertFalse("$it in $summary", it in summary) }
    }

    @Test
    fun `a run that heard nothing still sums up`() {
        val summary = TutorRunStats(sampleRateHz = 16_000, start).summary()

        assertTrue(summary, "audio=0.0s" in summary)
        assertTrue(summary, "place=0/5" in summary)
    }
}
