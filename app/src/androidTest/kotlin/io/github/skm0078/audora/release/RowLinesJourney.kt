// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/RowLinesJourney.kt
// Purpose: Release journey step 46 (ADR 2026-10-29-say-what-it-does): Settings' switches say what they change and
//          cost on their own rows, so "Keep screen on" names its battery cost without the info button.

package io.github.skm0078.audora.release

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.skm0078.audora.R
import io.github.skm0078.audora.release.JourneyDevice.app
import io.github.skm0078.audora.release.JourneyDevice.shows

/** [backToTabs] leaves any screen for the main tabs, as the journey does. */
internal class RowLinesJourney(private val rule: ComposeTestRule, private val backToTabs: () -> Unit) {
    private fun text(id: Int): String = app.getString(id)

    private fun waitFor(matcher: SemanticsMatcher) {
        rule.waitUntil(UI_TIMEOUT_MS) { rule.shows(matcher) }
    }

    /** The settings page shows the battery cost of keeping the screen on and of true black, under their names. */
    fun settingsSayWhatTheyCost() {
        backToTabs()
        val you = hasText(text(R.string.tab_you)) and hasClickAction()
        waitFor(you)
        rule.onAllNodes(you).onFirst().performClick()
        listOf(R.string.row_line_keep_screen_on, R.string.row_line_true_black).forEach { line ->
            waitFor(hasText(text(line)))
            rule.onAllNodes(hasText(text(line))).onFirst().performScrollTo()
        }
        backToTabs()
    }

    private companion object {
        const val UI_TIMEOUT_MS = 15_000L
    }
}
