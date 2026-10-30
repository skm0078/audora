// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/WarmUpJourney.kt
// Purpose: The release journey's voice warm-up step on the installed app (ADR 2026-10-29-speed-quick-wins), kept
//          apart from ReleaseJourneyTest so that file stays under detekt's class size.

package io.github.skm0078.audora.release

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.ComposeTestRule
import io.github.skm0078.audora.R
import io.github.skm0078.audora.release.JourneyDevice.app
import io.github.skm0078.audora.release.JourneyDevice.awaitTrue
import io.github.skm0078.audora.release.JourneyDevice.logCount
import io.github.skm0078.audora.release.JourneyDevice.logTail
import io.github.skm0078.audora.release.JourneyDevice.shows
import io.github.skm0078.audora.ui.reader.MediaControllerPlaybackPort
import org.junit.Assert.assertFalse

/** [port] is the journey's own connection to the player; [backToTabs] leaves whatever screen is open. */
internal class WarmUpJourney(
    private val rule: ComposeTestRule,
    private val port: () -> MediaControllerPlaybackPort?,
    private val backToTabs: () -> Unit,
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

        // Opened from the tabs as the other reader steps do, so this is a reader of its own, not one left open.
        backToTabs()
        val current = requireNotNull(JourneyDevice.resumedActivity()) { "No app on screen" }
        JourneyDevice.onMain { current.startActivity(JourneyDevice.openWith(JourneyDevice.downloadedPdf(pdf))) }
        awaitTrue("the book open", OPEN_TIMEOUT_MS) {
            rule.shows(hasContentDescription(app.getString(R.string.reader_play)))
        }
        // The test runner cuts a long failure message, so only the warm-up's own lines, newest last.
        val recentLog = {
            logTail(LOG_LINES).filter { line -> WARM_UP_WORDS.any { it in line } }
                .takeLast(SHOWN_LINES)
                .joinToString("\n")
        }
        awaitTrue("the book's voice loaded without Play", WARM_TIMEOUT_MS, recentLog) {
            // The reader's one-second wait runs on the test's Compose clock, which moves only while the rule syncs.
            rule.waitForIdle()
            logCount(WARMED) > warmedBefore
        }
        assertFalse("Loading the voice must not start reading", player.state.value.wantsToPlay)
        backToTabs()
    }

    private companion object {
        /** VoiceWarmUp's log line once the model is loaded. */
        const val WARMED = "Warmed voice="
        const val OPEN_TIMEOUT_MS = 60_000L

        /**
         * Counted from the Play button: the page's text is read first (seconds on the CI emulator), then the reader's
         * one-second wait, then loading the model.
         */
        const val WARM_TIMEOUT_MS = 60_000L
        const val LOG_LINES = 200
        const val SHOWN_LINES = 15
        val WARM_UP_WORDS = listOf("arm", "load the voice", "Voice to load", "refused", "Ignored", "Opened", "connect")
    }
}
