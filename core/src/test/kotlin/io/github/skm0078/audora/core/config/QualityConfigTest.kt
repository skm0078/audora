// File: core/src/test/kotlin/io/github/skm0078/audora/core/config/QualityConfigTest.kt
// Purpose: QualityConfig's defaults are valid and out-of-range tunables are rejected at construction.

package io.github.skm0078.audora.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class QualityConfigTest {
    @Test
    fun `defaults follow ADR-011`() {
        val config = QualityConfig()
        assertEquals(0.05f, config.maxSampleCer)
        assertEquals(0.02f, config.maxUnicodeMapErrorRate)
        assertEquals(50, config.recheckEveryPages)
    }

    @Test
    fun `out-of-range values are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { QualityConfig(maxSampleCer = 1.5f) }
        assertThrows(IllegalArgumentException::class.java) { QualityConfig(minWordInitialShare = -0.1f) }
        assertThrows(IllegalArgumentException::class.java) { QualityConfig(minVisualOrderSigns = 0) }
        assertThrows(IllegalArgumentException::class.java) { QualityConfig(recheckEveryPages = 0) }
    }
}
