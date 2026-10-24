// File: core/src/test/kotlin/io/github/skm0078/audora/core/library/BookDetailsTest.kt
// Purpose: What a file says fills a book in, the listener's edits are never overwritten, and junk is ignored (ADR-068).

package io.github.skm0078.audora.core.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookDetailsTest {
    private val book = Book(BookId("b"), "content://b", "moby_dick_final", 10, "f", 300, 1, null)

    @Test
    fun `a file's title, author and language fill a book in, and mark it filled`() {
        val file =
            FileDetails(DocumentFormat.PDF, title = "  Moby   Dick ", author = "Herman Melville", language = "en-GB")

        val filled = BookDetails.filled(book, file)

        assertEquals("Moby Dick", filled.title)
        assertEquals("Herman Melville", filled.author)
        assertEquals("en", filled.language)
        assertEquals(DocumentFormat.PDF, filled.format)
        assertEquals(CoverChoice.FirstPage, filled.cover)
        assertEquals(BookDetails.VERSION, filled.detailsVersion)
    }

    @Test
    fun `placeholder titles and account names are not taken`() {
        val junk = listOf("Untitled", "Microsoft Word - report.docx", "scan_0001.pdf", "1234", "x")
        junk.forEach { title ->
            assertEquals(title, book.title, BookDetails.filled(book, FileDetails(null, title = title)).title)
        }
        listOf("Administrator", "user", "--").forEach { author ->
            assertNull(author, BookDetails.filled(book, FileDetails(null, author = author)).author)
        }
    }

    @Test
    fun `without a language tag the title's script says it`() {
        assertEquals("hi", BookDetails.filled(book, FileDetails(null, title = "गीता रहस्य")).language)
        assertEquals("bn", BookDetails.filled(book, FileDetails(null, title = "গীতাঞ্জলি")).language)
        assertEquals("en", BookDetails.filled(book, FileDetails(null)).language) // the file name's letters
        assertNull(BookDetails.scriptLanguage("1234 —"))
    }

    @Test
    fun `an EPUB with its own cover image gets it, unless the listener chose one`() {
        val epub = FileDetails(DocumentFormat.EPUB, hasCoverImage = true)
        assertEquals(CoverChoice.FromBook, BookDetails.filled(book, epub).cover)

        val chosen = book.copy(cover = CoverChoice.Picture, edited = setOf(BookField.COVER))
        assertEquals(CoverChoice.Picture, BookDetails.filled(chosen, epub).cover)
    }

    @Test
    fun `an edit is kept over what the file says when it is filled in again`() {
        val edited = BookDetails.edited(book, BookEdit(title = " My Moby ", author = "H. M.", language = "bn"))
        val file = FileDetails(DocumentFormat.PDF, title = "Moby Dick", author = "Herman Melville", language = "en")

        val refilled = BookDetails.filled(edited, file)

        assertEquals("My Moby", refilled.title)
        assertEquals("H. M.", refilled.author)
        assertEquals("bn", refilled.language)
        assertEquals(setOf(BookField.TITLE, BookField.AUTHOR, BookField.LANGUAGE), refilled.edited)
    }

    @Test
    fun `a blank title is refused and a blank author clears it, still edited`() {
        val withAuthor = book.copy(author = "Someone")

        val edited = BookDetails.edited(withAuthor, BookEdit(title = "   ", author = " "))

        assertEquals(book.title, edited.title)
        assertNull(edited.author)
        assertEquals(setOf(BookField.AUTHOR), edited.edited)
    }

    @Test
    fun `handing a field back to the file unmarks it and asks for a fill again`() {
        val edited = book.copy(language = "bn", edited = setOf(BookField.LANGUAGE, BookField.TITLE), detailsVersion = 1)

        val back = BookDetails.edited(edited, BookEdit(fromFile = setOf(BookField.LANGUAGE)))

        assertEquals(setOf(BookField.TITLE), back.edited)
        assertEquals(0, back.detailsVersion)
        assertEquals("en", BookDetails.filled(back, FileDetails(null, language = "en")).language)
    }

    @Test
    fun `reset forgets every edit and the chosen cover`() {
        val edited = book.copy(cover = CoverChoice.Page(3), edited = BookField.entries.toSet(), detailsVersion = 1)

        val reset = BookDetails.reset(edited)

        assertEquals(emptySet<BookField>(), reset.edited)
        assertEquals(CoverChoice.FirstPage, reset.cover)
        assertEquals(0, reset.detailsVersion)
    }

    @Test
    fun `language tags are cut to their language`() {
        val cases = mapOf("en-GB" to "en", "BN" to "bn", "hi_IN" to "hi", "und" to null, "" to null, "12" to null)
        cases.forEach { (raw, expected) -> assertEquals(raw, expected, BookDetails.languageTag(raw)) }
        assertNull(BookDetails.languageTag(null))
    }

    @Test
    fun `cover choices are stored and read back, and damage reads as the first page`() {
        listOf(
            CoverChoice.FirstPage,
            CoverChoice.Page(0),
            CoverChoice.Page(41),
            CoverChoice.Picture,
            CoverChoice.FromBook,
        )
            .forEach { assertEquals(it, CoverChoice.decode(CoverChoice.encode(it))) }
        assertNull(CoverChoice.encode(CoverChoice.FirstPage))
        listOf("page:", "page:-1", "page:x", "gallery", "").forEach {
            assertEquals(it, CoverChoice.FirstPage, CoverChoice.decode(it))
        }
    }
}
