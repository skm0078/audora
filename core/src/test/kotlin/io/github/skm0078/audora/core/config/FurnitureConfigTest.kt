// File: core/src/test/kotlin/io/github/skm0078/audora/core/config/FurnitureConfigTest.kt
// Purpose: FurnitureConfig's defaults are valid and out-of-range tunables are rejected at construction.

package io.github.skm0078.audora.core.config

import org.junit.Assert.assertThrows
import org.junit.Test

class FurnitureConfigTest {
    @Test
    fun `defaults are valid`() {
        FurnitureConfig()
        LayoutConfig()
    }

    @Test
    fun `out-of-range values are rejected`() {
        listOf(
            { FurnitureConfig(captionSizeRatio = 1.2f) },
            { FurnitureConfig(equationMaxWordShare = -0.1f) },
            { FurnitureConfig(equationMaxWordShare = 0.5f, numberedEquationMaxWordShare = 0.4f) },
            { FurnitureConfig(numberedEquationMaxWordShare = 1.1f) },
            { FurnitureConfig(minEquationWordLetters = 0) },
            { FurnitureConfig(equationMinIndentEm = -1f) },
        ).forEach { build -> assertThrows(IllegalArgumentException::class.java) { build() } }
    }
}
