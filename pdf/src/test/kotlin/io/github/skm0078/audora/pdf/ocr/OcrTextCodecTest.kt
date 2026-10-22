// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/ocr/OcrTextCodecTest.kt
// Purpose: Cached OCR pages round-trip with their boxes, rules and turn; anything else reads as a cache miss.

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.pdf.render.ViewRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OcrTextCodecTest {
    @Test
    fun `a page round-trips with words, boxes, paragraphs and its turn`() {
        val text = OcrText(
            listOf(
                OcrLine(listOf(OcrWord("नमस्ते", ViewRect(0.1f, 0.2f, 0.3f, 0.25f), 91), OcrWord("x", null, 0)), 0),
                OcrLine(listOf(OcrWord("tab\there", ViewRect(0f, 0f, 1f, 1f))), 2),
            ),
            quarterTurns = 3,
            rules = listOf(ViewRect(0.1f, 0.5f, 0.9f, 0.502f), ViewRect(0.3f, 0.1f, 0.301f, 0.6f)),
        )
        val back = requireNotNull(OcrTextCodec.decode(OcrTextCodec.encode(text)))
        assertEquals(3, back.quarterTurns)
        assertEquals(text.rules, back.rules)
        assertEquals(listOf(0, 2), back.lines.map { it.paragraph })
        assertEquals(listOf("नमस्ते", "x", "tab here"), back.lines.flatMap { line -> line.words.map { it.text } })
        assertEquals(
            listOf(ViewRect(0.1f, 0.2f, 0.3f, 0.25f), null, ViewRect(0f, 0f, 1f, 1f)),
            back.lines.flatMap { line ->
                line.words.map { it.box }
            },
        )
        assertEquals(listOf(91, 0, null), back.lines.flatMap { line -> line.words.map { it.confidence } })
        assertEquals(text.text.replace('\t', ' '), back.text)
    }

    @Test
    fun `a page without rules writes no rules row`() {
        val text = OcrText(listOf(OcrLine(listOf(OcrWord("a", null)), 0)))
        val encoded = OcrTextCodec.encode(text)
        assertEquals("audora-ocr 3 0\n0\t-\ta\n", encoded)
        assertEquals(emptyList<ViewRect>(), requireNotNull(OcrTextCodec.decode(encoded)).rules)
    }

    @Test
    fun `version 2 pages from before rules still decode`() {
        val back = requireNotNull(OcrTextCodec.decode("audora-ocr 2 1\n0\t0.1,0.2,0.3,0.4@88\tword\n"))
        assertEquals(1, back.quarterTurns)
        assertEquals(listOf(88), back.lines.single().words.map { it.confidence })
        assertEquals(emptyList<ViewRect>(), back.rules)
    }

    @Test
    fun `version 1 pages from before confidences still decode`() {
        val back = requireNotNull(OcrTextCodec.decode("audora-ocr 1 0\n0\t0.1,0.2,0.3,0.4\tword\t-\tx\n"))
        assertEquals(listOf("word", "x"), back.lines.single().words.map { it.text })
        assertEquals(listOf(null, null), back.lines.single().words.map { it.confidence })
    }

    @Test
    fun `old plain-text pages and damaged files are misses`() {
        assertNull(OcrTextCodec.decode("Some text from an older version.\n\nMore."))
        assertNull(OcrTextCodec.decode("audora-ocr 1 7\n"))
        assertNull(OcrTextCodec.decode("audora-ocr 1 0\nzero\t-\tx\n"))
        assertNull(OcrTextCodec.decode("audora-ocr 1 0\n0\t0.5,0.5,0.1,0.1\tx\n"))
        assertNull(OcrTextCodec.decode("audora-ocr 2 0\n0\t-@101\tx\n"))
        assertNull(OcrTextCodec.decode("audora-ocr 2 0\n0\t-@high\tx\n"))
        assertNull(OcrTextCodec.decode("audora-ocr 4 0\n"))
        assertNull(OcrTextCodec.decode("audora-ocr 3 0\nR\t0.5,0.5,0.1,0.1\n"))
        assertNull(OcrTextCodec.decode("audora-ocr 3 0\nR\tleft\n"))
        assertEquals(0, OcrTextCodec.decode("audora-ocr 1 0\n")?.lines?.size)
    }
}
