// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/HomeJourney.kt
// Purpose: Release journey steps 44 and 48 (ADR 2026-10-28-make-it-yours, ADR 2026-10-29-personal-touches-2): "Make
//          it yours" names the greeting and turns a home part on, at once and without a Save, and both undo the same
//          way; "This week" is one of its parts, and its card opens Your listening.

package io.github.skm0078.audora.release

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import io.github.skm0078.audora.R
import io.github.skm0078.audora.release.JourneyDevice.app
import io.github.skm0078.audora.release.JourneyDevice.shows
import io.github.skm0078.audora.ui.library.MAKE_IT_YOURS_TAG

/** [backToTabs] leaves any screen for the main tabs, as the journey does. */
internal class HomeJourney(private val rule: ComposeTestRule, private val backToTabs: () -> Unit) {
    private fun text(id: Int, vararg args: Any): String = app.getString(id, *args)

    private fun waitFor(matcher: SemanticsMatcher) {
        rule.waitUntil(UI_TIMEOUT_MS) { rule.shows(matcher) }
    }

    /** Scrolls to [matcher] first only inside a scrolling list (the menu, the sheet); the ⋮ button has none. */
    private fun tap(matcher: SemanticsMatcher) {
        waitFor(matcher)
        val node = rule.onAllNodes(matcher).onFirst()
        if (rule.shows(matcher and hasAnyAncestor(hasScrollAction()))) node.performScrollTo()
        node.performClick()
    }

    /** The greeting takes the typed name and Recently added shows, then both go back to how they were. */
    fun makesItYours() {
        backToTabs()
        tap(hasText(text(R.string.tab_library)) and hasClickAction())
        openSheet()
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement(NAME)
        toggleRecentlyAdded()
        closeSheet()
        waitFor(namedGreeting())
        waitFor(hasText(text(R.string.home_section_recent)))

        openSheet()
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement("")
        toggleRecentlyAdded()
        closeSheet()
        rule.waitUntil(UI_TIMEOUT_MS) { !rule.shows(namedGreeting()) }
        waitFor(hasText(text(R.string.library_title)))
    }

    /**
     * The sheet lists This week. The journey has listened by now (step 30's stats), so the card is on the home: a tap
     * opens Your listening. On a phone whose log was cleared the card is rightly absent and only the sheet is checked.
     */
    fun offersThisWeek() {
        backToTabs()
        tap(hasText(text(R.string.tab_library)) and hasClickAction())
        openSheet()
        waitFor(hasContentDescription(text(R.string.home_section_week)) and hasClickAction())
        closeSheet()
        val card = SemanticsMatcher("click labelled ${text(R.string.home_week_open)}") {
            it.config.getOrNull(SemanticsActions.OnClick)?.label == text(R.string.home_week_open)
        }
        if (!rule.shows(card)) return
        rule.onAllNodes(card).onFirst().performScrollTo().performClick()
        waitFor(hasText(text(R.string.stats_title)))
        JourneyDevice.pressBackKey()
        rule.waitUntil(UI_TIMEOUT_MS) { !rule.shows(hasText(text(R.string.stats_title))) }
    }

    private fun openSheet() {
        tap(hasContentDescription(text(R.string.reader_more)))
        tap(hasText(text(R.string.library_make_it_yours)))
        waitFor(hasTestTag(MAKE_IT_YOURS_TAG))
    }

    private fun toggleRecentlyAdded() {
        tap(hasContentDescription(text(R.string.home_section_recent)) and hasClickAction())
    }

    /** Back closes the keyboard first if the name field left it up, then the sheet. */
    private fun closeSheet() {
        repeat(MAX_BACKS) {
            if (!rule.shows(hasTestTag(MAKE_IT_YOURS_TAG))) return
            JourneyDevice.pressBackKey()
            rule.waitForIdle()
        }
        rule.waitUntil(UI_TIMEOUT_MS) { !rule.shows(hasTestTag(MAKE_IT_YOURS_TAG)) }
    }

    /** Any of the three greetings with the name: the hour decides which. */
    private fun namedGreeting(): SemanticsMatcher = listOf(
        R.string.library_greeting_morning_named,
        R.string.library_greeting_afternoon_named,
        R.string.library_greeting_evening_named,
    ).map { hasText(text(it, NAME)) }.reduce { one, other -> one or other }

    private companion object {
        const val UI_TIMEOUT_MS = 15_000L
        const val NAME = "Ria"
        const val MAX_BACKS = 3
    }
}
