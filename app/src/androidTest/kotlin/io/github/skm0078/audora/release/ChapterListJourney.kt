// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/ChapterListJourney.kt
// Purpose: The release journey's chapter-list step on the installed app (ADR 2026-11-15-epub-contents): a scanned
//          book whose contents name only a "Notice", and a book that keeps every chapter in one file, both list their
//          chapters. Kept apart from ReleaseJourneyTest so that file stays under detekt's class size.

package io.github.skm0078.audora.release

import android.app.Activity
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import io.github.skm0078.audora.R
import io.github.skm0078.audora.release.JourneyDevice.app
import io.github.skm0078.audora.release.JourneyDevice.shows

/** [activity] is the app's screen now; [backToTabs] leaves any screen for the main tabs, as the journey does. */
internal class ChapterListJourney(
    private val rule: ComposeTestRule,
    private val activity: () -> Activity,
    private val backToTabs: () -> Unit,
) {
    /**
     * assets/ebooks, written by scripts/fixtures/make_chapter_list_fixtures.py. Before the fix the scanned book's
     * chapter list held only "Notice", and the one-file book's only "Part One".
     */
    fun listChapters() {
        listsChapter(SCANNED, SCANNED_CHAPTER)
        listsChapter(ONE_FILE, ONE_FILE_SECTION)
        backToTabs()
    }

    private fun listsChapter(asset: String, chapter: String) {
        backToTabs()
        val uri = JourneyDevice.downloadedBook(asset)
        val current = activity()
        JourneyDevice.onMain { current.startActivity(JourneyDevice.openWith(uri, EPUB_TYPE)) }
        val chapters = hasContentDescription(app.getString(R.string.chapters_title))
        await("the chapters button of $asset") { rule.shows(chapters) }
        rule.onAllNodes(chapters).onFirst().performClick()
        await("\"$chapter\" in the chapter list of $asset") { rule.shows(hasText(chapter)) }
        JourneyDevice.pressBackKey()
    }

    /** Waits on the Compose test clock; a timeout lists the app's last log lines, which name a failed open. */
    private fun await(what: String, condition: () -> Boolean) {
        try {
            rule.waitUntil(OPEN_TIMEOUT_MS, condition)
        } catch (e: ComposeTimeoutException) {
            throw AssertionError("Timed out waiting for: $what. Last log lines: ${JourneyDevice.logTail(LOG_TAIL)}", e)
        }
    }

    private companion object {
        const val EPUB_TYPE = "application/epub+zip"
        const val SCANNED = "contents-scanned.epub"
        const val SCANNED_CHAPTER = "Chapter 2: The Second Road"
        const val ONE_FILE = "contents-one-file.epub"
        const val ONE_FILE_SECTION = "A Section Inside"
        const val OPEN_TIMEOUT_MS = 60_000L
        const val LOG_TAIL = 30
    }
}
