// File: app/src/test/kotlin/io/github/skm0078/audora/ui/stats/StatsAllPeriodsTest.kt
// Purpose: The stats screen's books finished per period, its goal per period and its badges, from the library and
//          the books' places as the screen reads them.

package io.github.skm0078.audora.ui.stats

import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.resume.ResumeAnchor
import io.github.skm0078.audora.core.stats.Heard
import io.github.skm0078.audora.core.stats.ListeningPeriod
import io.github.skm0078.audora.core.stats.ListeningSlot
import io.github.skm0078.audora.core.stats.Milestone
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsAllPeriodsTest {
    private fun inputs(
        books: List<Book> = emptyList(),
        anchors: List<ResumeAnchor> = emptyList(),
        goal: Int = 5,
        slots: Map<ListeningSlot, Heard> = emptyMap(),
    ) = StatsInputs(slots, books, anchors, madeAudiobook = true, goal, TODAY, ZONE, DayOfWeek.MONDAY)

    @Test
    fun `books finished count in the period their place was saved in`() {
        val books = listOf(book("this-week"), book("this-month"), book("last-year"), book("halfway"), book("unopened"))
        val anchors = listOf(
            anchor("this-week", page = 9, on = TODAY),
            anchor("this-month", page = 9, on = LocalDate.of(2026, 10, 3)),
            anchor("last-year", page = 9, on = LocalDate.of(2025, 12, 31)),
            anchor("halfway", page = 4, on = TODAY),
            anchor("removed", page = 9, on = TODAY),
        )
        val all = StatsAllPeriods.of(inputs(books, anchors))

        assertEquals(1, all.getValue(ListeningPeriod.WEEK).finished)
        assertEquals(2, all.getValue(ListeningPeriod.MONTH).finished)
        assertEquals(2, all.getValue(ListeningPeriod.YEAR).finished)
        assertEquals(3, all.getValue(ListeningPeriod.ALL).finished)
    }

    @Test
    fun `the weekly goal is shared out over a month and a year, and off is no ring`() {
        val all = StatsAllPeriods.of(inputs(goal = 7))

        assertEquals(HOUR * 7, all.getValue(ListeningPeriod.WEEK).goalMs)
        assertEquals(HOUR * 31, all.getValue(ListeningPeriod.MONTH).goalMs)
        assertEquals(HOUR * 365, all.getValue(ListeningPeriod.YEAR).goalMs)
        assertNull(all.getValue(ListeningPeriod.ALL).goalMs)
        assertNull(StatsAllPeriods.of(inputs(goal = 0)).getValue(ListeningPeriod.WEEK).goalMs)
    }

    @Test
    fun `nothing listened is empty, and an audiobook made is already a badge`() {
        val data = StatsAllPeriods.of(inputs()).getValue(ListeningPeriod.WEEK)
        assertTrue(data.isEmpty)
        assertEquals(true, data.milestones[Milestone.FIRST_AUDIOBOOK])

        val listened = mapOf(ListeningSlot(TODAY, 9, "a") to Heard(HOUR))
        val busy = StatsAllPeriods.of(inputs(slots = listened)).getValue(ListeningPeriod.WEEK)
        assertEquals(false, busy.isEmpty)
        assertEquals(1, busy.streaks.current)
    }

    private fun book(id: String) = Book(BookId(id), "content://test/$id", id, 1_000, "f-$id", 10, 1, null)

    private fun anchor(id: String, page: Int, on: LocalDate) =
        ResumeAnchor(BookId(id), page, 0, 1L, 1L, 1, on.atTime(LocalTime.NOON).atZone(ZONE).toInstant().toEpochMilli())

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 10, 28)
        val ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
        const val HOUR = 60 * 60_000L
    }
}
