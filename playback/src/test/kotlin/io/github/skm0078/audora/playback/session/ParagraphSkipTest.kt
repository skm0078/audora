// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/session/ParagraphSkipTest.kt
// Purpose: Next and previous paragraph land on paragraph starts, across pages, at the book's ends and on bad pages.

package io.github.skm0078.audora.playback.session

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParagraphSkipTest {
    private val book = listOf(
        "sE" + "ssE",
        "s" + "sE" + "E",
        "ss",
    )
    private val skip = ParagraphSkip(::page, book.size, maxPages = 3)

    @Test
    fun nextGoesToTheFirstSentenceAfterTheParagraphEnd() {
        assertEquals(at(0, 2), skip.next(unit(0, 0)))
        assertEquals(at(0, 2), skip.next(unit(0, 1)))
    }

    @Test
    fun nextRunsAcrossAPageBreak() {
        // Page 0 ends on a paragraph end, so page 1 starts one.
        assertEquals(at(1, 0), skip.next(unit(0, 2)))
        // Page 1's last unit ends one; page 2 is the next paragraph.
        assertEquals(at(1, 3), skip.next(unit(1, 0)))
        assertEquals(at(2, 0), skip.next(unit(1, 3)))
    }

    @Test
    fun nextIsNullInTheLastParagraph() {
        assertNull(skip.next(unit(2, 0)))
    }

    @Test
    fun previousRestartsTheParagraphThenGoesToTheOneBefore() {
        // Mid-paragraph: its own start.
        assertEquals(at(0, 2), skip.previous(unit(0, 4)))
        // At a paragraph's start: the paragraph before.
        assertEquals(at(0, 0), skip.previous(unit(0, 2)))
        // Across a page break: page 1 starts a paragraph, so back to page 0's last one.
        assertEquals(at(0, 2), skip.previous(unit(1, 0)))
    }

    @Test
    fun previousAtTheBookStartStaysThere() {
        assertEquals(at(0, 0), skip.previous(unit(0, 0)))
        assertEquals(at(0, 0), skip.previous(unit(0, 1)))
    }

    @Test
    fun withNoParagraphMarksNextStopsAtThePageLimit() {
        val flat = List(10) { "sss" }
        val limited = ParagraphSkip({ p -> units(p, flat[p]) }, flat.size, maxPages = 2)
        assertEquals(Spot(3, 0), limited.next(units(0, flat[0])[1]))
        assertEquals(at(3, 0), limited.previous(units(5, flat[5])[1]))
    }

    @Test
    fun anUnreadablePageIsPassedOver() {
        val withHole = ParagraphSkip({ p -> if (p == 1) null else page(p) }, book.size, maxPages = 3)
        assertEquals(at(2, 0), withHole.next(unit(0, 2)))
    }

    @Test
    fun previousPastUnreadablePagesGoesToThePageBeforeThem() {
        val holes = ParagraphSkip({ p -> if (p in 2..4) null else units(p, "sss") }, 10, maxPages = 3)
        assertEquals(Spot(1, 0), holes.previous(units(5, "sss")[0]))
        // Reaching the book's first page with nothing on the way: the book's start, not "already there".
        val openingHoles = ParagraphSkip({ p -> if (p < 2) null else units(p, "sss") }, 10, maxPages = 3)
        assertEquals(Spot(0, 0), openingHoles.previous(units(2, "sss")[0]))
    }

    @Test
    fun aSkipLandsOnTheUnitItNamesWhenTwoShareAnOffset() {
        // Page 0 ends a paragraph; page 1 opens with a zero-length summary and its first row at the same offset.
        val summary = ReadingUnit(1, 0, 0, 0, "", Lang.EN, 0, null)
        val row = ReadingUnit(1, 1, 0, CHARS - 1, "r", Lang.EN, PARAGRAPH_PAUSE_MS, null)
        val pages = listOf(page(0), listOf(summary, row), page(2))
        val shared = ParagraphSkip({ pages[it] }, pages.size, maxPages = 3)

        assertEquals(Spot(1, 0, 0), shared.next(unit(0, 2)))
        assertEquals(Spot(1, 0, 0), shared.previous(unit(2, 0)))
        // With no paragraph end in reach, the unread page's start names no unit.
        assertNull(ParagraphSkip({ units(it, "sss") }, 9, maxPages = 2).next(units(0, "sss")[0])?.index)
    }

    /** The unit [index] on [page] as a skip names it. */
    private fun at(page: Int, index: Int) = Spot(page, index * CHARS, index)

    private fun unit(page: Int, index: Int) = page(page)[index]

    private fun page(page: Int): List<ReadingUnit> = units(page, book[page])

    private fun units(page: Int, marks: String): List<ReadingUnit> = marks.mapIndexed { i, mark ->
        val pause = if (mark == 'E') PARAGRAPH_PAUSE_MS else 0
        ReadingUnit(page, i, i * CHARS, i * CHARS + CHARS - 1, "s", Lang.EN, pause, null)
    }

    private companion object {
        const val CHARS = 10
        const val PARAGRAPH_PAUSE_MS = 400
    }
}
