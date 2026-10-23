// File: core/src/test/kotlin/io/github/skm0078/audora/core/config/OcrThresholdTest.kt
// Purpose: OCR binarization defaults to Sauvola and maps to Tesseract's thresholding_method values.

package io.github.skm0078.audora.core.config

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrThresholdTest {
    @Test
    fun `each method is Tesseract's thresholding_method value`() {
        val cases = listOf(OcrThreshold.OTSU to 0, OcrThreshold.LEPTONICA_OTSU to 1, OcrThreshold.SAUVOLA to 2)
        for ((method, value) in cases) assertEquals(method.name, value, method.tesseractValue)
    }

    @Test
    fun `pages are binarized with Sauvola by default`() {
        assertEquals(OcrThreshold.SAUVOLA, OcrConfig().threshold)
    }
}
