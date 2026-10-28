// File: app/src/main/kotlin/io/github/skm0078/audora/ui/stats/StatsActions.kt
// Purpose: What the stats screen and its share card ask of their route.

package io.github.skm0078.audora.ui.stats

import io.github.skm0078.audora.core.stats.ListeningPeriod

/** What the stats screen asks of its route. */
internal interface StatsScreenActions {
    fun onClose()

    fun onPeriod(chosen: ListeningPeriod)

    fun onGoal(weeklyHours: Int)

    fun onShare()

    fun onClear()
}

internal interface StatsShareActions {
    fun onBack()

    fun onChoices(next: StatsCardChoices)

    fun onSave()

    fun onShare()
}
