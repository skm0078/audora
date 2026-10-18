// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/document/PdfBookTest.kt
// Purpose: Page-at-a-time reading, the reopen-every-N-pages rule, passwords and failures, with a fake engine.

package io.github.skm0078.audora.pdf.document

import io.github.skm0078.audora.core.config.ExtractionConfig
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.errorOrNull
import io.github.skm0078.audora.core.result.getOrNull
import io.github.skm0078.audora.pdf.extract.RawPage
import io.github.skm0078.audora.pdf.extract.RawPageFixture
import io.github.skm0078.audora.pdf.units.ReadingUnitBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfBookTest {
    private class FakeEngine(
        val pages: Int,
        val password: String? = null,
        val labels: (Int) -> String? = { null },
        val text: (Int) -> String = { "Page $it." },
    ) : PdfEngine {
        val passwordsSeen = ArrayList<String?>()
        var openHandles = 0
        var reads = 0
        var failNextOpen = false

        override fun open(password: String?): Outcome<PdfDocumentHandle, PdfOpenError> {
            passwordsSeen.add(password)
            return when {
                failNextOpen -> Outcome.Failure(PdfOpenError.Unreadable("gone"))

                this.password != null && password == null -> Outcome.Failure(PdfOpenError.PasswordRequired)

                password != this.password -> Outcome.Failure(PdfOpenError.IncorrectPassword)

                else -> {
                    openHandles++
                    Outcome.Success(handle())
                }
            }
        }

        private fun handle() = object : PdfDocumentHandle {
            override val pageCount = pages

            override fun readPage(pageIndex: Int): Outcome<RawPage, PdfPageError> {
                reads++
                return Outcome.Success(
                    RawPageFixture.page(RawPageFixture.chars(text(pageIndex)), pageIndex = pageIndex),
                )
            }

            override fun outline() = listOf(OutlineEntry("Chapter", 0))

            override fun pageLabel(pageIndex: Int) = labels(pageIndex)

            override fun close() {
                openHandles--
            }
        }
    }

    @Test
    fun `a page reads the same whichever page was read before it, in one book or another`() {
        val texts = listOf("He turned and said that the", "rains came late that year.", "Then the river rose high.")
        val builder = ReadingUnitBuilder()
        fun open() = requireNotNull(PdfBook.open(FakeEngine(pages = 3) { texts[it] }).getOrNull())
        val inOrder = open()
        inOrder.readPage(0, builder)
        val second = requireNotNull(inOrder.readPage(1, builder).getOrNull())
        assertTrue(second.continuesPreviousPage)
        val jumped = requireNotNull(open().readPage(1, builder).getOrNull())
        assertTrue(jumped.continuesPreviousPage)
        assertEquals(second.units, jumped.units)
        val back = open()
        back.readPage(2, builder)
        assertTrue(requireNotNull(back.readPage(1, builder).getOrNull()).continuesPreviousPage)
        assertFalse(requireNotNull(back.readPage(0, builder).getOrNull()).continuesPreviousPage)
        assertFalse(requireNotNull(back.readPage(2, builder).getOrNull()).continuesPreviousPage)
    }

    @Test
    fun `the outline comes from the open document and does not count as a page read`() {
        val engine = FakeEngine(pages = 3)
        val book = requireNotNull(PdfBook.open(engine, config = ExtractionConfig(reopenEveryPages = 1)).getOrNull())
        assertEquals(listOf(0), book.outline().getOrNull()?.map { it.page })
        assertTrue(book.pageText(0) is Outcome.Success)
        assertEquals(1, book.opens)
        book.close()
        assertEquals(PdfPageError.BookClosed, book.outline().errorOrNull())
    }

    @Test
    fun `page labels come from the open document, cleaned, and do not count as page reads`() {
        val printed = listOf("i", " ii ", null, "", "A-\u00003", "12")
        val engine = FakeEngine(pages = printed.size, labels = printed::get)
        val book = requireNotNull(PdfBook.open(engine).getOrNull())
        assertEquals(listOf("i", "ii", null, null, "A-3", "12"), book.pageLabels().getOrNull())
        assertEquals(0, engine.reads)
        book.close()
        assertEquals(PdfPageError.BookClosed, book.pageLabels().errorOrNull())
    }

    @Test
    fun `500 pages read in order reopen the document every 100 pages`() {
        val engine = FakeEngine(pages = 500)
        val book = requireNotNull(PdfBook.open(engine, config = ExtractionConfig(reopenEveryPages = 100)).getOrNull())
        val builder = ReadingUnitBuilder()
        repeat(500) { page ->
            val units = requireNotNull(book.readingUnits(page, builder).getOrNull())
            assertEquals("Page $page.", units.single().text)
            assertEquals(1, engine.openHandles)
        }
        // Cleanup reads up to three pages ahead as neighbours (ADR-020); the caches keep it to one read per page.
        assertTrue("reads=${engine.reads}", engine.reads <= 500 + ReadingUnitBuilder().cleanupConfig.neighbourPages)
        assertEquals(1 + (engine.reads - 1) / 100, book.opens)
        assertEquals(engine.reads, book.pagesRead)
        book.close()
        assertEquals(0, engine.openHandles)
    }

    @Test
    fun `password is asked for, checked and reused on every reopen`() {
        val engine = FakeEngine(pages = 3, password = "secret")
        assertEquals(PdfOpenError.PasswordRequired, PdfBook.open(engine).errorOrNull())
        assertEquals(PdfOpenError.IncorrectPassword, PdfBook.open(engine, password = "nope").errorOrNull())
        val book =
            requireNotNull(
                PdfBook.open(engine, password = "secret", config = ExtractionConfig(reopenEveryPages = 1)).getOrNull(),
            )
        repeat(3) { assertTrue(book.pageText(it) is Outcome.Success) }
        assertEquals(listOf(null, "nope", "secret", "secret", "secret"), engine.passwordsSeen)
    }

    @Test
    fun `failed reopen is reported and closes the book`() {
        val engine = FakeEngine(pages = 3)
        val book = requireNotNull(PdfBook.open(engine, config = ExtractionConfig(reopenEveryPages = 1)).getOrNull())
        assertTrue(book.pageText(0) is Outcome.Success)
        engine.failNextOpen = true
        assertEquals(PdfPageError.ReopenFailed(PdfOpenError.Unreadable("gone")), book.pageText(1).errorOrNull())
        assertEquals(PdfPageError.BookClosed, book.pageText(1).errorOrNull())
    }

    @Test
    fun `out of range and closed books are typed errors`() {
        val book = requireNotNull(PdfBook.open(FakeEngine(pages = 2)).getOrNull())
        assertEquals(PdfPageError.PageOutOfRange(2, 2), book.pageText(2).errorOrNull())
        assertEquals(PdfPageError.PageOutOfRange(-1, 2), book.pageText(-1).errorOrNull())
        book.close()
        assertEquals(PdfPageError.BookClosed, book.pageText(0).errorOrNull())
    }
}
