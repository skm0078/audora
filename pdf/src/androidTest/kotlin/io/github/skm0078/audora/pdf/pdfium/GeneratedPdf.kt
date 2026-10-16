// File: pdf/src/androidTest/kotlin/io/github/skm0078/audora/pdf/pdfium/GeneratedPdf.kt
// Purpose: Writes a long plain-text PDF at test time, so the 500-page memory test commits no large file.

package io.github.skm0078.audora.pdf.pdfium

import java.io.File

/**
 * A minimal PDF 1.4 writer: one Helvetica font, one content stream per page, a correct xref table.
 * Every page has its own content stream and its own sentence numbers, so PDFium parses and caches distinct
 * objects per page as it would in a real book.
 */
object GeneratedPdf {
    /** [rotate] is the pages' `/Rotate` (0, 90, 180 or 270), for the page renderer's test (P4-03). */
    fun write(file: File, pages: Int, linesPerPage: Int, rotate: Int = 0) {
        val objects = ArrayList<String>()
        objects += "<< /Type /Catalog /Pages 2 0 R >>"
        val kids = (0 until pages).joinToString(" ") { "${FIRST_PAGE_OBJECT + it * 2} 0 R" }
        objects += "<< /Type /Pages /Kids [$kids] /Count $pages >>"
        objects += "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"
        for (page in 0 until pages) {
            val contentObject = FIRST_PAGE_OBJECT + page * 2 + 1
            objects += "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Rotate $rotate " +
                "/Resources << /Font << /F1 3 0 R >> >> /Contents $contentObject 0 R >>"
            val content = contentStream(page, linesPerPage)
            objects += "<< /Length ${content.length} >>\nstream\n$content\nendstream"
        }
        file.outputStream().buffered().use { out ->
            var offset = 0L
            val offsets = LongArray(objects.size)
            fun emit(s: String) {
                val bytes = s.toByteArray(Charsets.ISO_8859_1)
                out.write(bytes)
                offset += bytes.size
            }
            emit("%PDF-1.4\n")
            objects.forEachIndexed { i, body ->
                offsets[i] = offset
                emit("${i + 1} 0 obj\n$body\nendobj\n")
            }
            val xref = offset
            emit("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
            offsets.forEach { emit("%010d 00000 n \n".format(it)) }
            emit("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        }
    }

    /** The text of [page]'s first line, as extraction should return it. */
    fun firstSentence(page: Int) = "Page ${page + 1}, sentence 1 of a generated book about nothing in particular."

    private fun contentStream(page: Int, lines: Int): String = buildString {
        append("BT /F1 11 Tf 72 740 Td 14 TL\n")
        for (line in 1..lines) {
            append('(')
            append("Page ${page + 1}, sentence $line of a generated book about nothing in particular.")
            append(") Tj T*\n")
        }
        append("ET")
    }

    private const val FIRST_PAGE_OBJECT = 4
}
