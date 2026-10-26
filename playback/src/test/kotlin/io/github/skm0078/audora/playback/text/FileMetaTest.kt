// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/text/FileMetaTest.kt
// Purpose: A file's own title, author, language and cover are read from each format's metadata (ADR-068).

package io.github.skm0078.audora.playback.text

import io.github.skm0078.audora.core.library.DocumentFormat
import io.github.skm0078.audora.core.library.FileDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileMetaTest {
    private val container = """<?xml version="1.0"?>
        <container><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>"""

    private fun opf(metadata: String, manifest: String) = """<?xml version="1.0"?>
        <package version="3.0"><metadata>$metadata</metadata><manifest>
        <item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/>$manifest</manifest>
        <spine><itemref idref="c1"/></spine></package>"""

    private fun epub(opf: String) = mapOf(FileMeta.EPUB_CONTAINER to container, "OEBPS/content.opf" to opf)
        .mapValues { it.value.toByteArray() }

    @Test
    fun `an EPUB 3 package gives its creator, language and cover-image item`() {
        val pkg = opf(
            "<dc:title>Gitanjali</dc:title><dc:creator>Rabindranath Tagore</dc:creator><dc:language>bn</dc:language>",
            """<item id="cv" href="images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>""",
        )

        val details = FileMeta.epubPackage(epub(pkg))

        assertEquals(EpubDetails("Gitanjali", "Rabindranath Tagore", "bn", "OEBPS/images/cover.jpg"), details)
    }

    @Test
    fun `an EPUB 2 cover is named by its meta, and only a picture counts`() {
        val epub2 = opf(
            """<dc:title>Old</dc:title><meta name="cover" content="pic"/>""",
            """<item id="pic" href="cover.png" media-type="image/png"/>""",
        )
        assertEquals("OEBPS/cover.png", FileMeta.epubPackage(epub(epub2))?.coverPath)

        val notPicture = opf(
            """<dc:title>Old</dc:title><meta name="cover" content="c1"/>""",
            "",
        )
        assertNull(FileMeta.epubPackage(epub(notPicture))?.coverPath)
    }

    @Test
    fun `an EPUB without its package has no details`() {
        assertNull(FileMeta.epubPackage(mapOf(FileMeta.EPUB_CONTAINER to container.toByteArray())))
    }

    @Test
    fun `Word core properties give title, creator and language`() {
        val core = """<cp:coreProperties><dc:title>Annual Report</dc:title><dc:creator>Asha Rao</dc:creator>
            <dc:language>en-IN</dc:language></cp:coreProperties>"""

        val details = FileMeta.office(DocumentFormat.DOCX, mapOf("docProps/core.xml" to core.toByteArray()))

        assertEquals(FileDetails(DocumentFormat.DOCX, "Annual Report", "Asha Rao", "en-IN"), details)
    }

    @Test
    fun `OpenDocument meta falls back to the initial creator`() {
        val meta = """<office:document-meta><office:meta><dc:title>Letter</dc:title>
            <meta:initial-creator>Ravi</meta:initial-creator></office:meta></office:document-meta>"""

        val details = FileMeta.office(DocumentFormat.ODT, mapOf("meta.xml" to meta.toByteArray()))

        assertEquals("Letter", details.title)
        assertEquals("Ravi", details.author)
        assertEquals(FileDetails(DocumentFormat.ODT), FileMeta.office(DocumentFormat.ODT, emptyMap()))
    }

    @Test
    fun `a web page's head gives its title, author meta and lang`() {
        val page = """<!doctype html><html lang="hi"><head><title>गीता</title>
            <meta name="Author" content=" Vyasa "></head><body>…</body></html>"""

        assertEquals(FileDetails(DocumentFormat.HTML, "गीता", "Vyasa", "hi"), FileMeta.html(page.toByteArray()))
    }

    @Test
    fun `an RTF info group gives its title and a plain author`() {
        val rtf = """{\rtf1\ansi{\info{\title Short Story}{\author Mira Nair}}\pard Hello\par}"""

        val details = FileMeta.rtf(rtf.toByteArray(Charsets.ISO_8859_1))

        assertEquals("Short Story", details.title)
        assertEquals("Mira Nair", details.author)
    }

    @Test
    fun `an FB2's title-info gives its title, authors and language`() {
        val head = """<?xml version="1.0" encoding="utf-8"?><FictionBook><description><title-info>
            <author><first-name>Leo</first-name><last-name>Tolstoy</last-name></author>
            <author><nickname>Translator</nickname></author>
            <book-title>Childhood</book-title><lang>ru</lang></title-info></description><body>""".toByteArray()

        assertEquals(
            FileDetails(DocumentFormat.FB2, title = "Childhood", author = "Leo Tolstoy, Translator", language = "ru"),
            FileMeta.fb2(head),
        )
    }

    @Test
    fun `a Kindle book's EXTH gives its title, author and language`() {
        val content = MobiFile.Content("A Mobi Book", "Ann Writer", "en", isHtml = true, markup = "")

        assertEquals(
            FileDetails(DocumentFormat.MOBI, title = "A Mobi Book", author = "Ann Writer", language = "en"),
            FileMeta.mobi(content),
        )
    }
}
