// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/HintsJourney.kt
// Purpose: Release journey step for the first-use hints (ADR 2026-10-30-first-use-hints): on the installed app, the
//          library's search finds "Tips as you go", it opens You › Advanced at that switch, and the switch turns the
//          hints on and off. The test runner mutes hints for every test, so the step leaves them muted again.

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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import io.github.skm0078.audora.R
import io.github.skm0078.audora.release.JourneyDevice.app
import io.github.skm0078.audora.release.JourneyDevice.shows
import io.github.skm0078.audora.release.JourneyDevice.toggleRow
import io.github.skm0078.audora.ui.hints.HintsState
import io.github.skm0078.audora.ui.search.FOUND_TAG

/** [backToTabs] leaves any screen for the main tabs, as the journey does. */
internal class HintsJourney(private val rule: ComposeTestRule, private val backToTabs: () -> Unit) {
    private fun text(id: Int): String = app.getString(id)

    private fun waitFor(matcher: SemanticsMatcher) {
        rule.waitUntil(UI_TIMEOUT_MS) { rule.shows(matcher) }
    }

    private fun tap(matcher: SemanticsMatcher) {
        waitFor(matcher)
        rule.onAllNodes(matcher).onFirst().performClick()
    }

    /** A row in a scrolling page: brought into view first (the top bar's buttons do not scroll, so [tap] does not). */
    private fun tapRow(matcher: SemanticsMatcher) {
        waitFor(matcher)
        rule.onAllNodes(matcher).onFirst().performScrollTo().performClick()
    }

    /** Search finds the switch by a word it never shows ("tips" is in its title, "hints" only in its terms). */
    fun searchFindsTheMute() {
        val hints = HintsState.of(app)
        backToTabs()
        tap(hasContentDescription(text(R.string.library_search)))
        waitFor(hasSetTextAction())
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput(SEARCHED)
        tap(hasText(text(R.string.settings_hints_title)) and hasAnyAncestor(hasTestTag(FOUND_TAG)))
        val row = toggleRow(text(R.string.settings_hints_title))
        waitFor(row)
        // Muted by the test runner; the switch reads the saved flag off the main thread, so wait for it to show.
        rule.waitUntil(UI_TIMEOUT_MS) { hints.muted }
        tapRow(row)
        rule.waitUntil(UI_TIMEOUT_MS) { !hints.muted }
        tapRow(row)
        rule.waitUntil(UI_TIMEOUT_MS) { hints.muted }
        backToTabs()
        closeTheSearch()
    }

    /** The library's search keeps its words after a result opened (ADR 2026-10-28-action-search): closed here. */
    private fun closeTheSearch() {
        repeat(MAX_BACKS) {
            if (!rule.shows(hasSetTextAction())) return@repeat
            JourneyDevice.pressBackKey()
            rule.waitForIdle()
        }
        rule.waitUntil(UI_TIMEOUT_MS) { !rule.shows(hasTestTag(FOUND_TAG)) }
        waitFor(hasText(text(R.string.library_title)))
    }

    private companion object {
        const val UI_TIMEOUT_MS = 15_000L
        const val SEARCHED = "hints"
        const val MAX_BACKS = 3
    }
}
