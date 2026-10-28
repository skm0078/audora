// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/TourJourney.kt
// Purpose: The release journey's tour step on the installed app (ADR 2026-10-28-home-and-tour), kept apart from
//          ReleaseJourneyTest so it stays under detekt's class size.

package io.github.skm0078.audora.release

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import io.github.skm0078.audora.R
import io.github.skm0078.audora.release.JourneyDevice.app
import io.github.skm0078.audora.release.JourneyDevice.shows
import io.github.skm0078.audora.ui.firstrun.TOUR_TAG
import io.github.skm0078.audora.ui.firstrun.TourStop

internal class TourJourney(private val rule: ComposeTestRule) {
    private fun text(id: Int, vararg args: Any): String = app.getString(id, *args)

    private fun tap(matcher: SemanticsMatcher) {
        rule.waitUntil(UI_TIMEOUT_MS) { rule.shows(matcher) }
        rule.onAllNodes(matcher).onFirst().performClick()
    }

    /** Replayed from You: each stop says which it is, Next walks them, and Done leaves the library as it was. */
    fun replays() {
        tap(hasText(text(R.string.tab_you)))
        // You keeps the page it was on: step 24b left it on Advanced, whose back arrow returns to You's own page.
        val row = hasText(text(R.string.settings_tour_title))
        val pageBack = hasContentDescription(text(R.string.navigate_back))
        rule.waitUntil(UI_TIMEOUT_MS) { rule.shows(row) || rule.shows(pageBack) }
        if (!rule.shows(row)) tap(pageBack)
        tap(row)
        val stops = TourStop.entries.size
        repeat(stops) { index ->
            rule.waitUntil(UI_TIMEOUT_MS) { rule.shows(hasText(text(R.string.tour_step, index + 1, stops))) }
            if (index < stops - 1) tap(hasText(text(R.string.tour_next)))
        }
        tap(hasText(text(R.string.tour_done)))
        rule.waitUntil(UI_TIMEOUT_MS) { !rule.shows(hasTestTag(TOUR_TAG)) }
        rule.waitUntil(UI_TIMEOUT_MS) { rule.shows(hasText(text(R.string.library_title))) }
    }

    private companion object {
        const val UI_TIMEOUT_MS = 15_000L
    }
}
