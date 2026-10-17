// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/ocr/OcrTextTest.kt
// Purpose: A page's OCR confidence (RI-9): its words' confidences weighted by length, null when none has one.

package io.github.skm0078.audora.pdf.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OcrTextTest {
    @Test
    fun `the mean confidence weighs long words more and leaves out words without one`() {
        val text = OcrText(
            listOf(
                OcrLine(listOf(OcrWord("reading", null, 90), OcrWord("x", null, 10)), 0),
                OcrLine(listOf(OcrWord("aloud", null, 70), OcrWord("unscored", null)), 1),
            ),
        )
        assertEquals((7 * 90 + 1 * 10 + 5 * 70) / 13, text.meanConfidence)
    }

    @Test
    fun `a page without confidences has none`() {
        assertNull(OcrText.plain("Words from an engine without confidences.").meanConfidence)
        assertNull(OcrText(emptyList()).meanConfidence)
    }
}
