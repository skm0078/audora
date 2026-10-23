// File: core/src/test/kotlin/io/github/skm0078/audora/core/config/RuleConfigTest.kt
// Purpose: RuleConfig rejects thresholds and sizes that cannot find a scanned page's rules.

package io.github.skm0078.audora.core.config

import org.junit.Assert.assertThrows
import org.junit.Test

class RuleConfigTest {
    @Test
    fun `defaults are valid, inside OcrConfig too`() {
        RuleConfig()
        OcrConfig(rules = RuleConfig(enabled = false))
    }

    @Test
    fun `out of range values are rejected`() {
        val bad = listOf<() -> RuleConfig>(
            { RuleConfig(maxDarkBelow = 0) },
            { RuleConfig(maxDarkBelow = 256) },
            { RuleConfig(smearEm = -0.1f) },
            { RuleConfig(gapEm = -1f) },
            { RuleConfig(maxThicknessEm = 0f) },
            { RuleConfig(minInkShare = 0f) },
            { RuleConfig(minInkShare = 1.5f) },
            { RuleConfig(minHorizontalEm = 0f) },
            { RuleConfig(minVerticalEm = -2f) },
        )
        for (make in bad) assertThrows(IllegalArgumentException::class.java) { make() }
    }
}
