// File: core/src/test/kotlin/io/github/skm0078/audora/core/stats/ListeningMilestonesTest.kt
// Purpose: Badges are reached from all time: the best streak, every hour and language heard, books and audiobooks.

package io.github.skm0078.audora.core.stats

import io.github.skm0078.audora.core.model.Lang
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ListeningMilestonesTest {
    @Test
    fun `nothing yet reaches nothing`() {
        val badges = ListeningMilestones.of(emptyMap(), bestStreak = 0, booksFinished = 0, madeAudiobook = false)
        assertEquals(Milestone.entries.associateWith { false }, badges)
    }

    @Test
    fun `each badge at its threshold`() {
        val day = LocalDate.of(2026, 10, 28)
        val slots = mapOf(
            ListeningSlot(day, 8, "a", Lang.EN) to Heard(HOUR * 4),
            ListeningSlot(day, 9, "a", Lang.HI) to Heard(HOUR * 3),
            ListeningSlot(day, 10, "b", Lang.BN) to Heard(HOUR * 3),
        )
        val badges = ListeningMilestones.of(slots, bestStreak = 7, booksFinished = 10, madeAudiobook = true)

        assertEquals(
            Milestone.entries.associateWith { it != Milestone.LONG_STREAK },
            badges,
        )
        val short = ListeningMilestones.of(slots.minus(slots.keys.first()), 30, 9, false)
        assertEquals(
            mapOf(
                Milestone.SHORT_STREAK to true,
                Milestone.HOURS to false,
                Milestone.LANGUAGES to false,
                Milestone.FIRST_AUDIOBOOK to false,
                Milestone.LONG_STREAK to true,
                Milestone.BOOKS to false,
            ),
            short,
        )
    }

    private companion object {
        const val HOUR = 60 * 60_000L
    }
}
