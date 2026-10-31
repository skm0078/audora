// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/start/ContentStartFinderTest.kt
// Purpose: The start of reading is found past front matter from a chapter list or from the first pages, and the
//          search skips nothing whenever it is not sure (ADR 2026-10-31-skip-front-matter).

package io.github.skm0078.audora.pdf.start

import io.github.skm0078.audora.core.config.ContentStartConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.UnitKind
import io.github.skm0078.audora.pdf.document.OutlineEntry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentStartFinderTest {
    private val finder = ContentStartFinder()

    private fun entry(title: String, page: Int?, vararg children: OutlineEntry) =
        OutlineEntry(title, page, children.toList())

    private fun outline(vararg entries: OutlineEntry, pageCount: Int = 200) =
        finder.fromOutline(entries.toList(), pageCount)

    @Test
    fun `a chapter list's front matter entries are passed over`() {
        val found = outline(
            entry("Cover", 0),
            entry("Copyright", 2),
            entry("Contents", 3),
            entry("Preface", 5),
            entry("Chapter 1: Beginnings", 9),
            entry("Chapter 2", 30),
        ) as StartSearch.Found
        assertEquals(9, found.start.page)
        assertEquals("Chapter 1: Beginnings", found.start.title)
        assertEquals(
            setOf(TITLE, COPY, CONTENTS, PREFACE),
            found.start.skipped,
        )
        assertEquals(ContentStart.Source.CHAPTER_LIST, found.start.source)
    }

    @Test
    fun `a wrapper entry holding the front matter is looked into`() {
        val found = outline(
            entry("My Book", 0, entry("Contents", 1), entry("भूमिका", 4), entry("अध्याय 1", 7), entry("अध्याय 2", 20)),
        ) as StartSearch.Found
        assertEquals(7, found.start.page)
    }

    @Test
    fun `a chapter list that only lists chapters still skips to the first`() {
        val found = outline(entry("The Storm", 11), entry("The Calm", 40)) as StartSearch.Found
        assertEquals(11, found.start.page)
        assertTrue(found.start.skipped.isEmpty())
    }

    @Test
    fun `a book whose first chapter is its first page skips nothing`() {
        assertEquals(StartSearch.FirstPage(StartSearch.Why.STARTS_AT_ONCE, 0), outline(entry("Introduction", 0)))
    }

    @Test
    fun `a chapter list with nothing to go by leaves it to the pages`() {
        assertEquals(null, outline())
        assertEquals(null, outline(entry("Contents", 1), entry("Preface", 3)))
        // Half-way into the book is not front matter: more likely an outline of one part only.
        assertEquals(null, outline(entry("Part Two", 150), pageCount = 200))
    }

    @Test
    fun `a short document is never searched`() = runBlocking {
        val search = finder.find(listOf(entry("Chapter 1", 3)), pageCount = 5, nowMs = { 0L }) { error("no reads") }
        assertEquals(StartSearch.FirstPage(StartSearch.Why.SHORT_DOCUMENT, 0), search)
    }

    @Test
    fun `pages past title, copyright, contents and preface find the first chapter`() = runBlocking {
        val book = Book(
            short("A Novel"),
            copyright(),
            contents(),
            contents(firstLine = "Chapter 9 The End ........ 210"),
            page(heading("Preface"), prose()),
            page(prose()),
            short("— the author, 2021"),
            page(heading("Chapter 1"), prose()),
        )
        val found = book.search() as StartSearch.Found
        assertEquals(7, found.start.page)
        assertEquals("Chapter 1", found.start.title)
        assertEquals(
            setOf(TITLE, COPY, CONTENTS, PREFACE),
            found.start.skipped,
        )
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7), book.reads)
    }

    @Test
    fun `a chapter named only by its title starts the reading after the front matter`() = runBlocking {
        val found = Book(short("A Novel"), copyright(), page(heading("The Boy Who Lived"), prose())).search()
        assertEquals(StartSearch.Found(ContentStart(2, "The Boy Who Lived", setOf(TITLE, COPY), PAGES)), found)
    }

    @Test
    fun `a contents page opening with chapter one is still the contents`() = runBlocking {
        val found = Book(contents(firstLine = "Chapter 1 The Storm .... 1"), page(heading("Chapter 1"), prose()))
            .search() as StartSearch.Found
        assertEquals(1, found.start.page)
    }

    @Test
    fun `a bengali book with its index and introduction starts at chapter one`() = runBlocking {
        val found = Book(
            short("কিশোর সাহিত্য সমগ্র"),
            page(heading("সূচিপত্র"), prose(words = 12)),
            contents(digits = "০১২৩৪৫৬৭৮৯"),
            page(heading("ভূমিকা"), prose(word = "গল্প")),
            page(prose(word = "গল্প")),
            page(heading("প্রথম অধ্যায়"), prose(word = "গল্প")),
        ).search() as StartSearch.Found
        assertEquals(5, found.start.page)
        assertEquals(setOf(TITLE, CONTENTS, PREFACE), found.start.skipped)
    }

    @Test
    fun `a hindi copyright page is told by its publisher words`() = runBlocking {
        val found = Book(
            short("उपन्यास"),
            page(unit("प्रकाशक: राजकमल प्रकाशन, प्रथम संस्करण 2019, मूल्य 250 रुपये")),
            page(prose(word = "कहानी")),
        ).search() as StartSearch.Found
        assertEquals(2, found.start.page)
        assertEquals(setOf(TITLE, COPY), found.start.skipped)
    }

    @Test
    fun `a first page with its title, a preamble heading and its text starts the book`() = runBlocking {
        // The Bengali Universal Declaration: "মুখবন্ধ" (preamble, also "preface") under the title, then the text.
        val first = page(heading("মানবাধিকারের সার্বজনীন ঘোষণাপত্র"), heading("মুখবন্ধ"), prose(word = "যেহেতু"))
        val book = Book(first, page(prose(word = "যেহেতু")), page(heading("অনুচ্ছেদ ১"), prose(word = "সকল")))
        assertEquals(StartSearch.FirstPage(StartSearch.Why.STARTS_AT_ONCE, 1), book.search())
    }

    @Test
    fun `a book that starts with running text skips nothing`() = runBlocking {
        assertEquals(StartSearch.FirstPage(StartSearch.Why.STARTS_AT_ONCE, 1), Book(page(prose())).search())
    }

    @Test
    fun `a scanned page that is not recognised yet ends the search without skipping`() = runBlocking {
        val search = Book(short("A Novel"), null, page(prose())).search()
        assertEquals(StartSearch.FirstPage(StartSearch.Why.SCANNED_PAGE, 1), search)
    }

    @Test
    fun `a preface that never ends within reach skips nothing`() = runBlocking {
        val pages = listOf(short("A Novel"), page(heading("Preface"), prose())) + List(40) { page(prose()) }
        val book = Book(*pages.toTypedArray())
        assertEquals(StartSearch.FirstPage(StartSearch.Why.NOT_SURE, 30), book.search(pageCount = 300))
        assertEquals(30, book.reads.size)
    }

    @Test
    fun `the search gives up when it runs out of time`() = runBlocking {
        var clock = 0L
        val book = Book(*Array(20) { short("Plate $it") })
        val search = finder.find(emptyList(), 300, nowMs = { clock }) { p ->
            clock += 1_000L
            book.pages.getOrNull(p)
        }
        assertEquals(StartSearch.FirstPage(StartSearch.Why.OUT_OF_TIME, 3), search)
    }

    @Test
    fun `a narrow search window never reaches past a third of a small book`() {
        assertEquals(0, ContentStartConfig().pagesToSearch(12))
        assertEquals(8, ContentStartConfig().pagesToSearch(24))
        assertEquals(30, ContentStartConfig().pagesToSearch(1_000))
    }

    // --- a book made of pages ---

    private inner class Book(vararg pages: List<ReadingUnit>?) {
        val pages = pages.toList()
        val reads = ArrayList<Int>()

        suspend fun search(pageCount: Int = 200): StartSearch = finder.find(emptyList(), pageCount, { 0L }) { p ->
            reads += p
            pages.getOrNull(p) ?: if (p < pages.size) null else listOf(unit(LOREM.repeat(20)))
        }
    }

    private fun page(vararg units: ReadingUnit) = units.toList()

    private fun short(text: String) = page(unit(text))

    private fun heading(text: String) = unit(text, UnitKind.HEADING)

    private fun prose(words: Int = 120, word: String = "word") = unit(List(words) { word }.joinToString(" "))

    private fun copyright() = page(
        unit("Copyright © 2021 A. Writer. All rights reserved."),
        unit("Published by Small Press. ISBN 978-0-00-000000-0. Printed in India."),
    )

    private fun contents(firstLine: String = "Chapter 1 The Storm .... 1", digits: String = "0123456789") = page(
        unit("Contents"),
        unit(firstLine),
        *Array(8) { i -> unit("Chapter ${i + 2} A title here .... ${digits[(i + 1) % 10]}${digits[(i * 3) % 10]}") },
    )

    private fun unit(text: String, kind: UnitKind = UnitKind.BODY) =
        ReadingUnit(0, 0, 0, text.length, text, Lang.EN, 0, null, kind)

    private companion object {
        val TITLE = FrontMatterKind.TITLE_PAGES
        val COPY = FrontMatterKind.COPYRIGHT
        val CONTENTS = FrontMatterKind.CONTENTS
        val PREFACE = FrontMatterKind.PREFACE
        val PAGES = ContentStart.Source.PAGES
        const val LOREM = "lorem ipsum dolor "
    }
}
