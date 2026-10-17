// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/ocr/HocrTest.kt
// Purpose: Tesseract's hOCR becomes words with boxes and confidences, lines and paragraphs; boxes turn back correctly.

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.pdf.render.ViewRect
import org.junit.Assert.assertEquals
import org.junit.Test

class HocrTest {
    private val hocr = """
        <div class='ocr_page' id='page_1' title='image "x"; bbox 0 0 200 100; ppageno 0'>
         <div class='ocr_carea' id='block_1_1' title="bbox 10 10 190 90">
          <p class='ocr_par' id='par_1_1' lang='eng' title="bbox 10 10 190 30">
           <span class='ocr_header' id='line_1_1' title="bbox 10 10 190 20; baseline 0 0; x_size 10">
            <span class='ocrx_word' id='word_1_1' title='bbox 10 10 50 20; x_wconf 96'>Tom &amp; Jerry</span>
            <span class='ocrx_word' id='word_1_2' title='bbox 60 10 90 20; x_wconf 40'><strong>&lt;b&gt;</strong></span>
           </span>
          </p>
          <p class='ocr_par' id='par_1_2' lang='hin' title="bbox 10 40 190 90">
           <span class='ocr_line' id='line_1_2' title="bbox 10 40 190 50">
            <span class='ocrx_word' id='word_1_3' title='bbox 10 40 60 50; x_wconf 90'>नमस्ते</span>
            <span class='ocrx_word' id='word_1_4' title='bbox 70 40 80 50; x_wconf 90'> </span>
           </span>
           <span class='ocr_line' id='line_1_3' title="bbox 10 60 190 70">
            <span class='ocrx_word' id='word_1_5' title='bbox 10 60 60 70; x_wconf 80'>दुनिया</span>
           </span>
          </p>
         </div>
        </div>
    """.trimIndent()

    @Test
    fun `words, lines, paragraphs and confidences are read`() {
        val page = Hocr.parse(hocr, 200, 100)
        assertEquals(
            listOf(listOf("Tom & Jerry", "<b>"), listOf("नमस्ते"), listOf("दुनिया")),
            page.lines.map { line ->
                line.words.map { it.text }
            },
        )
        assertEquals(listOf(0, 1, 1), page.lines.map { it.paragraph })
        val first = page.lines[0].words[0]
        assertEquals(listOf(10, 10, 50, 20, 96), listOf(first.left, first.top, first.right, first.bottom, first.conf))
        // Weighted by length: 11·96 + 3·40 + 6·90 + 6·80 over 26 letters.
        assertEquals((11 * 96 + 3 * 40 + 6 * 90 + 6 * 80) / 26, page.meanConfidence)
    }

    @Test
    fun `boxes become fractions of the page as displayed, turned back by the image's turn`() {
        val page = RecognizedPage(listOf(RecognizedLine(listOf(RecognizedWord("x", 0, 0, 50, 10, 90)), 0)), 200, 100)
        assertEquals(ViewRect(0f, 0f, 0.25f, 0.1f), page.toOcrText(0).lines[0].words[0].box)
        // The image read upright was the page turned a quarter clockwise: its top-left is the page's bottom-left.
        val turned = page.toOcrText(1)
        assertEquals(1, turned.quarterTurns)
        val box = requireNotNull(turned.lines[0].words[0].box)
        assertRect(ViewRect(0f, 0.75f, 0.1f, 1f), box)
        assertRect(ViewRect(0f, 0f, 0.25f, 0.1f), turned.readingFrame(box))
    }

    @Test
    fun `empty or broken hOCR gives no lines`() {
        assertEquals(0, Hocr.parse("", 10, 10).lines.size)
        assertEquals(0, Hocr.parse("<span class='ocrx_word' title='no box'>x</span>", 10, 10).lines.size)
        assertEquals(0, Hocr.parse("", 10, 10).meanConfidence)
    }

    private fun assertRect(expected: ViewRect, actual: ViewRect) {
        val pairs = listOf(
            expected.left to actual.left,
            expected.top to actual.top,
            expected.right to actual.right,
            expected.bottom to actual.bottom,
        )
        pairs.forEach { (e, a) -> assertEquals("$actual", e, a, EPSILON) }
    }

    private companion object {
        const val EPSILON = 1e-5f
    }
}
