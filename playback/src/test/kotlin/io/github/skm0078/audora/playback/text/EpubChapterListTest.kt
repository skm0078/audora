// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/text/EpubChapterListTest.kt
// Purpose: Whole small EPUBs, one per kind of contents that used to lose chapters, end in the right chapter list.

package io.github.skm0078.audora.playback.text

import io.github.skm0078.audora.core.config.TextBookConfig
import io.github.skm0078.audora.core.result.Outcome
import org.junit.Assert.assertEquals
import org.junit.Test

class EpubChapterListTest {
    private val container =
        """<container><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>"""

    /** An EPUB of [files] (name → XHTML) read in that order, with [toc] files (name → markup) in its manifest. */
    private fun epub(files: Map<String, String>, toc: Map<String, String> = emptyMap()): TextDocument {
        val items = files.keys.mapIndexed { i, name ->
            """<item id="f$i" href="$name" media-type="application/xhtml+xml"/>"""
        }
        val tocItems = toc.keys.map { name ->
            if (name.endsWith(".ncx")) {
                """<item id="ncx" href="$name" media-type="application/x-dtbncx+xml"/>"""
            } else {
                """<item id="nav" href="$name" media-type="application/xhtml+xml" properties="nav"/>"""
            }
        }
        val refs = files.keys.indices.joinToString("") { """<itemref idref="f$it"/>""" }
        val opf = """<package><metadata/><manifest>${(items + tocItems).joinToString("")}</manifest>
            <spine toc="ncx">$refs</spine></package>"""
        val entries = mapOf("META-INF/container.xml" to container, "OEBPS/content.opf" to opf) +
            (files + toc).mapKeys { "OEBPS/${it.key}" }
        return (EpubParser.parse(entries.mapValues { it.value.toByteArray() }) as Outcome.Success).value
    }

    private fun rows(document: TextDocument, config: TextBookConfig = TextBookConfig()) =
        TextBookText(document, config).contents

    private fun para(words: Int, word: String = "word") = "<p>${List(words) { word }.joinToString(" ")}.</p>"

    @Test
    fun `chapters kept in one file are each listed, nested, on the page they start`() {
        val book = """<html><body><h1 id="p1">Part One</h1>
            <h2 id="c1">Chapter 1</h2>${para(60)}<h2 id="c2">Chapter 2</h2>${para(60)}
            <h3 id="s2">A section</h3>${para(60)}</body></html>"""
        val nav = """<nav epub:type="toc"><ol><li><a href="book.xhtml">Part One</a><ol>
            <li><a href="book.xhtml#c1">Chapter 1</a></li>
            <li><a href="book.xhtml#c2">Chapter 2</a><ol><li><a href="book.xhtml#s2">A section</a></li></ol></li>
            </ol></li></ol></nav>"""
        val document = epub(mapOf("book.xhtml" to book), mapOf("nav.xhtml" to nav))
        val text = TextBookText(document, TextBookConfig(pageChars = 300))
        assertEquals(
            listOf(
                ContentsRow("Part One", 0, 0),
                ContentsRow("Chapter 1", 0, 1),
                ContentsRow("Chapter 2", 2, 1),
                ContentsRow("A section", 4, 2),
            ),
            text.contents,
        )
        assertEquals(listOf("Part One"), text.outline.map { it.title })
        assertEquals(listOf("Chapter 1", "Chapter 2"), text.outline.single().children.map { it.title })
        // Before, the one file was one chapter: the list held "Part One" only, and the timer had no chapter ends.
        assertEquals(listOf(0, 2), text.chapterStarts())
    }

    @Test
    fun `a stub navigation document gives way to a full NCX`() {
        val nav = """<nav epub:type="toc"><ol><li><a href="a.xhtml">Start</a></li></ol></nav>"""
        val ncx = """<ncx><navMap>
            <navPoint><navLabel><text>Book One</text></navLabel><content src="a.xhtml"/>
              <navPoint><navLabel><text>Chapter 1</text></navLabel><content src="b.xhtml"/></navPoint>
              <navPoint><navLabel><text>Chapter 2</text></navLabel><content src="c.xhtml"/></navPoint>
            </navPoint></navMap></ncx>"""
        val document = epub(
            mapOf("a.xhtml" to para(5), "b.xhtml" to para(5), "c.xhtml" to para(5)),
            mapOf("nav.xhtml" to nav, "toc.ncx" to ncx),
        )
        assertEquals(
            listOf(ContentsRow("Book One", 0, 0), ContentsRow("Chapter 1", 1, 1), ContentsRow("Chapter 2", 2, 1)),
            rows(document),
        )
    }

    @Test
    fun `a scanned book whose contents name only a notice lists the chapters in its text`() {
        val notice = "<p>This book was produced in EPUB format by the Internet Archive.</p>"
        val pages = mapOf(
            "notice.html" to notice,
            "page_1.html" to "<p>CONTENTS CHAPTER I. The Start - 2. The Middle - 3. The End</p>",
            "page_2.html" to "<p>CHAPTER I The Start ONCE there was a king.</p>",
            "page_3.html" to "<p>the king slept. 4 CHAPTER 2 The Middle THE queen woke.</p>",
            "page_4.html" to "<p>${List(10) { "Running text" }.joinToString(" ")} CHAPTER 3 The End AT last.</p>",
        )
        val ncx = """<ncx><navMap><navPoint><navLabel><text>Notice</text></navLabel>
            <content src="notice.html"/></navPoint></navMap></ncx>"""
        val document = epub(pages, mapOf("toc.ncx" to ncx))
        assertEquals(
            listOf(
                ContentsRow("Notice", 0),
                ContentsRow("Chapter I: The Start", 2),
                ContentsRow("Chapter 2: The Middle", 3),
                ContentsRow("Chapter 3: The End", 4),
            ),
            rows(document),
        )
        // Before, every scanned page was a chapter end for the sleep timer.
        assertEquals(listOf(0, 2, 3, 4), TextBookText(document).chapterStarts())
    }

    @Test
    fun `a contents row on a picture page, and a link with a stray percent, still open`() {
        val nav = """<nav epub:type="toc"><ol><li><a href="plate.xhtml">Part One</a></li>
            <li><a href="b.xhtml#100%">Chapter 1</a></li><li><a href="c.xhtml">Chapter 2</a></li></ol></nav>"""
        val document = epub(
            mapOf("plate.xhtml" to "<img src='p.jpg'/>", "b.xhtml" to para(5), "c.xhtml" to para(5)),
            mapOf("nav.xhtml" to nav),
        )
        assertEquals(
            listOf(ContentsRow("Part One", 0), ContentsRow("Chapter 1", 0), ContentsRow("Chapter 2", 1)),
            rows(document),
        )
    }

    @Test
    fun `a file no contents row names is listed by its heading, at the level before it`() {
        val nav = """<nav epub:type="toc"><ol><li><a href="a.xhtml">Part</a><ol>
            <li><a href="b.xhtml">One</a></li></ol></li></ol></nav>"""
        val document = epub(
            mapOf("a.xhtml" to para(3), "b.xhtml" to para(3), "c.xhtml" to "<h2>Two</h2>${para(3)}"),
            mapOf("nav.xhtml" to nav),
        )
        assertEquals(
            listOf(ContentsRow("Part", 0, 0), ContentsRow("One", 1, 1), ContentsRow("Two", 2, 1)),
            rows(document),
        )
        assertEquals(listOf("Part", "One", "Two"), document.chapters.map { it.title })
    }
}
