// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/queue/RenderAheadSimulationTest.kt
// Purpose: Simulates an hour of reading on one render thread to show the measured window prevents stalls (P4-02).

package io.github.skm0078.audora.playback.queue

import io.github.skm0078.audora.core.config.QueueConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.playback.unit
import io.github.skm0078.audora.speech.voices.VoiceSpeed
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The real queue's timing, without threads: one worker renders the window's sentences in order, each taking
 * `rtf × its length`, slowed by per-sentence noise and by hot spells; the player plays sentence after sentence and
 * stalls whenever the next one is not rendered when the current one ends. The window is re-planned at each sentence
 * change, as BookSession does, and the first sentence waits for the policy's start check (or the hold limit), as
 * RenderAheadQueue does. The book mixes everyday sentences with run-ons, the case that stalls a fixed window. The
 * policy is told the voice's average speed; the phone is noisier and has hot spells it does not know about.
 */
class RenderAheadSimulationTest {
    private val config = QueueConfig()

    private class Outcome(val stalls: Int, val stalledSeconds: Double)

    private fun book(seed: Int): List<Double> {
        val random = Random(seed)
        val lengths = ArrayList<Double>()
        var total = 0.0
        while (total < BOOK_SECONDS) {
            val seconds = if (random.nextDouble() < RUN_ON_SHARE) {
                random.nextDouble(RUN_ON_MIN, RUN_ON_MAX)
            } else {
                random.nextDouble(SHORT_MIN, SHORT_MAX)
            }
            lengths.add(seconds)
            total += seconds
        }
        return lengths
    }

    private fun simulate(policy: RenderAheadPolicy, rtf: Double, seed: Int): Outcome {
        val lengths = book(seed)
        val random = Random(seed + 1)
        val units = lengths.mapIndexed { i, seconds ->
            unit(0, i, Lang.EN).copy(text = "x".repeat((seconds * CHARS_PER_SECOND).toInt().coerceAtLeast(1)))
        }
        // Actual render time: noise per sentence, and every HOT_EVERY sentences a hot spell slows the phone down.
        val renderSeconds = lengths.mapIndexed { i, seconds ->
            val hot = if ((i / HOT_SPELL) % HOT_EVERY == HOT_EVERY - 1) HOT_SLOWDOWN else 1.0
            seconds * rtf * hot * random.nextDouble(1 - NOISE, 1 + NOISE)
        }
        val finished = DoubleArray(units.size) { Double.POSITIVE_INFINITY }
        var opened = -1

        fun openWindow(playing: Int, at: Double) {
            val candidates = units.subList(playing, minOf(units.size, playing + 1 + config.renderAhead.maxAheadUnits))
            val end = playing + policy.window(candidates, config).size - 1
            for (k in opened + 1..end) {
                val previous = if (k == 0) 0.0 else finished[k - 1]
                finished[k] = maxOf(previous, at) + renderSeconds[k]
            }
            opened = maxOf(opened, end)
        }

        openWindow(0, 0.0)
        var start = startTime(policy, units, lengths, finished, opened)
        var stalls = 0
        var stalled = 0.0
        for (playing in 0 until units.size - 1) {
            openWindow(playing, start)
            val end = start + lengths[playing]
            // The next sentence is always in the window (prefetchUnits >= 1), so its finish time is known.
            val ready = finished[playing + 1]
            if (ready > end + TOLERANCE) {
                stalls++
                stalled += ready - end
            }
            start = maxOf(end, ready)
        }
        return Outcome(stalls, stalled)
    }

    /** When the queue hands out the first sentence: at a render that passes the start check, or at the limit. */
    private fun startTime(
        policy: RenderAheadPolicy,
        units: List<ReadingUnit>,
        lengths: List<Double>,
        finished: DoubleArray,
        opened: Int,
    ): Double {
        val limit = finished[0] + config.renderAhead.maxStartHoldSeconds
        for (rendered in 0..opened) {
            val at = finished[rendered]
            if (at > limit) break
            val buffered = lengths.subList(0, rendered + 1).sum()
            if (policy.canStart(buffered, units.subList(rendered + 1, opened + 1), config)) return at
        }
        return minOf(limit, finished[opened])
    }

    private fun measured(rtf: Double) = MeasuredRenderAhead { VoiceSpeed(rtf, CHARS_PER_SECOND, MEASURED) }

    @Test
    fun `voices well inside the usable limit never stall with the measured window`() {
        for (rtf in listOf(0.1, 0.2, 0.4, 0.6)) {
            for (seed in SEEDS) {
                val outcome = simulate(measured(rtf), rtf, seed)
                assertEquals("rtf $rtf seed $seed stalled ${outcome.stalledSeconds} s", 0, outcome.stalls)
            }
        }
    }

    @Test
    fun `the fixed three-sentence window stalls on the same books once the voice is not fast`() {
        val stalls = SEEDS.sumOf { simulate(RenderAheadPolicy.FIXED, rtf = 0.6, seed = it).stalls }

        assertTrue("fixed window should stall at rtf 0.6, stalled $stalls times", stalls > 0)
    }

    @Test
    fun `at the usable limit the measured window stalls far less than the fixed one`() {
        val measured = SEEDS.sumOf { simulate(measured(0.79), rtf = 0.79, seed = it).stalledSeconds }
        val fixed = SEEDS.sumOf { simulate(RenderAheadPolicy.FIXED, rtf = 0.79, seed = it).stalledSeconds }

        assertTrue("measured $measured s vs fixed $fixed s", measured * IMPROVEMENT < fixed)
    }

    @Test
    fun `a fast voice is never held at the start`() {
        for (seed in SEEDS) {
            val lengths = book(seed)
            val units = lengths.mapIndexed { i, seconds ->
                unit(0, i).copy(text = "x".repeat((seconds * CHARS_PER_SECOND).toInt()))
            }
            assertTrue(measured(0.05).canStart(lengths[0], units.subList(1, 4), config))
        }
    }

    @Test
    fun `a voice slower than real time stalls whatever the window, which is why it is not offered`() {
        val outcome = simulate(measured(1.4), rtf = 1.4, seed = SEEDS.first())

        assertTrue(outcome.stalledSeconds > 0)
    }

    private companion object {
        const val BOOK_SECONDS = 3_600.0
        const val SHORT_MIN = 2.0
        const val SHORT_MAX = 12.0
        const val RUN_ON_SHARE = 0.08
        const val RUN_ON_MIN = 18.0
        const val RUN_ON_MAX = 30.0
        const val CHARS_PER_SECOND = 14.0
        const val NOISE = 0.2
        const val HOT_SPELL = 40
        const val HOT_EVERY = 4
        const val HOT_SLOWDOWN = 1.25
        const val MEASURED = 600.0
        const val TOLERANCE = 1e-6
        const val IMPROVEMENT = 4
        val SEEDS = (1..20).toList()
    }
}
