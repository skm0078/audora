// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/queue/MeasuredRenderAheadTest.kt
// Purpose: The render window grows with the voice's measured slowness, within its floor and caps (P4-02).

package io.github.skm0078.audora.playback.queue

import io.github.skm0078.audora.core.config.QueueConfig
import io.github.skm0078.audora.core.config.RenderAheadConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.playback.unit
import io.github.skm0078.audora.speech.voices.VoiceSpeed
import org.junit.Assert.assertEquals
import org.junit.Test

class MeasuredRenderAheadTest {
    private val config = QueueConfig(
        prefetchUnits = 3,
        renderAhead = RenderAheadConfig(
            assumedRtf = 0.5,
            assumedCharsPerSecond = 10.0,
            longSentenceSeconds = 20.0,
            slowdownMargin = 1.5,
            minAheadSeconds = 8.0,
            maxAheadSeconds = 60.0,
            maxAheadUnits = 30,
        ),
    )

    /** [count] sentences of [seconds] each at 10 characters a second. */
    private fun sentences(count: Int, seconds: Int = 5, lang: Lang = Lang.EN): List<ReadingUnit> =
        List(count) { unit(0, it, lang).copy(text = "x".repeat(seconds * CPS)) }

    private fun speed(rtf: Double) = VoiceSpeed(rtf = rtf, charsPerSecond = CPS.toDouble(), measuredSeconds = 600.0)

    private fun ahead(units: List<ReadingUnit>, speeds: Map<Lang, VoiceSpeed?>): Int =
        MeasuredRenderAhead({ speeds[it] }).window(units, config).size - 1

    @Test
    fun `a fast voice keeps the spec's three sentences`() {
        // target = max(8 s, 0.1 × 20 × 1.5 = 3 s) = 8 s: two 5 s sentences reach it, the floor of 3 wins.
        assertEquals(3, ahead(sentences(30), mapOf(Lang.EN to speed(0.1))))
    }

    @Test
    fun `a slower voice renders further ahead, in proportion to its speed`() {
        assertEquals(3, ahead(sentences(30), mapOf(Lang.EN to speed(0.5)))) // 15 s
        assertEquals(5, ahead(sentences(30), mapOf(Lang.EN to speed(0.8)))) // 24 s
        assertEquals(9, ahead(sentences(30), mapOf(Lang.EN to speed(1.5)))) // 45 s
    }

    @Test
    fun `a voice slower than real time stops at the cap`() {
        assertEquals(12, ahead(sentences(30), mapOf(Lang.EN to speed(4.0)))) // capped at 60 s
        assertEquals(30, ahead(sentences(60, seconds = 1), mapOf(Lang.EN to speed(4.0)))) // capped at 30 units
    }

    @Test
    fun `an unmeasured voice uses the assumed speed`() {
        assertEquals(3, ahead(sentences(30), emptyMap())) // 0.5 → 15 s
    }

    @Test
    fun `the slowest voice among the queued sentences sets the target`() {
        val mixed = sentences(2) + sentences(28, lang = Lang.HI).map { it.copy(index = it.index + 2) }

        assertEquals(5, ahead(mixed, mapOf(Lang.EN to speed(0.1), Lang.HI to speed(0.8))))
    }

    @Test
    fun `short sentences mean more of them, and the window never passes what is queued`() {
        assertEquals(15, ahead(sentences(30, seconds = 1), mapOf(Lang.EN to speed(0.5)))) // 15 s of 1 s sentences
        assertEquals(4, ahead(sentences(5), mapOf(Lang.EN to speed(1.5))))
        assertEquals(0, ahead(sentences(1), mapOf(Lang.EN to speed(1.5))))
        assertEquals(emptyList<ReadingUnit>(), MeasuredRenderAhead({ null }).window(emptyList(), config))
    }

    @Test
    fun `the fixed policy is the original count`() {
        assertEquals(4, RenderAheadPolicy.FIXED.window(sentences(30), config).size)
    }

    @Test
    fun `pauses count as time to render in, but cost no render time`() {
        val paused = sentences(30).map { it.copy(pauseAfterMs = 1_000) }

        // 24 s at RTF 0.8: five 5 s sentences without pauses, four 6 s ones with them.
        assertEquals(4, ahead(paused, mapOf(Lang.EN to speed(0.8))))
        // Start hold at RTF 1, margin 1.2: each pending 5 s sentence takes 6 s to render. With 6.5 s buffered, the
        // second one is ready in time (12 s) only because the first one's pause makes 12.5 s playable, not 11.5 s.
        val policy = MeasuredRenderAhead({ speed(1.0) })
        assertEquals(false, policy.canStart(5.5, paused.take(1), config))
        assertEquals(true, policy.canStart(6.5, paused.take(2), config))
        assertEquals(false, policy.canStart(6.5, sentences(2), config))
    }

    @Test
    fun `a faster playback speed drains the queue sooner, so the window grows in proportion`() {
        fun at(playback: Double, rtf: Double) =
            MeasuredRenderAhead({ speed(rtf) }, { playback }).window(sentences(30), config).size - 1

        assertEquals(5, at(1.0, 0.8)) // 24 s
        assertEquals(10, at(2.0, 0.8)) // 48 s: twice the target
        assertEquals(3, at(2.0, 0.1)) // 6 s, below the 8 s floor: the floor of 3 sentences still wins
        assertEquals(12, at(2.0, 1.5)) // 90 s, capped at 60 s
        assertEquals(3, at(0.5, 0.8)) // 12 s at half speed
    }

    @Test
    fun `at double speed the start hold waits where normal speed would start`() {
        // RTF 0.5, margin 1.2: a pending 5 s sentence takes 3 s to render. At 1x, 4 s buffered covers it; at 2x
        // those 4 s play in 2 s, so the queue would run dry.
        val pending = sentences(1)

        assertEquals(true, MeasuredRenderAhead({ speed(0.5) }, { 1.0 }).canStart(4.0, pending, config))
        assertEquals(false, MeasuredRenderAhead({ speed(0.5) }, { 2.0 }).canStart(4.0, pending, config))
        assertEquals(true, MeasuredRenderAhead({ speed(0.5) }, { 2.0 }).canStart(6.5, pending, config))
    }

    @Test
    fun `a speed that is not positive counts as normal speed`() {
        val normal = MeasuredRenderAhead({ speed(0.8) })
        for (bad in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            val policy = MeasuredRenderAhead({ speed(0.8) }, { bad })

            assertEquals("speed $bad", normal.window(sentences(30), config), policy.window(sentences(30), config))
            // RTF 0.8, margin 1.2: 4.8 s to render a 5 s sentence, exactly as at 1x (not free, not stretched).
            assertEquals("speed $bad", true, policy.canStart(5.0, sentences(1), config))
            assertEquals("speed $bad", false, policy.canStart(4.0, sentences(1), config))
        }
    }

    private companion object {
        const val CPS = 10
    }
}
