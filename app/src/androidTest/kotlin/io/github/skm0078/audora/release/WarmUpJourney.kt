// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/WarmUpJourney.kt
// Purpose: The release journey's voice warm-up step on the installed app (ADR 2026-10-29-speed-quick-wins), kept
//          apart from ReleaseJourneyTest so that file stays under detekt's class size.

package io.github.skm0078.audora.release

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.ComposeTestRule
import io.github.skm0078.audora.MainActivity
import io.github.skm0078.audora.R
import io.github.skm0078.audora.release.JourneyDevice.app
import io.github.skm0078.audora.release.JourneyDevice.awaitTrue
import io.github.skm0078.audora.release.JourneyDevice.logCount
import io.github.skm0078.audora.release.JourneyDevice.logTail
import io.github.skm0078.audora.release.JourneyDevice.shows
import io.github.skm0078.audora.ui.reader.MediaControllerPlaybackPort
import org.junit.Assert.assertFalse

/** [activity] is the app on screen now; [port] the journey's own connection to the player. */
internal class WarmUpJourney(
    private val rule: ComposeTestRule,
    private val activity: () -> MainActivity,
    private val port: () -> MediaControllerPlaybackPort?,
) {
    /**
     * With nothing playing, opening [pdf] loads its voice by itself a moment later, and loading it never starts
     * reading: Play is still the listener's to press (C7).
     */
    fun anOpenBookLoadsItsVoiceWithoutPlaying(pdf: String) {
        val player = requireNotNull(port()) { "the journey is connected to the player by now" }
        JourneyDevice.onMain(player::pause)
        awaitTrue("nothing playing") { !player.state.value.wantsToPlay }
        val warmedBefore = logCount(WARMED)

        val current = activity()
        JourneyDevice.onMain { current.startActivity(JourneyDevice.openWith(JourneyDevice.downloadedPdf(pdf))) }
        val pageImage = { page: Int -> hasContentDescription(app.getString(R.string.reader_page_image, page)) }
        awaitTrue("the book open on a page", OPEN_TIMEOUT_MS) {
            (1..MAX_SCANNED_PAGES).any { rule.shows(pageImage(it)) }
        }
        val recentLog = { logTail(LOG_LINES).joinToString("\n") }
        awaitTrue("the book's voice loaded without Play", WARM_TIMEOUT_MS, recentLog) {
            logCount(WARMED) > warmedBefore
        }
        assertFalse("Loading the voice must not start reading", player.state.value.wantsToPlay)
        JourneyDevice.pressBackKey()
    }

    private companion object {
        /** VoiceWarmUp's log line once the model is loaded. */
        const val WARMED = "Warmed voice="
        const val OPEN_TIMEOUT_MS = 60_000L

        /**
         * Counted from the page image: the page's text is read first (seconds on the CI emulator), then the reader's
         * one-second wait, then loading the model.
         */
        const val WARM_TIMEOUT_MS = 60_000L
        const val MAX_SCANNED_PAGES = 40
        const val LOG_LINES = 60
    }
}
