// File: core/src/test/kotlin/io/github/skm0078/audora/core/config/SpeakabilityConfigTest.kt
// Purpose: SpeakabilityConfig's defaults are valid and out-of-range tunables are rejected at construction.

package io.github.skm0078.audora.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SpeakabilityConfigTest {
    @Test
    fun `default weights add up to one`() {
        val config = SpeakabilityConfig()
        val sum = config.ocrConfidenceWeight + config.dictionaryWeight + config.letterModelWeight +
            config.symbolSoupWeight + config.scriptMixWeight
        assertEquals(1f, sum, 1e-6f)
    }

    @Test
    fun `out-of-range values are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { SpeakabilityConfig(skipBelow = 1.1f) }
        assertThrows(IllegalArgumentException::class.java) { SpeakabilityConfig(dictionaryWeight = -0.1f) }
        assertThrows(IllegalArgumentException::class.java) { SpeakabilityConfig(minDictionaryWords = 0) }
        assertThrows(IllegalArgumentException::class.java) {
            SpeakabilityConfig(0.5f, 0f, 0f, 0f, 0f, 0f)
        }
    }
}
