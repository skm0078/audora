// File: core/src/test/kotlin/io/github/skm0078/audora/core/stats/ListeningStreaksTest.kt
// Purpose: Current and best streaks, with today still open and one forgiven rest day a week, table by table.

package io.github.skm0078.audora.core.stats

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ListeningStreaksTest {
    /** Wednesday 28 October 2026 is [TODAY]; each number is days before it. */
    private fun streaks(vararg back: Int, restDays: Boolean = true) =
        ListeningStreaks.of(back.map { TODAY.minusDays(it.toLong()) }.toSet(), TODAY, DayOfWeek.MONDAY, restDays)

    @Test
    fun `no rest days counts plain runs`() {
        val cases = listOf(
            intArrayOf() to ListeningStreaks(0, 0, 0),
            intArrayOf(0, 1, 2) to ListeningStreaks(3, 3, 0),
            // Today not listened yet: the run up to yesterday still stands.
            intArrayOf(1, 2) to ListeningStreaks(2, 2, 0),
            // A day missed yesterday ends it.
            intArrayOf(0, 2, 3, 4) to ListeningStreaks(1, 3, 0),
            intArrayOf(5, 6) to ListeningStreaks(0, 2, 0),
        )
        cases.forEach { (back, expected) ->
            assertEquals("days ${back.toList()}", expected, streaks(*back, restDays = false))
        }
    }

    @Test
    fun `one missed day a week between days listened is a rest day`() {
        val cases = listOf(
            // Monday 26 missed, between Sunday and Tuesday: forgiven, not counted.
            intArrayOf(0, 1, 3, 4) to ListeningStreaks(4, 4, 1),
            // Yesterday missed and today still open: forgiven for now.
            intArrayOf(2, 3) to ListeningStreaks(2, 2, 1),
            // Two days in a row missed: no rest day can bridge it.
            intArrayOf(0, 3, 4) to ListeningStreaks(1, 2, 0),
            // Wed 21 and Fri 23 missed in the same Monday week: only the first is forgiven.
            intArrayOf(0, 1, 2, 3, 4, 6, 8, 9) to ListeningStreaks(5, 5, 0),
            // Missed days in different weeks are each forgiven (Fri 16, Mon 26).
            intArrayOf(0, 1, 3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 14) to ListeningStreaks(13, 13, 2),
        )
        cases.forEach { (back, expected) -> assertEquals("days ${back.toList()}", expected, streaks(*back)) }
    }

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 10, 28)
    }
}
