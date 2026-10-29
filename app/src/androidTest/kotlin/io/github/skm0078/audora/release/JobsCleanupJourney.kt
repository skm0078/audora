// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/JobsCleanupJourney.kt
// Purpose: Release journey step 55 (ADR 2026-10-29-jobs-card-and-leftovers): a half download nothing resumes shows
//          on the Jobs screen as unfinished work, Clean up now deletes it, and the screen says what went.

package io.github.skm0078.audora.release

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import io.github.skm0078.audora.R
import io.github.skm0078.audora.release.JourneyDevice.app
import io.github.skm0078.audora.release.JourneyDevice.awaitTrue
import io.github.skm0078.audora.release.JourneyDevice.shows
import io.github.skm0078.audora.speech.download.DownloadJobs
import io.github.skm0078.audora.speech.download.DownloadLeftovers
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/** [backToTabs] leaves any screen for the main tabs, as the journey does. */
internal class JobsCleanupJourney(private val rule: ComposeTestRule, private val backToTabs: () -> Unit) {
    private fun text(id: Int, vararg args: Any): String = app.getString(id, *args)

    private fun node(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        rule.waitUntil(UI_TIMEOUT_MS) { rule.shows(matcher) }
        return rule.onAllNodes(matcher).onFirst()
    }

    /**
     * The Jobs list is lazy: the Unfinished work card, last, is not composed until scrolled to, and appears once
     * measured. Scrolls the list to [matcher] until it is there.
     */
    private fun scrolledTo(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        rule.waitUntil(UI_TIMEOUT_MS) {
            runCatching { rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(matcher) }.isSuccess
        }
        return node(matcher)
    }

    /**
     * A fresh half download is kept (start-up's clean-up only takes week-old ones), so the Jobs screen offers it;
     * Clean up now asks first, then deletes it and says nothing is left. Downloads still running are never
     * touched, so the step waits for earlier steps' downloads to settle first.
     */
    fun cleansUpUnfinishedWork() {
        awaitTrue("no download queued or running", DOWNLOAD_SETTLE_MS) { !DownloadJobs.anyActive(app) }
        val half = File(app.noBackupFilesDir, "journey/${DownloadLeftovers.DOWNLOADS}/journey-model.partial").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(HALF_BYTES))
        }
        try {
            backToTabs()
            node(hasText(text(R.string.tab_made))).performClick()
            node(hasText(text(R.string.made_jobs_short))).performClick()
            scrolledTo(hasText(text(R.string.jobs_leftovers_section)))
            assertTrue("a fresh half download is kept for a resume", half.exists())

            val clean = text(R.string.jobs_leftovers_clean)
            scrolledTo(hasText(clean)).performClick()
            node(hasText(clean) and hasAnyAncestor(isDialog())).performClick()

            scrolledTo(hasText(text(R.string.jobs_leftovers_nothing)))
            assertFalse("Clean up now deleted the half download", half.exists())
        } finally {
            half.parentFile?.parentFile?.deleteRecursively()
            backToTabs()
        }
    }

    private companion object {
        const val UI_TIMEOUT_MS = 15_000L
        const val DOWNLOAD_SETTLE_MS = 120_000L
        const val HALF_BYTES = 64 * 1024
    }
}
