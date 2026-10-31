// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/FindOptionsJourney.kt
// Purpose: Release journey step 54 (ADR 2026-10-29-find-player-options): on the installed app, the library's search
//          finds Focus sound and opens the book's Voice and speed sheet with that group open, and You's Listening
//          group opens the same sheet at its top for the book read last.

package io.github.skm0078.audora.release

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
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
import io.github.skm0078.audora.ui.search.FOUND_TAG

/** Needs a book read before it; [backToTabs] leaves any screen for the main tabs, as the journey does. */
internal class FindOptionsJourney(private val rule: ComposeTestRule, private val backToTabs: () -> Unit) {
    private fun text(id: Int): String = app.getString(id)

    private fun waitFor(matcher: SemanticsMatcher, timeoutMs: Long = UI_TIMEOUT_MS) {
        rule.waitUntil(timeoutMs) { rule.shows(matcher) }
    }

    private fun tap(matcher: SemanticsMatcher) {
        waitFor(matcher)
        rule.onAllNodes(matcher).onFirst().performClick()
    }

    private fun closeTheSheet() {
        JourneyDevice.pressBackKey()
        rule.waitUntil(UI_TIMEOUT_MS) { !rule.shows(hasText(text(R.string.speech_sound))) }
        backToTabs()
    }

    fun openTheSheet() {
        searchOpensFocusSound()
        settingsOpensTheSheet()
    }

    private fun searchOpensFocusSound() {
        backToTabs()
        tap(hasContentDescription(text(R.string.library_search)))
        waitFor(hasSetTextAction())
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput(SEARCHED)
        tap(hasText(text(R.string.focus_title)) and hasAnyAncestor(hasTestTag(FOUND_TAG)))
        // The book opens, and its sheet with Focus sound's own row already showing: its help button is in the group,
        // not on the header (the volume row shows only once a sound is picked).
        val focusHelp = app.getString(R.string.option_help_about, text(R.string.focus_title))
        waitFor(hasContentDescription(focusHelp), OPEN_TIMEOUT_MS)
        closeTheSheet()
        closeTheSearch()
    }

    /** Back from the book returns to the search, its words still there (ADR 2026-10-28-action-search): closed here. */
    private fun closeTheSearch() {
        repeat(MAX_BACKS) {
            if (!rule.shows(hasSetTextAction())) return@repeat
            JourneyDevice.pressBackKey()
            rule.waitForIdle()
        }
        rule.waitUntil(UI_TIMEOUT_MS) { !rule.shows(hasTestTag(FOUND_TAG)) }
        waitFor(hasText(text(R.string.library_title)))
    }

    private fun settingsOpensTheSheet() {
        tap(hasText(text(R.string.tab_you)) and hasClickAction())
        // You keeps the page it was last on; its back arrow returns to the top, where Listening is.
        val door = hasText(text(R.string.settings_speech_body))
        val pageBack = hasContentDescription(text(R.string.navigate_back))
        rule.waitUntil(UI_TIMEOUT_MS) { rule.shows(door) || rule.shows(pageBack) }
        if (!rule.shows(door)) tap(pageBack)
        waitFor(door)
        rule.onAllNodes(door).onFirst().performScrollTo().performClick()
        // The sheet's top: its groups listed, none open.
        waitFor(hasText(text(R.string.speech_sound)), OPEN_TIMEOUT_MS)
        closeTheSheet()
    }

    private companion object {
        const val UI_TIMEOUT_MS = 15_000L
        const val OPEN_TIMEOUT_MS = 60_000L
        const val SEARCHED = "focus"
        const val MAX_BACKS = 3
    }
}
