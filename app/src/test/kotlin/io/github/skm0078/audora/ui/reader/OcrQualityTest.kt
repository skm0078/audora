// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/OcrQualityTest.kt
// Purpose: The "OCR" badge's text per page route and OCR confidence band (RI-9).

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.ReaderConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class OcrQualityTest {
    private val config = ReaderConfig()

    @Test
    fun `badge text per route and confidence band`() {
        val cases = listOf(
            Triple(PageOrigin.TEXT, 95, null),
            Triple(PageOrigin.NEEDS_OCR_MODELS, null, null),
            Triple(PageOrigin.OCR_FAILED, null, null),
            Triple(PageOrigin.OCR, null, R.string.reader_ocr_badge_body),
            Triple(PageOrigin.OCR, 100, R.string.reader_ocr_quality_good),
            Triple(PageOrigin.OCR, config.ocrGoodFrom, R.string.reader_ocr_quality_good),
            Triple(PageOrigin.OCR, config.ocrGoodFrom - 1, R.string.reader_ocr_quality_fair),
            Triple(PageOrigin.OCR, config.ocrFairFrom, R.string.reader_ocr_quality_fair),
            Triple(PageOrigin.OCR, config.ocrFairFrom - 1, R.string.reader_ocr_quality_poor),
            Triple(PageOrigin.OCR, 0, R.string.reader_ocr_quality_poor),
        )
        cases.forEach { (origin, confidence, expected) ->
            assertEquals("$origin $confidence", expected, ocrBadgeText(origin, confidence, config))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `bands out of order are refused`() {
        ReaderConfig(ocrGoodFrom = 60, ocrFairFrom = 80)
    }
}
