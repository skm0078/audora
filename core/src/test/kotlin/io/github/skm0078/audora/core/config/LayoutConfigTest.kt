// File: core/src/test/kotlin/io/github/skm0078/audora/core/config/LayoutConfigTest.kt
// Purpose: LayoutConfig rejects heading thresholds that cannot tell a heading from body text.

package io.github.skm0078.audora.core.config

import org.junit.Assert.assertThrows
import org.junit.Test

class LayoutConfigTest {
    @Test
    fun `defaults are valid`() {
        LayoutConfig()
    }

    @Test
    fun `out of range heading thresholds are rejected`() {
        val bad = listOf<() -> LayoutConfig>(
            { LayoutConfig(headingSizeRatio = 1f) },
            { LayoutConfig(ocrHeadingSizeRatio = 0.9f) },
            { LayoutConfig(ocrHeadingMinConfidence = -1) },
            { LayoutConfig(ocrHeadingMinConfidence = 101) },
            { LayoutConfig(maxHeadingLines = 0) },
            { LayoutConfig(ocrBulletMaxGapEm = 0f) },
            { LayoutConfig(ocrDandaMinIndicWords = 0) },
        )
        for (make in bad) assertThrows(IllegalArgumentException::class.java) { make() }
    }
}
