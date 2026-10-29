// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/ShareHereJourney.kt
// Purpose: The release journey's step for the reader's Share button (ADR 2026-10-29-share-credit-and-link): it opens
//          the Share choices, "Choose more lines" shows its bar, and a quote card opens in the reader's colours.

package io.github.skm0078.audora.release

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.skm0078.audora.MainActivity
import io.github.skm0078.audora.R
import io.github.skm0078.audora.release.JourneyDevice.app
import io.github.skm0078.audora.release.JourneyDevice.awaitTrue
import io.github.skm0078.audora.release.JourneyDevice.shows
import io.github.skm0078.audora.ui.reader.PageTone
import io.github.skm0078.audora.ui.reader.ReadingLookPreferences
import io.github.skm0078.audora.ui.share.ShareCardActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** [activity] is the app on screen now. */
internal class ShareHereJourney(private val rule: ComposeTestRule, private val activity: () -> MainActivity) {
    private fun text(id: Int, vararg args: Any): String = app.getString(id, *args)

    private fun node(matcher: SemanticsMatcher, timeoutMs: Long = UI_TIMEOUT_MS): SemanticsNodeInteraction {
        rule.waitUntil(timeoutMs) { rule.shows(matcher) }
        return rule.onAllNodes(matcher).onFirst()
    }

    /**
     * [pdf] opened (Open with, as the step before may end anywhere) with sepia pages: the top bar's Share opens the
     * Share choices with "Choose more lines", which shows its bar until Cancel; Share again, then Share as a picture,
     * opens the card screen on the Sepia background. The look is put back as it was.
     */
    fun theShareButtonOpensTheChoices(pdf: String) {
        val looks = ReadingLookPreferences(app)
        val before = runBlocking { looks.look.first() }
        runBlocking { looks.save(before.copy(tone = PageTone.SEPIA)) }
        try {
            val uri = JourneyDevice.downloadedPdf(pdf)
            val current = activity()
            JourneyDevice.onMain { current.startActivity(JourneyDevice.openWith(uri)) }
            val pageImage = hasContentDescription(text(R.string.reader_page_image, 1).substringAfter("1"), true)
            node(pageImage, OPEN_TIMEOUT_MS)
            val share = hasContentDescription(text(R.string.reader_share)) and hasClickAction()

            node(share).performClick()
            node(hasText(text(R.string.share_choices_title)))
            node(hasText(text(R.string.share_more_lines))).performScrollTo().performClick()
            val bar = hasText(text(R.string.share_range_prompt, 1).substringBefore("1"), substring = true)
            node(bar)
            node(hasText(text(R.string.password_cancel)) and hasClickAction()).performClick()
            rule.waitUntil(UI_TIMEOUT_MS) { !rule.shows(bar) }

            node(share).performClick()
            node(hasText(text(R.string.share_as_picture))).performScrollTo().performClick()
            awaitTrue("the card screen") { JourneyDevice.resumedActivity() is ShareCardActivity }
            node(hasContentDescription(text(R.string.share_card_look_sepia)) and isSelected(), OPEN_TIMEOUT_MS)
        } finally {
            runBlocking { looks.save(before) }
            repeat(MAX_BACKS) {
                if (JourneyDevice.resumedActivity() is MainActivity) return@repeat
                JourneyDevice.pressBackKey()
                Thread.sleep(POLL_MS)
            }
        }
    }

    private companion object {
        const val UI_TIMEOUT_MS = 15_000L
        const val OPEN_TIMEOUT_MS = 60_000L
        const val MAX_BACKS = 3
        const val POLL_MS = 200L
    }
}
