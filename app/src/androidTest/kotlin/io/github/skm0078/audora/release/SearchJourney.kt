// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/SearchJourney.kt
// Purpose: Release journey step 24b (ADR 2026-10-28-action-search): the library's search finds a setting on
//          Advanced; its row opens You on that page with the setting focused, and Back returns to the search, its
//          words still there.

package io.github.skm0078.audora.release

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.skm0078.audora.R
import io.github.skm0078.audora.release.JourneyDevice.app
import io.github.skm0078.audora.release.JourneyDevice.shows
import io.github.skm0078.audora.ui.search.FOUND_TAG
import io.github.skm0078.audora.ui.settings.FOCUSED_TAG

/** [back] presses Back in the app on screen, as the journey does. */
internal class SearchJourney(private val rule: ComposeTestRule, private val back: () -> Unit) {
    private fun text(id: Int): String = app.getString(id)

    private fun waitFor(matcher: SemanticsMatcher) {
        rule.waitUntil(UI_TIMEOUT_MS) { rule.shows(matcher) }
    }

    private fun first(matcher: SemanticsMatcher) = rule.onAllNodes(matcher).onFirst()

    fun opensASetting() {
        waitFor(hasContentDescription(text(R.string.library_search)))
        first(hasContentDescription(text(R.string.library_search))).performClick()
        waitFor(hasSetTextAction())
        first(hasSetTextAction()).performTextInput(SEARCHED_SETTING)
        // Tapped as soon as it shows: the found rows wait for the books to narrow, so the screen does not change
        // again under the tap (it once did, and the tap was lost).
        val row = hasText(text(R.string.settings_volume_keys_title)) and hasAnyAncestor(hasTestTag(FOUND_TAG))
        waitFor(row)
        first(row).performClick()
        waitFor(hasTestTag(FOCUSED_TAG))
        waitFor(hasText(text(R.string.settings_advanced_title)))
        back()
        waitFor(hasTestTag(FOUND_TAG))
        back()
        rule.waitUntil(UI_TIMEOUT_MS) { !rule.shows(hasTestTag(FOUND_TAG)) }
        waitFor(hasText(text(R.string.library_title)))
    }

    private companion object {
        const val UI_TIMEOUT_MS = 15_000L
        const val SEARCHED_SETTING = "volume"
    }
}
