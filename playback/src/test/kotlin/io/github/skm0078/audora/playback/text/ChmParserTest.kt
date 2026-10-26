// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/text/ChmParserTest.kt
// Purpose: A CHM's pages come out in its contents' order, through all three LZX block kinds and across resets.

package io.github.skm0078.audora.playback.text

import io.github.skm0078.audora.core.result.Outcome
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ChmParserTest {
    private val chm = ChmFile.open(ByteArrayChmSource(Ebooks.bytes("help.chm")))

    @Test
    fun `lists the files and reads the title and contents file from SYSTEM`() {
        assertEquals(ChmParser.Info("Tiny Help", "toc.hhc"), ChmParser.info(chm))
        assertEquals(1, chm.entry("/INDEX.HTM")?.section)
    }

    @Test
    fun `decompresses verbatim, aligned and uncompressed frames, across resets`() {
        val files = chm.read(listOf("/index.htm", "/images/photo.gif", "/sub/chapter2.htm"), Long.MAX_VALUE)
        val index = String(files.getValue("/index.htm"), Charsets.UTF_8)
        assertTrue(index.startsWith("<html><head><title>Welcome</title>"))
        val last = "Step 899: the help file explains step 899 of the long procedure in plain words.</p></body></html>"
        assertTrue(index.endsWith(last))
        assertArrayEquals(picture(70_000), files.getValue("/images/photo.gif"))
        assertTrue(String(files.getValue("/sub/chapter2.htm"), Charsets.ISO_8859_1).contains("<h2>Second page</h2>"))
    }

    @Test
    fun `reads the pages the contents list, once each, named by their entries`() {
        val document = (ChmParser.parse(chm, Long.MAX_VALUE) as Outcome.Success).value
        assertEquals("Tiny Help", document.title)
        assertEquals(listOf("Welcome", "Second page"), document.chapters.map { it.title })
        assertEquals(TextBlock(BlockKind.HEADING, "Getting started"), document.chapters[0].blocks[0])
        assertEquals("Café “quoted” text.", document.chapters[1].blocks[1].text)
        assertTrue(document.chapters.none { chapter -> chapter.blocks.any { "no contents entry" in it.text } })
    }

    @Test
    fun `refuses more text than the limit`() {
        try {
            chm.read(listOf("/index.htm"), 1_000)
            fail("read past the limit")
        } catch (expected: ChmTooLargeException) {
            assertTrue(expected.message.orEmpty().isNotEmpty())
        }
    }

    @Test
    fun `a damaged file is a typed error, not a crash`() {
        val bytes = Ebooks.bytes("help.chm").also { it.fill(0x55, 300, 2_000) }
        try {
            ChmFile.open(ByteArrayChmSource(bytes)).read(listOf("/index.htm"), Long.MAX_VALUE)
            fail("read a damaged file")
        } catch (expected: ChmException) {
            assertTrue(expected.message.orEmpty().isNotEmpty())
        }
    }

    /** The same stand-in picture the fixture script writes. */
    private fun picture(size: Int): ByteArray {
        var x = 12345L
        return ByteArray(size) {
            x = (1103515245L * x + 12345L) and 0x7FFFFFFFL
            ((x shr 16) and 0xFF).toByte()
        }
    }
}
