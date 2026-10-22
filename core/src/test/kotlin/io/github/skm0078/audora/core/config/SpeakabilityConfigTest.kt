// File: core/src/test/kotlin/io/github/skm0078/audora/core/config/SpeakabilityConfigTest.kt
// Purpose: SpeakabilityConfig's defaults are valid and out-of-range tunables are rejected at construction.

package io.github.skm0078.audora.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SpeakabilityConfigTest {
    @Test
    fun `default weights add up to one`() {
        assertEquals(1f, SpeakabilityConfig().signals.sumOf { it.weight.toDouble() }.toFloat(), 1e-6f)
    }

    @Test
    fun `a range maps junk to zero, clean to one and linearly between`() {
        val range = SignalRange(junk = 0.2f, clean = 0.6f)
        listOf(0f to 0f, 0.2f to 0f, 0.4f to 0.5f, 0.6f to 1f, 1f to 1f).forEach { (raw, mapped) ->
            assertEquals("raw $raw", mapped, range.map(raw), 1e-6f)
        }
    }

    @Test
    fun `out-of-range values are rejected`() {
        val off = Signal(0f, SignalRange(0f, 1f))
        listOf(
            { SpeakabilityConfig(skipBelow = 1.1f) },
            { SpeakabilityConfig(minDictionaryWords = 0) },
            { Signal(weight = -0.1f, range = SignalRange(0f, 1f)) },
            { SignalRange(junk = 0.5f, clean = 0.5f) },
            { SignalRange(junk = -0.1f, clean = 0.5f) },
            {
                SpeakabilityConfig(
                    ocrConfidence = off,
                    dictionary = off,
                    letterModel = off,
                    symbolSoup = off,
                    scriptMix = off,
                )
            },
        ).forEach { build -> assertThrows(IllegalArgumentException::class.java) { build() } }
    }
}
