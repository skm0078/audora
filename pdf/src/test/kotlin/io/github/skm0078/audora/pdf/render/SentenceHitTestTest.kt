// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/render/SentenceHitTestTest.kt
// Purpose: A tap on the page picks the sentence whose line box is nearest, within the slop, else nothing.

package io.github.skm0078.audora.pdf.render

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.TextBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SentenceHitTestTest {
    // Sentence 0 fills line 1 and starts line 2; sentence 1 ends line 2; sentence 2 has no boxes (OCR'd text).
    private val first = unit(0, TextBox(72f, 700f, 540f, 688f), TextBox(72f, 680f, 300f, 668f))
    private val second = unit(1, TextBox(305f, 680f, 540f, 668f))
    private val ocr = unit(2, bounds = null)
    private val units = listOf(first, second, ocr)

    @Test
    fun `table of taps`() {
        data class Tap(val name: String, val x: Float, val y: Float, val expected: ReadingUnit?)
        listOf(
            Tap("on the first line", 100f, 694f, first),
            Tap("on the second sentence's part of line 2", 400f, 674f, second),
            Tap("on line 2 left of the sentence break", 200f, 674f, first),
            Tap("between the lines, nearer line 2", 400f, 682f, second),
            Tap("just right of the text", 545f, 694f, first),
            Tap("in the margin, beyond the slop", 20f, 694f, null),
            Tap("far below the text", 300f, 100f, null),
        ).forEach { tap ->
            assertEquals(tap.name, tap.expected, SentenceHitTest.unitAt(units, PagePoint(tap.x, tap.y), SLOP))
        }
    }

    @Test
    fun `no units, or none with boxes, picks nothing`() {
        assertNull(SentenceHitTest.unitAt(emptyList(), PagePoint(100f, 694f), SLOP))
        assertNull(SentenceHitTest.unitAt(listOf(ocr), PagePoint(100f, 694f), SLOP))
    }

    private fun unit(index: Int, vararg boxes: TextBox, bounds: List<TextBox>? = boxes.toList()) =
        ReadingUnit(0, index, index * 50, index * 50 + 40, "Sentence $index.", Lang.EN, 0, bounds)

    private companion object {
        const val SLOP = 8f
    }
}
