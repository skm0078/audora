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
        assertEquals(Spot(0, 2 * CHARS), skip.next(unit(0, 0)))
        assertEquals(Spot(0, 2 * CHARS), skip.next(unit(0, 1)))
    }

    @Test
    fun nextRunsAcrossAPageBreak() {
        // Page 0 ends on a paragraph end, so page 1 starts one.
        assertEquals(Spot(1, 0), skip.next(unit(0, 2)))
        // Page 1's last unit ends one; page 2 is the next paragraph.
        assertEquals(Spot(1, 3 * CHARS), skip.next(unit(1, 0)))
        assertEquals(Spot(2, 0), skip.next(unit(1, 3)))
    }

    @Test
    fun nextIsNullInTheLastParagraph() {
        assertNull(skip.next(unit(2, 0)))
    }

    @Test
    fun previousRestartsTheParagraphThenGoesToTheOneBefore() {
        // Mid-paragraph: its own start.
        assertEquals(Spot(0, 2 * CHARS), skip.previous(unit(0, 4)))
        // At a paragraph's start: the paragraph before.
        assertEquals(Spot(0, 0), skip.previous(unit(0, 2)))
        // Across a page break: page 1 starts a paragraph, so back to page 0's last one.
        assertEquals(Spot(0, 2 * CHARS), skip.previous(unit(1, 0)))
    }

    @Test
    fun previousAtTheBookStartStaysThere() {
        assertEquals(Spot(0, 0), skip.previous(unit(0, 0)))
        assertEquals(Spot(0, 0), skip.previous(unit(0, 1)))
    }

    @Test
    fun withNoParagraphMarksNextStopsAtThePageLimit() {
        val flat = List(10) { "sss" }
        val limited = ParagraphSkip({ p -> units(p, flat[p]) }, flat.size, maxPages = 2)
        assertEquals(Spot(3, 0), limited.next(units(0, flat[0])[1]))
        assertEquals(Spot(3, 0), limited.previous(units(5, flat[5])[1]))
    }

    @Test
    fun anUnreadablePageIsPassedOver() {
        val withHole = ParagraphSkip({ p -> if (p == 1) null else page(p) }, book.size, maxPages = 3)
        assertEquals(Spot(2, 0), withHole.next(unit(0, 2)))
    }

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
