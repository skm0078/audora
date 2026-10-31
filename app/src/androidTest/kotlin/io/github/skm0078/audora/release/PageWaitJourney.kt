// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/PageWaitJourney.kt
// Purpose: The release journey's honest page wait step on the installed app (ADR 2026-10-31-honest-page-wait),
//          kept apart from ReleaseJourneyTest so that file stays under detekt's class size.

package io.github.skm0078.audora.release

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import io.github.skm0078.audora.R
import io.github.skm0078.audora.release.JourneyDevice.app
import io.github.skm0078.audora.release.JourneyDevice.awaitTrue
import io.github.skm0078.audora.release.JourneyDevice.logCount
import io.github.skm0078.audora.release.JourneyDevice.logTail
import io.github.skm0078.audora.release.JourneyDevice.shows
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/** [pdf] is a book with a text layer; [backToTabs] leaves any screen. */
internal class PageWaitJourney(
    private val rule: ComposeTestRule,
    private val pdf: String,
    private val backToTabs: () -> Unit,
) {
    /**
     * A text page never claims to be a scan, with the OCR models installed (step 12): while it loads, however long
     * that takes, the reader never shows the OCR line, and the reader's log of the page says it came from the text
     * layer and spent no time in OCR.
     */
    fun textPageNeverSaysOcr() {
        backToTabs()
        val readBefore = logCount(PAGE_WAIT)
        val current = requireNotNull(JourneyDevice.resumedActivity()) { "No app on screen" }
        JourneyDevice.onMain { current.startActivity(JourneyDevice.openWith(JourneyDevice.downloadedPdf(pdf))) }
        val ocrLine = hasText(app.getString(R.string.reader_page_slow_ocr))
        var claimedOcr = false
        awaitTrue("the reader to read the text page", OPEN_TIMEOUT_MS, ::recentPageLines) {
            // The notice waits on Compose's clock, which moves only with frames: moved, so a slow page shows its line.
            rule.mainClock.advanceTimeBy(CLOCK_STEP_MS)
            claimedOcr = claimedOcr || rule.shows(ocrLine)
            logCount(PAGE_WAIT) > readBefore
        }
        rule.mainClock.advanceTimeBy(CLOCK_STEP_MS)
        assertFalse("A text page said it was being read with OCR", claimedOcr || rule.shows(ocrLine))
        val read = logTail(LOG_LINES).filter { PAGE_WAIT in it }.takeLast(logCount(PAGE_WAIT) - readBefore)
        assertTrue("The page read was not a text page: $read", read.all { "origin=TEXT" in it })
        assertTrue("A text page spent time in OCR: $read", read.all { "ocrMs=0 " in it })
        backToTabs()
    }

    private fun recentPageLines(): String =
        logTail(LOG_LINES).filter { PAGE_WAIT in it }.takeLast(SHOWN_LINES).joinToString("\n")

    private companion object {
        /** In ReaderModel's log line for every page it reads ("Page read," or "Slow page,"). */
        const val PAGE_WAIT = "waitMs="
        const val OPEN_TIMEOUT_MS = 60_000L
        const val CLOCK_STEP_MS = 250L
        const val LOG_LINES = 400
        const val SHOWN_LINES = 5
    }
}
