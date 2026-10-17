// File: core/src/test/kotlin/io/github/skm0078/audora/core/model/SleepOptionsTest.kt
// Purpose: Sleep options (plan item 37): the bedtime window, across midnight too, and their saved line.

package io.github.skm0078.audora.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepOptionsTest {
    @Test
    fun `the bedtime window runs past midnight and leaves its end minute out`() {
        val night = SleepOptions(bedtimeFromMinute = 22 * 60, bedtimeToMinute = 6 * 60)
        assertTrue(night.inBedtime(23 * 60))
        assertTrue(night.inBedtime(0))
        assertTrue(night.inBedtime(5 * 60 + 59))
        assertFalse(night.inBedtime(6 * 60))
        assertFalse(night.inBedtime(12 * 60))
        val afternoon = SleepOptions(bedtimeFromMinute = 13 * 60, bedtimeToMinute = 15 * 60)
        assertTrue(afternoon.inBedtime(14 * 60))
        assertFalse(afternoon.inBedtime(15 * 60))
        assertFalse(SleepOptions(bedtimeFromMinute = 60, bedtimeToMinute = 60).inBedtime(60))
    }

    @Test
    fun `options round-trip and bad values take their defaults`() {
        val options = SleepOptions(shakeToExtend = false, fadeOut = false, bedtime = true, bedtimeMinutes = 45)
        assertEquals(options, SleepOptions.decode(options.encode()))
        assertEquals(SleepOptions(), SleepOptions.decode(null))
        assertEquals(SleepOptions(), SleepOptions.decode("shake=maybe;from=9999;minutes=-3;junk"))
    }
}
