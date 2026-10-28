// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/DrawerJourney.kt
// Purpose: Release journey step 45 (ADR 2026-10-28-phone-drawer): the library's menu button opens the side drawer,
//          and its Settings row closes it and opens the Settings page.

package io.github.skm0078.audora.release

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.skm0078.audora.R
import io.github.skm0078.audora.release.JourneyDevice.app
import io.github.skm0078.audora.release.JourneyDevice.shows
import io.github.skm0078.audora.ui.shell.DRAWER_TAG

/** [backToTabs] leaves any screen for the main tabs, as the journey does. */
internal class DrawerJourney(private val rule: ComposeTestRule, private val backToTabs: () -> Unit) {
    private fun text(id: Int): String = app.getString(id)

    private fun waitFor(matcher: SemanticsMatcher) {
        rule.waitUntil(UI_TIMEOUT_MS) { rule.shows(matcher) }
    }

    /** The phone's drawer opens from the menu button; Settings closes it and shows the Settings page. */
    fun opensSettings() {
        backToTabs()
        waitFor(hasText(text(R.string.tab_library)) and hasClickAction())
        rule.onAllNodes(hasText(text(R.string.tab_library)) and hasClickAction()).onFirst().performClick()
        waitFor(hasContentDescription(text(R.string.drawer_open)))
        rule.onAllNodes(hasContentDescription(text(R.string.drawer_open))).onFirst().performClick()
        waitFor(hasTestTag(DRAWER_TAG))
        rule.onNode(hasText(text(R.string.settings_title)) and hasClickAction()).performScrollTo().performClick()
        rule.waitUntil(UI_TIMEOUT_MS) { !rule.shows(hasTestTag(DRAWER_TAG)) }
        waitFor(hasText(text(R.string.settings_title)))
    }

    private companion object {
        const val UI_TIMEOUT_MS = 15_000L
    }
}
