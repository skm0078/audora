// File: core/src/test/kotlin/io/github/skm0078/audora/core/library/BookProgressTest.kt
// Purpose: A book's progress comes from its anchor's page, and only when the book's page count is known.

package io.github.skm0078.audora.core.library

import io.github.skm0078.audora.core.resume.ResumeAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookProgressTest {
    private val book = Book(BookId("b"), "content://x/1", "Title", 1, "v1:a", 200, 0, null)

    private fun anchor(page: Int) = ResumeAnchor(book.id, page, 0, 0L, 0L, 1, SAVED_AT)

    @Test
    fun `progress is the share of pages started`() {
        val cases = listOf(0 to 0.005f, 99 to 0.5f, 199 to 1f)
        cases.forEach { (page, fraction) ->
            assertEquals("page $page", fraction, requireNotNull(BookProgress.of(book, anchor(page))).fraction, EPS)
        }
    }

    @Test
    fun `no progress without an anchor or a page count`() {
        assertNull(BookProgress.of(book, null))
        assertNull(BookProgress.of(book.copy(pageCount = null), anchor(3)))
    }

    @Test
    fun `an anchor past the end of a shorter file is clamped to the last page`() {
        val progress = requireNotNull(BookProgress.of(book.copy(pageCount = 10), anchor(40)))

        assertEquals(9, progress.page)
        assertEquals(SAVED_AT, progress.savedAtMillis)
    }

    private companion object {
        const val SAVED_AT = 1_000L
        const val EPS = 1e-6f
    }
}
