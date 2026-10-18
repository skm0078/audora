// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/book/PageCursorTest.kt
// Purpose: Reading starts at the sentence containing the chosen offset and walks the following pages once.

package io.github.skm0078.audora.playback.book

import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.TableRowInfo
import io.github.skm0078.audora.core.model.UnitKind
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
        val units = tableOf(rows)

        val cursor = PageCursor(pageCount = 5, startPage = 4, startChar = rows[0].charStart)

        assertEquals(listOf(0, 1, 2, 3), cursor.playable(4, units).map { it.index })
        val later = PageCursor(pageCount = 5, startPage = 4, startChar = rows[1].charStart)
        assertEquals(listOf(2, 3), later.playable(4, units).map { it.index })
    }

    @Test
    fun aStartOnAKnownUnitSkipsTheSummarySharingItsOffset() {
        // A tap on row 0, or a rebuild while row 0 plays: the summary is not said again; a rebuild on it is.
        val rows = page(4, 3)
        val units = tableOf(rows)

        val onRow = PageCursor(pageCount = 5, startPage = 4, startChar = rows[0].charStart, startIndex = 1)
        assertEquals(listOf(1, 2, 3), onRow.playable(4, units).map { it.index })
        val onSummary = PageCursor(pageCount = 5, startPage = 4, startChar = rows[0].charStart, startIndex = 0)
        assertEquals(listOf(0, 1, 2, 3), onSummary.playable(4, units).map { it.index })
        assertEquals("other pages are whole", 4, onRow.playable(3, page(3, 4)).size)
    }

    @Test
    fun anIndexFromAnotherExtractionNeverDropsTheSentenceAtTheOffset() {
        // The reader's page had one unit more than the service's: its index names the next unit, or none at all.
        val units = page(4, 4)
        val onePastIt = PageCursor(pageCount = 5, startPage = 4, startChar = units[1].charStart, startIndex = 2)
        assertEquals(listOf(1, 2, 3), onePastIt.playable(4, units).map { it.index })
        val pastTheEnd = PageCursor(pageCount = 5, startPage = 4, startChar = units[3].charStart, startIndex = 7)
        assertEquals(listOf(3), pastTheEnd.playable(4, units).map { it.index })
    }

    @Test
    fun anIndexOneTooHighAtASharedOffsetStillStartsThere() {
        // An index naming a unit that does not start at the offset (another extraction's numbering) is ignored: the
        // start plays as a saved resume would, the summary first, and nothing at the offset is lost.
        val rows = page(4, 3)
        val units = tableOf(rows)
        val tooHigh = PageCursor(pageCount = 5, startPage = 4, startChar = rows[0].charStart, startIndex = 2)
        assertEquals(listOf(0, 1, 2, 3), tooHigh.playable(4, units).map { it.index })
    }

    /** [rows] as a table's rows after a zero-length summary sharing row 0's offset, indexed from the summary. */
    private fun tableOf(rows: List<ReadingUnit>): List<ReadingUnit> {
        val info = TableRowInfo(0, null, rows.size, 1, emptyList(), emptyList(), continued = true)
        val summary = rows[0].copy(
            index = 0,
            charEnd = rows[0].charStart,
            text = "",
            kind = UnitKind.TABLE_SUMMARY,
            table = info,
        )
        return listOf(summary) + rows.map {
            it.copy(index = it.index + 1, kind = UnitKind.TABLE_ROW, table = info.copy(row = it.index))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun aStartPageOutsideTheBookIsRefused() {
        PageCursor(pageCount = 3, startPage = 3, startChar = 0)
    }
}
