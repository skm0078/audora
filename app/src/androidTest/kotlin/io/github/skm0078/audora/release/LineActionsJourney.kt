// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/LineActionsJourney.kt
// Purpose: Release journey step for ADR 2026-10-30-player-line-actions: on the installed app, the reader's ⋮ brings
//          the quick tips back and its Page or Text button says its name; the full player shows Bookmark, Highlight
//          and Share by name, and Highlight marks the line being read and takes the mark off again.

package io.github.skm0078.audora.release

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import io.github.skm0078.audora.R
import io.github.skm0078.audora.battery.OnboardingPreferences
import io.github.skm0078.audora.release.JourneyDevice.app
import io.github.skm0078.audora.release.JourneyDevice.shows

/** [pdf] is the book to open; [backToTabs] leaves any screen for the main tabs, as the journey does. */
internal class LineActionsJourney(
    private val rule: ComposeTestRule,
    private val pdf: String,
    private val backToTabs: () -> Unit,
) {
    private fun text(id: Int): String = app.getString(id)

    private fun waitFor(matcher: SemanticsMatcher, timeoutMs: Long = UI_TIMEOUT_MS) {
        rule.waitUntil(timeoutMs) { rule.shows(matcher) }
    }

    private fun tap(matcher: SemanticsMatcher, timeoutMs: Long = UI_TIMEOUT_MS) {
        waitFor(matcher, timeoutMs)
        rule.onAllNodes(matcher).onFirst().performClick()
    }

    fun namedAndInSight() {
        tipsComeBackFromTheMenu()
        thePlayerNamesItsLineActions()
    }

    /** Opens [pdf] in the reader: ⋮ › Gestures and tips shows the tips card again; the view button has a name. */
    private fun tipsComeBackFromTheMenu() {
        backToTabs()
        // Seen already, so only the menu can bring the card back (and a late first-run card cannot race the check).
        OnboardingPreferences(app).gestureTipsSeen = true
        val activity = requireNotNull(JourneyDevice.resumedActivity()) { "No app on screen" }
        val uri = JourneyDevice.downloadedPdf(pdf)
        JourneyDevice.onMain { activity.startActivity(JourneyDevice.openWith(uri)) }
        waitFor(hasContentDescription(text(R.string.reader_play)), OPEN_TIMEOUT_MS)
        val gotIt = hasText(text(R.string.gesture_tips_got_it)) and hasClickAction()

        tap(hasContentDescription(text(R.string.reader_more)))
        tap(hasText(text(R.string.gesture_tips_menu)))
        waitFor(hasText(text(R.string.gesture_tips_title)))
        tap(gotIt)
        rule.waitUntil(UI_TIMEOUT_MS) { !rule.shows(hasText(text(R.string.gesture_tips_title))) }

        val named = hasText(text(R.string.reader_view_text)) or hasText(text(R.string.reader_view_page))
        rule.waitUntil(UI_TIMEOUT_MS) {
            rule.onAllNodes(named, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        backToTabs()
    }

    /** The mini player opens the full player, whose Highlight marks the line being read and then takes it off. */
    private fun thePlayerNamesItsLineActions() {
        tap(opensThePlayer, OPEN_TIMEOUT_MS)
        listOf(R.string.player_line_bookmark, R.string.player_line_highlight, R.string.reader_share).forEach { label ->
            rule.waitUntil(UI_TIMEOUT_MS) {
                rule.onAllNodes(hasText(text(label)), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
        }
        val highlight = hasContentDescription(text(R.string.sentence_menu_highlight)) and hasClickAction()
        val unhighlight = hasContentDescription(text(R.string.sentence_menu_unhighlight)) and hasClickAction()
        tap(highlight)
        tap(unhighlight)
        waitFor(highlight)
        backToTabs()
    }

    /** The mini player: its tap is named "Open player" for TalkBack. */
    private val opensThePlayer = SemanticsMatcher("opens the player") { node ->
        node.config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label == text(R.string.a11y_open_player)
    }

    private companion object {
        const val UI_TIMEOUT_MS = 15_000L
        const val OPEN_TIMEOUT_MS = 60_000L
    }
}
