// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/ocr/OcrPageTextTest.kt
// Purpose: OCR'd words become reading units: paragraphs, line-end hyphens, page numbers and highlight boxes.

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.core.config.SpeakabilityConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.pdf.render.PageTransform
import io.github.skm0078.audora.pdf.render.ViewRect
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
    fun `text without positions gives boxless units that index the OCR text and carry the page language`() {
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
    fun `word confidences reach the units as a speakability score`() {
        fun line(paragraph: Int, confidence: Int, vararg words: String) =
            OcrLine(words.map { OcrWord(it, null, confidence) }, paragraph)
        val ocr = OcrText(
            listOf(
                line(0, 95, "The", "old", "man", "lived", "near", "the", "river."),
                line(1, 15, "Tbe", "olb", "rnan", "livcd", "ncar", "tbe", "rivcr."),
            ),
        )
        val reading = builder.ocrReadingText(PAGE, ocr)
        val units = builder.build(reading)
        assertEquals(0.95f, reading.ocrConfidence(units[0].charStart, units[0].charEnd)!!, 1e-6f)
        assertEquals(0.15f, reading.ocrConfidence(units[1].charStart, units[1].charEnd)!!, 1e-6f)
        val skipBelow = SpeakabilityConfig().skipBelow
        assertTrue(units[0].speakScore!! >= skipBelow)
        assertTrue(units[1].speakScore!! < skipBelow)
        // Text without confidences (plain OCR text, a text layer) has none.
        val plain = builder.ocrReadingText(PAGE, "No confidence here.")
        assertNull(plain.ocrConfidence(0, plain.text.length))
    }

    @Test
    fun `empty or blank OCR text gives no units`() {
        assertTrue(read("").isEmpty())
        assertTrue(read(" \n\n \n").isEmpty())
    }

    @Test
    fun `words with boxes give each unit one box per line on the page, and dropped lines are left out`() {
        fun word(text: String, left: Float, top: Float) = OcrWord(text, ViewRect(left, top, left + 0.1f, top + 0.02f))
        val ocr = OcrText(
            listOf(
                OcrLine(listOf(word("Running", 0.1f, 0.02f), word("head", 0.3f, 0.02f)), paragraph = 0),
                OcrLine(listOf(word("One", 0.1f, 0.3f), word("line", 0.3f, 0.3f)), paragraph = 1),
                OcrLine(listOf(word("and", 0.1f, 0.33f), word("two.", 0.3f, 0.33f)), paragraph = 1),
            ),
        )
        val transform = PageTransform.upright(PAGE_WIDTH, PAGE_HEIGHT)
        val reading = builder.ocrReadingText(PAGE, ocr, dropped = setOf(0)) { transform.toPage(it) }
        val units = builder.build(reading)
        assertEquals(listOf("One line and two."), units.map { it.text })
        val boxes = requireNotNull(units.single().bounds)
        assertEquals(2, boxes.size)
        val first = transform.toView(boxes[0])
        assertEquals(0.1f, first.left, EPSILON)
        assertEquals(0.4f, first.right, EPSILON)
        assertEquals(0.3f, first.top, EPSILON)
        assertEquals(0.32f, first.bottom, EPSILON)
    }

    private companion object {
        const val PAGE = 4
        const val PAGE_WIDTH = 600f
        const val PAGE_HEIGHT = 800f
        const val EPSILON = 1e-4f
    }
}
