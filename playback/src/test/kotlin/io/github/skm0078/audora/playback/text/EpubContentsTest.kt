// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/text/EpubContentsTest.kt
// Purpose: Both kinds of EPUB contents keep their nesting, `#id` links and plain-label rows, and are placed in the
//          book.

package io.github.skm0078.audora.playback.text

import org.junit.Assert.assertEquals
import org.junit.Test

class EpubContentsTest {
    private fun nav(markup: String) = EpubContents.fromNav(MarkupScanner.scan(markup), "OEBPS/")

    private fun ncx(markup: String) = EpubContents.fromNcx(MarkupScanner.scan(markup), "OEBPS/")

    @Test
    fun `a nested navigation document keeps every level, in order, with its ids`() {
        val entries = nav(
            """<html><body><nav epub:type="landmarks"><ol><li><a href="cover.xhtml">Cover</a></li></ol></nav>
            <nav epub:type="toc"><h1>Contents</h1><ol>
              <li><a href="text/book.xhtml">Book One</a><ol>
                <li><a href="text/book.xhtml#ch1">Chapter <em>1</em></a><ol>
                  <li><a href="text/book.xhtml#s1.1">Section 1.1</a></li></ol></li>
                <li><a href="text/book.xhtml#ch%202">Chapter 2</a></li></ol></li>
              <li><span>Part Two</span><ol>
                <li><a href="text/two.xhtml">Chapter 3</a></li></ol></li>
              <li><span>Appendix with no link</span></li>
              <li><a href="text/end.xhtml">The End</a></li>
            </ol></nav>
            <nav epub:type="page-list"><ol><li><a href="text/book.xhtml#p5">5</a></li></ol></nav></body></html>""",
        )
        assertEquals(
            listOf(
                TocEntry("Book One", "OEBPS/text/book.xhtml", null, 0),
                TocEntry("Chapter 1", "OEBPS/text/book.xhtml", "ch1", 1),
                TocEntry("Section 1.1", "OEBPS/text/book.xhtml", "s1.1", 2),
                TocEntry("Chapter 2", "OEBPS/text/book.xhtml", "ch 2", 1),
                TocEntry("Part Two", "OEBPS/text/two.xhtml", null, 0),
                TocEntry("Chapter 3", "OEBPS/text/two.xhtml", null, 1),
                TocEntry("The End", "OEBPS/text/end.xhtml", null, 0),
            ),
            entries,
        )
    }

    @Test
    fun `a nested NCX keeps every level, whichever order a navPoint lists its parts in`() {
        val entries = ncx(
            """<ncx><docTitle><text>The Book</text></docTitle><navMap>
            <navPoint id="a"><navLabel><text>Part One</text></navLabel><content src="text/one.xhtml"/>
              <navPoint id="b"><navLabel><text>Chapter 1</text></navLabel><content src="text/one.xhtml#c1"/></navPoint>
              <navPoint id="c"><content src="text/one.xhtml#c2"/><navLabel><text> Chapter
                 2 </text></navLabel></navPoint>
            </navPoint>
            <navPoint id="d"><navLabel><text></text></navLabel><content src="text/blank.xhtml"/></navPoint>
            <navPoint id="e"><navLabel><text>Part Two</text></navLabel><content src="text/two.xhtml"/></navPoint>
            </navMap><pageList><pageTarget><navLabel><text>5</text></navLabel><content src="text/one.xhtml#p5"/>
            </pageTarget></pageList></ncx>""",
        )
        assertEquals(
            listOf(
                TocEntry("Part One", "OEBPS/text/one.xhtml", null, 0),
                TocEntry("Chapter 1", "OEBPS/text/one.xhtml", "c1", 1),
                TocEntry("Chapter 2", "OEBPS/text/one.xhtml", "c2", 1),
                TocEntry("Part Two", "OEBPS/text/two.xhtml", null, 0),
            ),
            entries,
        )
    }

    @Test
    fun `the fuller contents wins, the navigation document on a tie`() {
        val stub = listOf(TocEntry("Start", "a.xhtml", null, 0))
        val full = listOf(TocEntry("One", "a.xhtml", null, 0), TocEntry("Two", "b.xhtml", null, 0))
        assertEquals(full, EpubContents.fuller(stub, full))
        assertEquals(full, EpubContents.fuller(full, stub))
        assertEquals(stub, EpubContents.fuller(stub, listOf(TocEntry("Other", "b.xhtml", null, 0))))
    }

    @Test
    fun `rows land on the block their id marks, a picture page passes to the next chapter`() {
        val book = XhtmlBlocks.read(
            MarkupScanner.scan(
                """<body><h1 id="top">Book</h1><p>Intro.</p><h2 id="c1">One</h2><p>Text <a id="mid"/>goes on.</p>
                <aside epub:type="footnote"><p id="n1">A note.</p></aside><div id="end"></div></body>""",
            ),
        )
        assertEquals(Anchor(2, 0), book.anchors["c1"])
        assertEquals(Anchor(3, "Text ".length), book.anchors["mid"])
        assertEquals(null, book.anchors["n1"])
        val two = XhtmlBlocks.read(MarkupScanner.scan("<body><p>Second.</p></body>"))
        val toc = listOf(
            TocEntry("Book", "book.xhtml", null, 0),
            TocEntry("One", "book.xhtml", "c1", 1),
            TocEntry("Middle", "book.xhtml", "mid", 1),
            TocEntry("Missing id", "book.xhtml", "nope", 1),
            TocEntry("After the text", "book.xhtml", "end", 1),
            TocEntry("Plate", "plate.xhtml", null, 0),
            TocEntry("Not in the spine", "extra.xhtml", null, 0),
            TocEntry("Past the end", "back.xhtml", null, 0),
        )
        val placed = EpubContents.place(
            toc,
            spine = listOf("book.xhtml", "plate.xhtml", "two.xhtml", "back.xhtml"),
            chapters = mapOf(0 to (0 to book), 2 to (1 to two)),
        )
        assertEquals(
            listOf(
                ContentsEntry("Book", 0, 0, 0, 0),
                ContentsEntry("One", 0, 2, 0, 1),
                ContentsEntry("Middle", 0, 3, "Text ".length, 1),
                ContentsEntry("Missing id", 0, 0, 0, 1),
                ContentsEntry("After the text", 0, 3, 0, 1),
                ContentsEntry("Plate", 1, 0, 0, 0),
            ),
            placed,
        )
    }
}
