// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/PlayerServiceJourney.kt
// Purpose: The release journey's check that a cold start leaves the playback service down until something needs it
//          (ADR 2026-10-30-lazy-player-service), kept apart from ReleaseJourneyTest so that file stays under detekt's
//          class size.

package io.github.skm0078.audora.release

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.core.app.ActivityScenario
import io.github.skm0078.audora.MainActivity
import io.github.skm0078.audora.R
import io.github.skm0078.audora.playback.contract.PlaybackServiceLife
import io.github.skm0078.audora.release.JourneyDevice.app
import io.github.skm0078.audora.release.JourneyDevice.shows
import io.github.skm0078.audora.ui.firstrun.FirstRunStep
import org.junit.Assert.assertFalse

/** [launched] receives the app's scenario, which the journey closes at its end. */
internal class PlayerServiceJourney(
    private val rule: ComposeTestRule,
    private val launched: (ActivityScenario<MainActivity>) -> Unit,
) {
    /**
     * Starts the app. On the first screen of a fresh install nothing plays, so nothing starts the player: the mini
     * player and the lock's saver wait for the service instead of starting it. Watched for a while: a connection is
     * asynchronous.
     */
    fun launchesDown() {
        launched(ActivityScenario.launch(MainActivity::class.java))
        val stepOne = hasText(app.getString(R.string.first_run_step, 1, FirstRunStep.entries.size))
        rule.waitUntil(SCREEN_TIMEOUT_MS) { rule.shows(stepOne) }
        repeat(WATCH_MS / POLL_MS) {
            assertFalse("The playback service started with nothing to play", PlaybackServiceLife.up.value)
            Thread.sleep(POLL_MS.toLong())
        }
    }

    private companion object {
        const val SCREEN_TIMEOUT_MS = 30_000L
        const val WATCH_MS = 3_000
        const val POLL_MS = 100
    }
}
