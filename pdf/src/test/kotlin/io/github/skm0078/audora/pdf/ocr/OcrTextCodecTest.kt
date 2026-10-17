// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/ocr/OcrTextCodecTest.kt
// Purpose: Cached OCR pages round-trip with their boxes and turn; anything else reads as a cache miss.

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
                OcrLine(listOf(OcrWord("नमस्ते", ViewRect(0.1f, 0.2f, 0.3f, 0.25f)), OcrWord("x", null)), 0),
                OcrLine(listOf(OcrWord("tab\there", ViewRect(0f, 0f, 1f, 1f))), 2),
            ),
            quarterTurns = 3,
        )
        val back = requireNotNull(OcrTextCodec.decode(OcrTextCodec.encode(text)))
        assertEquals(3, back.quarterTurns)
        assertEquals(listOf(0, 2), back.lines.map { it.paragraph })
        assertEquals(listOf("नमस्ते", "x", "tab here"), back.lines.flatMap { line -> line.words.map { it.text } })
        assertEquals(
            listOf(ViewRect(0.1f, 0.2f, 0.3f, 0.25f), null, ViewRect(0f, 0f, 1f, 1f)),
            back.lines.flatMap { line ->
                line.words.map { it.box }
            },
        )
        assertEquals(text.text.replace('\t', ' '), back.text)
    }

    @Test
    fun `old plain-text pages and damaged files are misses`() {
        assertNull(OcrTextCodec.decode("Some text from an older version.\n\nMore."))
        assertNull(OcrTextCodec.decode("audora-ocr 1 7\n"))
        assertNull(OcrTextCodec.decode("audora-ocr 1 0\nzero\t-\tx\n"))
        assertNull(OcrTextCodec.decode("audora-ocr 1 0\n0\t0.5,0.5,0.1,0.1\tx\n"))
        assertEquals(0, OcrTextCodec.decode("audora-ocr 1 0\n")?.lines?.size)
    }
}
