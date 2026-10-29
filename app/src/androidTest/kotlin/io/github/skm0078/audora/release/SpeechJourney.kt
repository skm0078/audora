// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/SpeechJourney.kt
// Purpose: Release journey step 51 (ADR 2026-10-29-voice-and-speed-one-level): on the installed app, the player's
//          What to read shortcut opens the Voice and speed sheet with that group already open, and the speed chip
//          opens the same sheet at its top with every group closed, never a sheet over the sheet.

package io.github.skm0078.audora.release

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import io.github.skm0078.audora.R
import io.github.skm0078.audora.release.JourneyDevice.app
import io.github.skm0078.audora.release.JourneyDevice.shows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse

/** [pdf] is the book to open; [backToTabs] leaves any screen for the main tabs, as the journey does. */
internal class SpeechJourney(
    private val rule: ComposeTestRule,
    private val pdf: String,
    private val backToTabs: () -> Unit,
) {
    private fun text(id: Int): String = app.getString(id)

    private fun waitFor(matcher: SemanticsMatcher, timeoutMs: Long = UI_TIMEOUT_MS) {
        rule.waitUntil(timeoutMs) { rule.shows(matcher) }
    }

    private fun tap(matcher: SemanticsMatcher) {
        waitFor(matcher)
        rule.onAllNodes(matcher).onFirst().performClick()
    }

    private fun closeTheSheet(groupLine: SemanticsMatcher) {
        JourneyDevice.pressBackKey()
        rule.waitUntil(UI_TIMEOUT_MS) { !rule.shows(hasText(text(R.string.speech_sound))) }
        assertFalse("The sheet's group stayed open after Back", rule.shows(groupLine))
    }

    /** Opens [pdf] in the player, then the sheet from a shortcut and from the chip. */
    fun shortcutsOpenTheirGroup() {
        backToTabs()
        val activity = requireNotNull(JourneyDevice.resumedActivity()) { "No app on screen" }
        val uri = JourneyDevice.downloadedPdf(pdf)
        JourneyDevice.onMain { activity.startActivity(JourneyDevice.openWith(uri)) }
        waitFor(hasContentDescription(text(R.string.reader_play)), OPEN_TIMEOUT_MS)

        // The shortcuts wait for the first-run tips to be put away.
        val gotIt = hasText(text(R.string.gesture_tips_got_it)) and hasClickAction()
        rule.waitForIdle()
        if (rule.shows(gotIt)) tap(gotIt)

        // A row only What to read holds, shown as soon as its shortcut opens the sheet.
        val readingLine = hasText(text(R.string.reading_skim_short))
        tap(hasText(text(R.string.reading_modes_title)) and hasClickAction())
        waitFor(readingLine)
        val titles = rule.onAllNodes(hasText(text(R.string.speech_sheet_title))).fetchSemanticsNodes().size
        assertEquals("One Voice and speed sheet, not one over another", 1, titles)
        closeTheSheet(readingLine)

        // The chip opens the top: every group closed, What to read's rows behind its header.
        tap(hasContentDescription(text(R.string.speech_sheet_title)) and hasClickAction())
        waitFor(hasText(text(R.string.speech_sound)))
        assertFalse("The chip opened a group", rule.shows(readingLine))
        closeTheSheet(readingLine)
        backToTabs()
    }

    private companion object {
        const val UI_TIMEOUT_MS = 15_000L
        const val OPEN_TIMEOUT_MS = 60_000L
    }
}
