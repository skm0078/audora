// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/OpenInPlaceJourney.kt
// Purpose: Release journey step (phone test 2026-10-30): on the installed app, a Voice and speed group opened low in
//          the sheet scrolls its options into view instead of opening them below the sheet's edge.

package io.github.skm0078.audora.release

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.skm0078.audora.R
import io.github.skm0078.audora.battery.OnboardingPreferences
import io.github.skm0078.audora.release.JourneyDevice.app
import io.github.skm0078.audora.release.JourneyDevice.shows

/** [pdf] is the book to open; [backToTabs] leaves any screen for the main tabs, as the journey does. */
internal class OpenInPlaceJourney(
    private val rule: ComposeTestRule,
    private val pdf: String,
    private val backToTabs: () -> Unit,
) {
    private fun text(id: Int): String = app.getString(id)

    private fun waitFor(matcher: SemanticsMatcher, timeoutMs: Long = UI_TIMEOUT_MS) {
        rule.waitUntil(timeoutMs) { rule.shows(matcher) }
    }

    /** Opens [pdf], the sheet from the speed chip, then Study, the last group but one, from just in view. */
    fun anOpenedGroupComesIntoView() {
        backToTabs()
        // Tips marked seen first, so they never cover the chip (they load off the main thread after the reader opens).
        OnboardingPreferences(app).gestureTipsSeen = true
        val activity = requireNotNull(JourneyDevice.resumedActivity()) { "No app on screen" }
        val uri = JourneyDevice.downloadedPdf(pdf)
        JourneyDevice.onMain { activity.startActivity(JourneyDevice.openWith(uri)) }
        waitFor(hasContentDescription(text(R.string.reader_play)), OPEN_TIMEOUT_MS)

        val chip = hasContentDescription(text(R.string.speech_sheet_title)) and hasClickAction()
        waitFor(chip)
        rule.onAllNodes(chip).onFirst().performClick()
        val study = hasText(text(R.string.study_title)) and hasClickAction()
        waitFor(study)
        rule.onNode(study).performScrollTo().performClick()

        val firstOption = hasText(text(R.string.study_repeats))
        waitFor(firstOption)
        rule.waitForIdle()
        rule.onNode(firstOption).assertIsDisplayed()

        JourneyDevice.pressBackKey()
        rule.waitUntil(UI_TIMEOUT_MS) { !rule.shows(firstOption) }
        backToTabs()
    }

    private companion object {
        const val UI_TIMEOUT_MS = 15_000L
        const val OPEN_TIMEOUT_MS = 60_000L
    }
}
