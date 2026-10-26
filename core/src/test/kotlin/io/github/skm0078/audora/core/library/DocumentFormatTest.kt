// File: core/src/test/kotlin/io/github/skm0078/audora/core/library/DocumentFormatTest.kt
// Purpose: PDF, EPUB, Word, OpenDocument, RTF, HTML, FB2, MOBI, CHM and text books, and comic archives, are told
//          apart by their first bytes.

package io.github.skm0078.audora.core.library

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    /** A real ZIP's start, as a Files app hands it over: stored entries, so the local headers carry their sizes. */
    private fun realZip(vararg entries: Pair<String, String>, deflatedFolder: Boolean = false): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                val bytes = content.toByteArray()
                val folder = name.endsWith('/')
                zip.putNextEntry(
                    ZipEntry(name).apply {
                        if (!(folder && deflatedFolder)) {
                            method = ZipEntry.STORED
                            size = bytes.size.toLong()
                            crc = CRC32().apply { update(bytes) }.value
                        }
                    },
                )
                zip.write(bytes)
            }
        }
        return out.toByteArray().copyOf(HEAD)
    }

    private fun palmDatabase(typeCreator: String): ByteArray =
        ByteArray(60) { 'a'.code.toByte() } + typeCreator.toByteArray() + ByteArray(20)

    @Test
    fun `recognizes the e-book formats added for Moon+ parity`() {
        val cases = listOf(
            """<?xml version="1.0" encoding="windows-1251"?>
                <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">""".toByteArray() to DocumentFormat.FB2,
            "<FictionBook><body>".toByteArray() to DocumentFormat.FB2,
            realZip("Stories.FB2" to "<FictionBook/>") to DocumentFormat.FB2,
            palmDatabase("BOOKMOBI") to DocumentFormat.MOBI,
            palmDatabase("TEXtREAd") to DocumentFormat.MOBI,
            "ITSF\u0003\u0000\u0000\u0000`\u0000".toByteArray() to DocumentFormat.CHM,
        )
        for ((bytes, format) in cases) assertEquals(String(bytes), format, sniff(bytes))
        assertEquals(null, sniff(palmDatabase("DataPlkr")))
    }

    @Test
    fun `tells a comic archive from books and other zips`() {
        fun comic(bytes: ByteArray) = DocumentFormats.isComicArchive(bytes, pdfWindowBytes = 16)
        assertTrue(comic(realZip("001.jpg" to "\u00ff\u00d8", "002.jpg" to "\u00ff\u00d8")))
        assertTrue(comic(realZip("Issue 1/" to "", "Issue 1/page01.PNG" to "png")))
        assertTrue(comic(realZip("pages/" to "", "pages/a.webp" to "w", deflatedFolder = true)))
        assertTrue(comic(realZip("ComicInfo.xml" to "<ComicInfo/>", "1.jpg" to "j")))
        assertFalse(comic(realZip("notes.txt" to "hello")))
        assertFalse(comic(realZip("mimetype" to "application/epub+zip", "cover.jpg" to "j")))
        assertFalse(comic("%PDF-1.4".toByteArray()))
        assertEquals(null, sniff(realZip("001.jpg" to "\u00ff\u00d8")))
    }

    @Test
    fun `refuses other files`() {
        assertEquals(null, sniff(zip("mimetype", "application/vnd.oasis.opendocument.spreadsheet")))
        assertEquals(null, sniff(zip("photos/one.jpg", "\u00ff\u00d8")))
        assertEquals(null, sniff(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00)))
        assertEquals(null, sniff(ByteArray(0)))
        assertEquals(null, sniff(ByteArray(40) { 0x01 }))
    }

    private companion object {
        /** The head a library reads before it opens a file. */
        const val HEAD = 1024
    }
}
