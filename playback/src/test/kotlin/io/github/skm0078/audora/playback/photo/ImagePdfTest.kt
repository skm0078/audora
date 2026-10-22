// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/photo/ImagePdfTest.kt
// Purpose: The photo PDF is well formed: header, objects at the offsets the xref names, page size and image stream.

package io.github.skm0078.audora.playback.photo

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ImagePdfTest {
    private val jpeg = byteArrayOf(-1, -40, -1, -32, 1, 2, 3, 0, -1, -39)

    @Test
    fun `starts as a PDF and ends with EOF`() {
        val pdf = text(ImagePdf.write(jpeg, 300, 400, 842))
        assertTrue(pdf.startsWith("%PDF-1.4\n"))
        assertTrue(pdf.endsWith("%%EOF\n"))
    }

    @Test
    fun `every object sits at the offset the xref names`() {
        val bytes = ImagePdf.write(jpeg, 300, 400, 842)
        val pdf = text(bytes)
        val startXref = pdf.substringAfterLast("startxref\n").substringBefore('\n').toInt()
        assertTrue(pdf.startsWith("xref\n", startXref))
        val entries = pdf.substring(startXref).lines().drop(3).take(5)
        entries.forEachIndexed { index, line ->
            val offset = line.substringBefore(' ').toInt()
            assertTrue("object ${index + 1} at $offset", pdf.startsWith("${index + 1} 0 obj", offset))
        }
    }

    @Test
    fun `page keeps the photo's proportions with the long side given`() {
        val portrait = text(ImagePdf.write(jpeg, 3000, 4000, 842))
        assertTrue(portrait.contains("/MediaBox [0 0 632 842]"))
        val landscape = text(ImagePdf.write(jpeg, 4000, 3000, 842))
        assertTrue(landscape.contains("/MediaBox [0 0 842 632]"))
    }

    @Test
    fun `the JPEG is stored unchanged as a DCT stream`() {
        val bytes = ImagePdf.write(jpeg, 300, 400, 842)
        val pdf = text(bytes)
        assertTrue(pdf.contains("/Filter /DCTDecode /Length ${jpeg.size}"))
        val start = pdf.indexOf("stream\n", pdf.indexOf("/DCTDecode")) + "stream\n".length
        assertArrayEquals(jpeg, bytes.copyOfRange(start, start + jpeg.size))
        assertEquals(1, Regex("/Type /Page ").findAll(pdf).count())
    }

    @Test
    fun `an empty image size is refused`() {
        assertThrows(IllegalArgumentException::class.java) { ImagePdf.write(jpeg, 0, 10, 842) }
    }

    // ISO-8859-1 keeps one char per byte, so string offsets are byte offsets.
    private fun text(bytes: ByteArray) = String(bytes, Charsets.ISO_8859_1)
}
