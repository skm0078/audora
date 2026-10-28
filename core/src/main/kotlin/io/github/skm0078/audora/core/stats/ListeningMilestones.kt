// File: core/src/main/kotlin/io/github/skm0078/audora/core/stats/ListeningMilestones.kt
// Purpose: The stats screen's badges: which are reached, worked out from everything listened and made.

package io.github.skm0078.audora.core.stats

/** The badges, in the order the screen shows them. */
enum class Milestone { SHORT_STREAK, HOURS, LANGUAGES, FIRST_AUDIOBOOK, LONG_STREAK, BOOKS }

/**
 * Each badge is worked out from all time, so once reached it stays reached: the best streak, all hours, every
 * language heard. Books finished and audiobooks made are counted from the library, so deleting every saved audiobook
 * takes "First audiobook" back; nothing about badges is stored.
 */
object ListeningMilestones {
    fun of(
        allTime: Map<ListeningSlot, Heard>,
        bestStreak: Int,
        booksFinished: Int,
        madeAudiobook: Boolean,
        goals: MilestoneGoals = MilestoneGoals(),
    ): Map<Milestone, Boolean> {
        val hours = allTime.values.sumOf { it.ms } / HOUR_MS
        val languages = allTime.filter { it.value.ms > 0 || it.value.words > 0 }.keys.mapNotNull { it.lang }.toSet()
        return Milestone.entries.associateWith { milestone ->
            when (milestone) {
                Milestone.SHORT_STREAK -> bestStreak >= goals.shortStreakDays
                Milestone.HOURS -> hours >= goals.hours
                Milestone.LANGUAGES -> languages.size >= goals.languages
                Milestone.FIRST_AUDIOBOOK -> madeAudiobook
                Milestone.LONG_STREAK -> bestStreak >= goals.longStreakDays
                Milestone.BOOKS -> booksFinished >= goals.booksFinished
            }
        }
    }

    private const val HOUR_MS = 60 * 60_000L
}
