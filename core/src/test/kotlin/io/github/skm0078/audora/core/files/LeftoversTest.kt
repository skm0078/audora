// File: core/src/test/kotlin/io/github/skm0078/audora/core/files/LeftoversTest.kt
// Purpose: When an unfinished job's leftovers may be deleted, and how clean-ups add up.

package io.github.skm0078.audora.core.files

import io.github.skm0078.audora.core.config.LeftoverConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LeftoversTest {
    private val config = LeftoverConfig(keepMs = 1_000L)
    private val policy = LeftoverPolicy(config, nowMs = 10_000L)

    @Test
    fun `a leftover is kept until it is keepMs old, then it may go`() {
        assertFalse(policy.mayDelete(touchedAtMs = 9_500L))
        assertFalse(policy.mayDelete(touchedAtMs = 9_001L))
        assertTrue(policy.mayDelete(touchedAtMs = 9_000L))
        assertTrue(policy.mayDelete(touchedAtMs = 0L))
    }

    @Test
    fun `a book that is gone or the listener asking deletes it at once`() {
        assertTrue(policy.mayDelete(touchedAtMs = 10_000L, orphan = true))
        assertTrue(policy.mayDelete(touchedAtMs = 10_000L, asked = true))
    }

    @Test
    fun `a clock moved back never makes fresh work look old`() {
        assertFalse(policy.mayDelete(touchedAtMs = 50_000L))
    }

    @Test
    fun `tallies add up by kind and bytes, and an empty one is NONE`() {
        val a = LeftoverTally.of(LeftoverKind.DOWNLOAD, jobs = 2, bytes = 100L)
        val b = LeftoverTally.of(LeftoverKind.AUDIOBOOK, jobs = 1, bytes = 50L)
        val sum = a + b + LeftoverTally.of(LeftoverKind.DOWNLOAD, jobs = 1, bytes = 5L)
        assertEquals(3, sum.count(LeftoverKind.DOWNLOAD))
        assertEquals(1, sum.count(LeftoverKind.AUDIOBOOK))
        assertEquals(0, sum.count(LeftoverKind.TALKING_BOOK))
        assertEquals(155L, sum.bytes)
        assertEquals(LeftoverTally.NONE, LeftoverTally.of(LeftoverKind.DOWNLOAD, jobs = 0, bytes = 0L))
        assertTrue(LeftoverTally.NONE.isEmpty)
        assertFalse(sum.isEmpty)
    }

    @Test
    fun `the keep time is whole days for the wording, and never zero or negative`() {
        assertEquals(7, LeftoverConfig().keepDays)
        assertEquals(1, LeftoverConfig(keepMs = 1L).keepDays)
        assertThrows(IllegalArgumentException::class.java) { LeftoverConfig(keepMs = 0L) }
        assertThrows(IllegalArgumentException::class.java) { LeftoverConfig(startDelayMs = -1L) }
    }
}
