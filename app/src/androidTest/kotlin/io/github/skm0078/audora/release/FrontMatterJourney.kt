// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/FrontMatterJourney.kt
// Purpose: The release journey's skip-front-matter step on the installed app (ADR 2026-10-31-skip-front-matter): a
//          new book opens at its first chapter with a card saying what was skipped, and "Read from the start" goes
//          back to page 1. Kept apart from ReleaseJourneyTest so that file stays under detekt's class size.

package io.github.skm0078.audora.release

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import io.github.skm0078.audora.R
import io.github.skm0078.audora.release.JourneyDevice.app
import io.github.skm0078.audora.release.JourneyDevice.awaitTrue
import io.github.skm0078.audora.release.JourneyDevice.hides
import io.github.skm0078.audora.release.JourneyDevice.logCount
import io.github.skm0078.audora.release.JourneyDevice.shows

/** [backToTabs] leaves any screen for the main tabs, as the journey does. */
internal class FrontMatterJourney(private val rule: ComposeTestRule, private val backToTabs: () -> Unit) {
    /**
     * assets/pdfs/en-front-matter.pdf, never opened before: its title, copyright, contents and preface pages come
     * before "Chapter 1: The Light" on page 6. The card names the chapter and the page; Read from the start takes the
     * card away and goes to page 1, and the log says the book will open there from now on.
     */
    fun opensAtChapterOne() {
        backToTabs()
        val skippedBefore = logCount(SKIPPED)
        val current = requireNotNull(JourneyDevice.resumedActivity()) { "No app on screen" }
        JourneyDevice.onMain { current.startActivity(JourneyDevice.openWith(JourneyDevice.downloadedPdf(PDF))) }
        val card = hasText(app.getString(R.string.front_matter_started_at, CHAPTER, CHAPTER_PAGE))
        awaitTrue("the card saying the book opened at Chapter 1", OPEN_TIMEOUT_MS) { rule.shows(card) }
        awaitTrue("the reader's log line for the skip") { logCount(SKIPPED) > skippedBefore }

        val fromStartBefore = logCount(FROM_START)
        val readFromStart = hasText(app.getString(R.string.front_matter_read_from_start))
        rule.onAllNodes(readFromStart).onFirst().performClick()
        awaitTrue("the card gone after Read from the start") { rule.hides(card) }
        awaitTrue("the reader going back to page 1") { logCount(FROM_START) > fromStartBefore }
        backToTabs()
    }

    private companion object {
        const val PDF = "en-front-matter.pdf"
        const val CHAPTER = "Chapter 1: The Light"
        const val CHAPTER_PAGE = 6

        // ReaderModel's own log lines.
        const val SKIPPED = "Opening past front matter"
        const val FROM_START = "Read from the start"
        const val OPEN_TIMEOUT_MS = 60_000L
    }
}
