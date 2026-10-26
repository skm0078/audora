// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/text/EpubParserTest.kt
// Purpose: An EPUB's chapters come out in spine order with their titles, notes last, junk left out.

package io.github.skm0078.audora.playback.text

import io.github.skm0078.audora.core.result.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubParserTest {
    private val container = """<?xml version="1.0"?>
        <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles>
        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>"""

    private val opf = """<?xml version="1.0" encoding="UTF-8"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="3.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
        <dc:title>Gitanjali</dc:title><dc:language>bn</dc:language></metadata>
        <manifest>
          <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
          <item id="c1" href="text/ch%201.xhtml" media-type="application/xhtml+xml"/>
          <item id="c2" href="text/ch2.xhtml" media-type="application/xhtml+xml"/>
          <item id="notes" href="text/popup.xhtml" media-type="application/xhtml+xml"/>
          <item id="img" href="cover.jpg" media-type="image/jpeg"/>
        </manifest>
        <spine><itemref idref="nav"/><itemref idref="c1"/><itemref idref="notes" linear="no"/>
        <itemref idref="c2"/></spine>
        </package>"""

    private val nav = """<html xmlns:epub="http://www.idpf.org/2007/ops"><body>
        <nav epub:type="toc"><ol><li><a href="text/ch%201.xhtml">Song One</a></li>
        <li><a href="text/ch2.xhtml#start">Song <em>Two</em></a></li></ol></nav></body></html>"""

    private val chapter1 = """<html><head><title>x</title><style>p{}</style></head><body>
        <h1>Where the mind is without fear</h1>
        <p>Where the mind is without fear<a epub:type="noteref" href="#n1"><sup>1</sup></a> and the head is held high;</p>
        <p>Where knowledge is free.&nbsp;</p>
        <aside epub:type="footnote" id="n1"><p>Written in 1910.</p></aside>
        <ul><li>One</li><li>Two</li></ul>
        <table><tr><td>Year</td><td>1910</td></tr></table>
        </body></html>"""

    private val chapter2 = """<html><body>
        <p>চিত্ত যেথা ভয়শূন্য, উচ্চ যেথা শির।<br/>জ্ঞান যেথা মুক্ত।</p></body></html>"""

    private fun entries(extra: Map<String, String> = emptyMap()) = (
        mapOf(
            "META-INF/container.xml" to container,
            "OEBPS/content.opf" to opf,
            "OEBPS/nav.xhtml" to nav,
            "OEBPS/text/ch 1.xhtml" to chapter1,
            "OEBPS/text/ch2.xhtml" to chapter2,
            "OEBPS/text/popup.xhtml" to "<p>Hidden answer.</p>",
        ) + extra
        ).mapValues { it.value.toByteArray() }

    @Test
    fun `reads chapters in spine order with contents titles`() {
        val book = (EpubParser.parse(entries()) as Outcome.Success).value
        assertEquals("Gitanjali", book.title)
        assertEquals("bn", book.language)
        assertEquals(listOf("Song One", "Song Two"), book.chapters.map { it.title })
        val first = book.chapters[0].blocks
        assertEquals(
            listOf(
                TextBlock(BlockKind.HEADING, "Where the mind is without fear"),
                TextBlock(BlockKind.BODY, "Where the mind is without fear and the head is held high;"),
                TextBlock(BlockKind.BODY, "Where knowledge is free."),
                TextBlock(BlockKind.LIST_ITEM, "One"),
                TextBlock(BlockKind.LIST_ITEM, "Two"),
                TextBlock(BlockKind.BODY, "Year; 1910"),
                TextBlock(BlockKind.BODY, "Written in 1910."),
            ),
            first,
        )
        assertEquals("চিত্ত যেথা ভয়শূন্য, উচ্চ যেথা শির। জ্ঞান যেথা মুক্ত।", book.chapters[1].blocks.single().text)
    }

    @Test
    fun `falls back to the NCX, then to the first heading`() {
        val epub2 = opf.replace("""properties="nav"""", "")
            .replace("<spine>", """<spine toc="ncx">""")
            .replace(
                "</manifest>",
                """<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/></manifest>""",
            )
        val ncx = """<ncx><navMap><navPoint id="p1"><navLabel><text>First</text></navLabel>
            <content src="text/ch%201.xhtml"/></navPoint></navMap></ncx>"""
        val book = (
            EpubParser.parse(
                entries(mapOf("OEBPS/content.opf" to epub2, "OEBPS/toc.ncx" to ncx)),
            ) as Outcome.Success
            )
            .value
        assertEquals("First", book.chapters[0].title)
        assertEquals(null, book.chapters[1].title)
    }

    @Test
    fun `refuses broken, protected and textless books`() {
        assertTrue(EpubParser.parse(emptyMap()) is Outcome.Failure)
        val drm = """<encryption><EncryptedData><CipherData><CipherReference URI="OEBPS/text/ch2.xhtml"/>
            </CipherData></EncryptedData></encryption>"""
        val protected = EpubParser.parse(entries(mapOf("META-INF/encryption.xml" to drm)))
        assertTrue((protected as Outcome.Failure).error is TextBookError.Locked)
        val fonts = """<encryption><CipherReference URI="OEBPS/fonts/a.otf"/></encryption>"""
        assertTrue(EpubParser.parse(entries(mapOf("META-INF/encryption.xml" to fonts))) is Outcome.Success)
        val images =
            entries(mapOf("OEBPS/text/ch 1.xhtml" to "<img src='a.jpg'/>", "OEBPS/text/ch2.xhtml" to "<p> </p>"))
        assertEquals(Outcome.Failure(TextBookError.NoText), EpubParser.parse(images))
    }

    @Test
    fun `resolves relative paths`() {
        assertEquals("OEBPS/images/a b.png", EpubParser.resolve("OEBPS/text/", "../images/a%20b.png#x"))
        assertEquals("c.xhtml", EpubParser.resolve("", "./c.xhtml"))
        assertTrue(EpubParser.isTextEntry("OEBPS/a.XHTML"))
        assertTrue(!EpubParser.isTextEntry("OEBPS/cover.jpg"))
    }
}
