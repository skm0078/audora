// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/ocr/OcrPageTextTest.kt
// Purpose: Tesseract text becomes reading units: paragraphs, line-end hyphens, page numbers, no glyph boxes.

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.pdf.units.ReadingUnitBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrPageTextTest {
    private val builder = ReadingUnitBuilder()

    private fun read(ocrText: String): List<String> = builder.build(builder.ocrReadingText(PAGE, ocrText)).map {
        it.text
    }

    @Test
    fun `lines join into sentences and blank lines separate paragraphs`() {
        val units = read("The quick brown fox\njumps over the dog.\n\nA second paragraph\nends here.\n")
        assertEquals(listOf("The quick brown fox jumps over the dog.", "A second paragraph ends here."), units)
    }

    @Test
    fun `a word broken at the line end is joined, a real hyphen is kept`() {
        assertEquals(listOf("A negative result."), read("A nega-\ntive result."))
        assertEquals(listOf("The well-Known case."), read("The well-\nKnown case."))
    }

    @Test
    fun `lone page numbers at the top or bottom are dropped`() {
        val cases = mapOf(
            "12\n\nBody text here.\n" to listOf("Body text here."),
            "Body text here.\n\n- 3 -\n" to listOf("Body text here."),
            "১২\n\nএটি একটি বাক্য।\n\n(4)" to listOf("এটি একটি বাক্য।"),
            "Body text here.\n\n1867\n\nMore text." to listOf("Body text here.", "1867", "More text."),
        )
        for ((ocr, expected) in cases) assertEquals(ocr, expected, read(ocr))
    }

    @Test
    fun `units have no boxes, index the OCR reading text and carry the page language`() {
        val ocr = "मनुष्य स्वतंत्र पैदा होता है।\n\nसभी समान हैं।"
        val text = builder.ocrReadingText(PAGE, ocr).text
        val units = builder.build(builder.ocrReadingText(PAGE, ocr))
        assertEquals(2, units.size)
        for (unit in units) {
            assertNull(unit.bounds)
            assertEquals(PAGE, unit.page)
            assertEquals(Lang.HI, unit.lang)
            assertEquals(unit.text, text.substring(unit.charStart, unit.charEnd))
        }
    }

    @Test
    fun `empty or blank OCR text gives no units`() {
        assertTrue(read("").isEmpty())
        assertTrue(read(" \n\n \n").isEmpty())
    }

    private companion object {
        const val PAGE = 4
    }
}
