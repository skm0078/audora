// File: core/src/test/kotlin/io/github/skm0078/audora/core/config/OcrRepairConfigTest.kt
// Purpose: RI-3's OCR repair and line-retry tunables have valid defaults and reject out-of-range values.

package io.github.skm0078.audora.core.config

import org.junit.Assert.assertThrows
import org.junit.Test

class OcrRepairConfigTest {
    @Test
    fun `defaults are valid`() {
        OcrRepairConfig()
        OcrConfig()
    }

    @Test
    fun `out-of-range values are rejected`() {
        listOf(
            { OcrRepairConfig(maxConfidence = 101) },
            { OcrRepairConfig(minLetters = 0) },
            { OcrRepairConfig(minZipf = -1) },
            { OcrRepairConfig(zipfMargin = -1) },
            { OcrRepairConfig(confusionCost = 0f) },
            { OcrRepairConfig(confusionCost = 1.5f) },
            { OcrConfig(retryLineBelow = 101) },
            { OcrConfig(retryMinGain = -1) },
            { OcrConfig(retryMaxLines = -1) },
            { OcrConfig(retryBudgetMs = -1L) },
        ).forEach { build -> assertThrows(IllegalArgumentException::class.java) { build() } }
    }
}
