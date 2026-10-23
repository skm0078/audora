// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/studio/StudioProgressTest.kt
// Purpose: Studio progress tells the time left honestly: measured speed, the target or the book's end, whichever first.

package io.github.skm0078.audora.playback.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StudioProgressTest {
    private val hour = 3_600_000L
    private val minute = 60_000L

    @Suppress("LongParameterList") // One per field, each with a default, so a test names only what it varies.
    private fun progress(
        readyMs: Long,
        pagesReadyMs: Long = readyMs,
        page: Int = 0,
        fromPage: Int = 0,
        pageCount: Int = 1_000,
        renderedMs: Long = readyMs,
        renderingMs: Long = 0L,
    ) = StudioProgress(readyMs, pagesReadyMs, page, fromPage, pageCount, renderedMs, renderingMs)

    @Test
    fun `no time left is promised before the speed is measured`() {
        val early = progress(readyMs = minute, renderingMs = 5_000L)
        assertNull(early.timeLeftMs(aheadMs = hour, minMeasureMs = 20_000L))
        assertNull(early.renderFactor(20_000L))
    }

    @Test
    fun `time left is the speech still to make at the measured speed`() {
        // 10 min of speech took 5 min to make: half real time; 50 min to go take 25 min.
        val half = progress(readyMs = 10 * minute, page = 5, renderingMs = 5 * minute)
        assertEquals(0.5, half.renderFactor(20_000L)!!, 1e-9)
        assertEquals(25 * minute, half.timeLeftMs(aheadMs = hour, minMeasureMs = 20_000L))
    }

    @Test
    fun `the book's end comes first when it is nearer than the target`() {
        // 4 pages finished at 2 min each, on page 4 of 10 with 1 min of it done: 6 pages left, 11 min of speech.
        val nearEnd = progress(readyMs = 9 * minute, pagesReadyMs = 8 * minute, page = 4, pageCount = 10)
        assertEquals(11 * minute, nearEnd.speechLeftMs(aheadMs = 6 * hour))
        assertEquals(45, nearEnd.percent(aheadMs = 6 * hour))
    }

    @Test
    fun `before a page is finished only the target counts, and percent moves with every sentence`() {
        val firstSentences = progress(readyMs = 36 * minute / 10, pagesReadyMs = 0L, page = 0)
        assertEquals(6 * hour - 36 * minute / 10, firstSentences.speechLeftMs(aheadMs = 6 * hour))
        assertEquals(1, firstSentences.percent(aheadMs = 6 * hour))
    }

    @Test
    fun `pages found from an earlier run do not count as this run's speed`() {
        val reused = progress(readyMs = hour, page = 30, renderedMs = 0L, renderingMs = 30_000L)
        assertNull(reused.renderFactor(20_000L))
    }
}
