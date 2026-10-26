// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/text/MobiFileTest.kt
// Purpose: Kindle and Mobipocket books (PalmDOC and HUFF/CDIC compression, KF8) and PalmDOC files read to chapters;
//          DRM-locked ones are refused.

package io.github.skm0078.audora.playback.text

import io.github.skm0078.audora.core.result.Outcome
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MobiFileTest {
    private fun read(name: String, limit: Long = Long.MAX_VALUE, withText: Boolean = true) =
        MobiFile.read(ByteArrayInputStream(Ebooks.bytes(name)), limit, withText)

    private fun content(name: String) = (read(name) as Outcome.Success).value

    private fun document(name: String) = MobiParser.parse(content(name)) { PlainTextParser().parse(it) }

    @Test
    fun `reads a MOBI 6 book's details and its chapters at the page breaks`() {
        val book = content("mobi6-palmdoc.mobi")
        assertEquals("A Mobi Book", book.title)
        assertEquals("Ann Writer", book.author)
        assertEquals("en", book.language)
        val document = document("mobi6-palmdoc.mobi")
        assertEquals(listOf("Chapter One", "অধ্যায় দুই"), document.chapters.map { it.title })
        assertEquals(
            "It was a bright cold day in April, and the clocks were striking.",
            document.chapters[0].blocks[1].text,
        )
        assertEquals("मन जहाँ डर से परे है।", document.chapters[1].blocks[2].text)
        assertEquals(
            "Line 119 of a long chapter repeats the long chapter text so records split.",
            document.chapters[1].blocks.last().text,
        )
    }

    @Test
    fun `HUFF and CDIC compression gives the same text as PalmDOC`() {
        assertEquals(content("mobi6-palmdoc.mobi").markup, content("huffcdic.mobi").markup)
    }

    @Test
    fun `a KF8 book reads each file's fragments as a chapter, without its head`() {
        val document = document("kf8.azw3")
        assertEquals("A KF8 Book", document.title)
        assertEquals(listOf("Part One", "Part Two"), document.chapters.map { it.title })
        assertEquals("The second file's fragment.", document.chapters[1].blocks[1].text)
        assertTrue(document.chapters.flatMap { it.blocks }.none { "Ignored" in it.text })
    }

    @Test
    fun `a PalmDOC file is plain text`() {
        val document = document("palmdoc.pdb")
        assertEquals("Palm_Book", document.title)
        assertEquals("A plain PalmDOC book, café and all.", document.chapters.flatMap { it.blocks }.last().text)
    }

    @Test
    fun `refuses a DRM-locked book, a damaged one and one over the limit`() {
        assertEquals(TextBookError.Locked("protected by DRM"), (read("drm.azw") as Outcome.Failure).error)
        assertTrue((read("mobi6-palmdoc.mobi", limit = 100) as Outcome.Failure).error is TextBookError.TooLarge)
        val cut = Ebooks.bytes("mobi6-palmdoc.mobi").copyOf(400)
        val damaged = MobiFile.read(ByteArrayInputStream(cut), Long.MAX_VALUE) as Outcome.Failure
        assertTrue(damaged.error is TextBookError.Damaged)
    }

    @Test
    fun `details alone skip the text and its size limit`() {
        // The book details sheet reads with no text allowance at all.
        val book = (read("huffcdic.mobi", limit = 0, withText = false) as Outcome.Success).value
        assertEquals("Huff Book", book.title)
        assertEquals("", book.markup)
    }

    @Test
    fun `PalmDOC decompression handles literals, runs, space pairs and overlapping copies`() {
        // "a" then a copy of distance 1, length 5 (overlapping), then a space+'b' pair, then a run of two bytes.
        val packed =
            byteArrayOf(0x61, 0x80.toByte(), 0x0A, (0x62 xor 0x80).toByte(), 0x02, 0xE9.toByte(), 0x80.toByte())
        assertEquals("aaaaaa bé\u0080", String(PalmDoc.decompress(packed), Charsets.ISO_8859_1))
    }

    @Test
    fun `trailing entries are cut from the end of a record`() {
        val record = "text".toByteArray() + byteArrayOf(0x00) + byteArrayOf(0x01, 0x02, 0x83.toByte())
        assertEquals(4, TrailingEntries.size(record, flags = 3))
        assertEquals(0, TrailingEntries.size(record, flags = 0))
    }
}
