// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/book/PageCursorTest.kt
// Purpose: Reading starts at the sentence containing the chosen offset and walks the following pages once.

package io.github.skm0078.audora.playback.book

import io.github.skm0078.audora.playback.page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageCursorTest {
    @Test
    fun walksFromTheStartPageToTheLast() {
        val cursor = PageCursor(pageCount = 4, startPage = 2, startChar = 0)
        assertEquals(2, cursor.nextPage())
        assertEquals(3, cursor.nextPage())
        assertNull(cursor.nextPage())
        assertNull(cursor.nextPage())
    }

    @Test
    fun theStartPageBeginsAtTheSentenceContainingTheOffset() {
        val cursor = PageCursor(pageCount = 3, startPage = 1, startChar = 25)
        // Sentences cover 0..9, 10..19, 20..29, 30..39: offset 25 is inside the third.
        assertEquals(listOf(2, 3), cursor.playable(1, page(1, 4)).map { it.index })
        assertEquals("other pages are whole", 4, cursor.playable(2, page(2, 4)).size)
    }

    @Test
    fun anOffsetPastTheLastSentenceSkipsThePage() {
        val cursor = PageCursor(pageCount = 3, startPage = 0, startChar = 1_000)
        assertEquals(emptyList<Any>(), cursor.playable(0, page(0, 4)))
    }

    @Test
    fun resumingAtAContinuedTablesFirstRowPlaysItsEmptySummaryFirst() {
        // A page opening with carried rows: an empty "Table continues" summary shares row 0's charStart.
        val rows = page(4, 3)
        val summary = rows[0].copy(index = 0, charEnd = rows[0].charStart, text = "")
        val units = listOf(summary) + rows.map { it.copy(index = it.index + 1) }

        val cursor = PageCursor(pageCount = 5, startPage = 4, startChar = rows[0].charStart)

        assertEquals(listOf(0, 1, 2, 3), cursor.playable(4, units).map { it.index })
        val later = PageCursor(pageCount = 5, startPage = 4, startChar = rows[1].charStart)
        assertEquals(listOf(2, 3), later.playable(4, units).map { it.index })
    }

    @Test
    fun aStartOnAKnownUnitSkipsTheSummarySharingItsOffset() {
        // A tap on row 0, or a rebuild while row 0 plays: the summary is not said again; a rebuild on it is.
        val rows = page(4, 3)
        val summary = rows[0].copy(index = 0, charEnd = rows[0].charStart, text = "")
        val units = listOf(summary) + rows.map { it.copy(index = it.index + 1) }

        val onRow = PageCursor(pageCount = 5, startPage = 4, startChar = rows[0].charStart, startIndex = 1)
        assertEquals(listOf(1, 2, 3), onRow.playable(4, units).map { it.index })
        val onSummary = PageCursor(pageCount = 5, startPage = 4, startChar = rows[0].charStart, startIndex = 0)
        assertEquals(listOf(0, 1, 2, 3), onSummary.playable(4, units).map { it.index })
        assertEquals("other pages are whole", 4, onRow.playable(3, page(3, 4)).size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aStartPageOutsideTheBookIsRefused() {
        PageCursor(pageCount = 3, startPage = 3, startChar = 0)
    }
}
