// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/epub/BookPagesTest.kt
// Purpose: Reading a book's pages for a copy: skipped pages, a book that stops, progress.

package io.github.skm0078.audora.playback.epub

import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.document.PdfPageError
import org.junit.Assert.assertEquals
import org.junit.Test

class BookPagesTest {
    @Test
    fun `a page that cannot be extracted is skipped and progress counts every page`() {
        val progress = ArrayList<Int>()
        val result = BookPages { done, _ -> progress.add(done) }.read(3, { page ->
            if (page == 1) {
                Outcome.Failure(PdfPageError.ExtractionFailed(page, "broken"))
            } else {
                Outcome.Success(listOf(sentence(page, 0, "Text.")))
            }
        }, "test")

        val loaded = (result as Outcome.Success).value
        assertEquals(setOf(0, 2), loaded.units.keys)
        assertEquals(1, loaded.skippedPages)
        assertEquals(listOf(1, 2, 3), progress)
    }

    @Test
    fun `a book that can no longer be read stops the copy`() {
        val result = BookPages().read(3, { Outcome.Failure(PdfPageError.BookClosed) }, "test")

        assertEquals(Outcome.Failure(CopyError.BookUnreadable(0)), result)
    }
}
