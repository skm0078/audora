// File: core/src/test/kotlin/io/github/skm0078/audora/core/config/LightingConfigTest.kt
// Purpose: LightingConfig rejects limits and sizes that cannot even out a page's light.

package io.github.skm0078.audora.core.config

import org.junit.Assert.assertThrows
import org.junit.Test

class LightingConfigTest {
    @Test
    fun `defaults are valid, inside OcrConfig too`() {
        LightingConfig()
        OcrConfig(lighting = LightingConfig(enabled = false))
    }

    @Test
    fun `out of range values are rejected`() {
        val bad = listOf<() -> LightingConfig>(
            { LightingConfig(readAgainBelow = -1) },
            { LightingConfig(readAgainBelow = 101) },
            { LightingConfig(unevenBelow = 0f) },
            { LightingConfig(unevenBelow = 1.1f) },
            { LightingConfig(minGain = -1) },
            { LightingConfig(minWordShare = -0.1f) },
            { LightingConfig(minWordShare = 1.5f) },
            { LightingConfig(blockInches = 0f) },
            { LightingConfig(widenBlocks = -1) },
            { LightingConfig(smoothBlocks = -1) },
        )
        for (make in bad) assertThrows(IllegalArgumentException::class.java) { make() }
    }
}
