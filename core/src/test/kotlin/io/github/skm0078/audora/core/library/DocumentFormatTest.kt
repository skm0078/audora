// File: core/src/test/kotlin/io/github/skm0078/audora/core/library/DocumentFormatTest.kt
// Purpose: PDF, EPUB, Word, OpenDocument, RTF, HTML and text books are told apart by their first bytes.

package io.github.skm0078.audora.core.library

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentFormatTest {
    private fun sniff(bytes: ByteArray) = DocumentFormats.sniff(bytes, pdfWindowBytes = 16)

    private fun zip(firstEntry: String, content: String): ByteArray =
        byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(26) + firstEntry.toByteArray() + content.toByteArray()

    @Test
    fun `recognizes each format`() {
        val cases = listOf(
            "%PDF-1.7\n".toByteArray() to DocumentFormat.PDF,
            "junk\n%PDF-1.4".toByteArray() to DocumentFormat.PDF,
            zip("mimetype", "application/epub+zip") to DocumentFormat.EPUB,
            "Chapter 1\n\nIt was a dark night.\r\n".toByteArray() to DocumentFormat.TEXT,
            "मन भय से मुक्त हो।\n".toByteArray() to DocumentFormat.TEXT,
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte(), 0x41) to DocumentFormat.TEXT,
            byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x41, 0x00) to DocumentFormat.TEXT,
            "café \u0093quoted\u0094".toByteArray(Charsets.ISO_8859_1) to DocumentFormat.TEXT,
            zip("[Content_Types].xml", "\u0000\u0013compressed") to DocumentFormat.DOCX,
            zip("word/document.xml", "<w:document>") to DocumentFormat.DOCX,
            zip("mimetype", "application/vnd.oasis.opendocument.text") to DocumentFormat.ODT,
            "{\\rtf1\\ansi\\ansicpg1252 Hello}".toByteArray() to DocumentFormat.RTF,
            "<!DOCTYPE html>\n<html><body>Hi".toByteArray() to DocumentFormat.HTML,
            "\uFEFF<?xml version=\"1.0\"?>\n<!-- saved -->\n<html lang=\"hi\">".toByteArray() to DocumentFormat.HTML,
            "# Notes\n\nSome <b>bold</b> text, read as text.".toByteArray() to DocumentFormat.TEXT,
        )
        for ((bytes, format) in cases) assertEquals(String(bytes), format, sniff(bytes))
    }

    @Test
    fun `refuses other files`() {
        assertEquals(null, sniff(zip("mimetype", "application/vnd.oasis.opendocument.spreadsheet")))
        assertEquals(null, sniff(zip("photos/one.jpg", "\u00ff\u00d8")))
        assertEquals(null, sniff(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00)))
        assertEquals(null, sniff(ByteArray(0)))
        assertEquals(null, sniff(ByteArray(40) { 0x01 }))
    }
}
