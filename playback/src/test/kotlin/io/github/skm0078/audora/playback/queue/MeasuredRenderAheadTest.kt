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
        MeasuredRenderAhead { speeds[it] }.window(units, config).size - 1

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
        assertEquals(emptyList<ReadingUnit>(), MeasuredRenderAhead { null }.window(emptyList(), config))
    }

    @Test
    fun `the fixed policy is the original count`() {
        assertEquals(4, RenderAheadPolicy.FIXED.window(sentences(30), config).size)
    }

    private companion object {
        const val CPS = 10
    }
}
