// File: spikes/playback/src/test/kotlin/io/github/skm0078/audora/spike/playback/config/SpikeConfigTest.kt
// Purpose: Bad settings are reported before a run starts, and every preset is valid.

package io.github.skm0078.audora.spike.playback.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpikeConfigTest {
    @Test
    fun `every preset is runnable`() {
        SpikePreset.entries.forEach { preset ->
            assertTrue("${preset.name}: ${preset.config.problems()}", preset.config.problems().isEmpty())
        }
    }

    @Test
    fun `out-of-range values are all reported`() {
        val problems = SpikeConfig(realTimeFactor = 0.0, prefetchUnits = 0, minUnitMs = 10, maxUnitMs = 5).problems()

        assertEquals(3, problems.size)
    }
}
