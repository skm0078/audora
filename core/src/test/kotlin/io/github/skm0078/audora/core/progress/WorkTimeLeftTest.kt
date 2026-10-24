// File: core/src/test/kotlin/io/github/skm0078/audora/core/progress/WorkTimeLeftTest.kt
// Purpose: A long job's time left: measured from where it was first seen, shown only once measured.

package io.github.skm0078.audora.core.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkTimeLeftTest {
    private val estimate = WorkTimeLeft(minMeasureMs = 4_000)

    @Test
    fun `nothing is claimed until the speed has been measured for long enough`() {
        assertNull(estimate.update(nowMs = 0, done = 0, total = 100))
        assertNull(estimate.update(nowMs = 3_999, done = 10, total = 100))
        assertEquals(36_000L, estimate.update(nowMs = 4_000, done = 10, total = 100))
    }

    @Test
    fun `a resumed job is measured from where it was first seen, not from zero`() {
        estimate.update(nowMs = 1_000, done = 60, total = 100)

        // 20 in 10 s: the 20 left take 10 s more.
        assertEquals(10_000L, estimate.update(nowMs = 11_000, done = 80, total = 100))
    }

    @Test
    fun `a stall shows as a growing time, and no work at all claims nothing`() {
        estimate.update(nowMs = 0, done = 0, total = 100)
        assertEquals(10_000L, estimate.update(nowMs = 10_000, done = 50, total = 100))
        assertEquals(20_000L, estimate.update(nowMs = 20_000, done = 50, total = 100))

        val idle = WorkTimeLeft(minMeasureMs = 4_000)
        idle.update(nowMs = 0, done = 5, total = 100)
        assertNull(idle.update(nowMs = 60_000, done = 5, total = 100))
    }

    @Test
    fun `an unknown total, or work going backwards, starts the measure again`() {
        assertNull(estimate.update(nowMs = 0, done = 0, total = 0))
        estimate.update(nowMs = 0, done = 50, total = 100)
        assertNull(estimate.update(nowMs = 10_000, done = 10, total = 100))
        assertEquals(8_000L, estimate.update(nowMs = 20_000, done = 60, total = 100))
    }

    @Test
    fun `finished work has no time left, and reset forgets the measure`() {
        estimate.update(nowMs = 0, done = 0, total = 100)
        assertEquals(0L, estimate.update(nowMs = 5_000, done = 100, total = 100))

        estimate.reset()
        assertNull(estimate.update(nowMs = 6_000, done = 100, total = 100))
    }
}
