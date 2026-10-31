// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/PhotoBookJourney.kt
// Purpose: The release journey's photo-book steps on the installed app: a comic archive and a DjVu scan become photo
//          books that open in the reader by themselves, and a DjVu with a text layer opens as a text book. Kept apart
//          from ReleaseJourneyTest so neither file grows past detekt's class size.

package io.github.skm0078.audora.release

import android.app.Activity
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import io.github.skm0078.audora.release.JourneyDevice.shows

/** [activity] is the app's screen now; [backToTabs] leaves any screen for the main tabs, as the journey does. */
internal class PhotoBookJourney(
    private val rule: ComposeTestRule,
    private val activity: () -> Activity,
    private val backToTabs: () -> Unit,
) {
    /**
     * A comic archive is not added as a book: its pages become a photo book, added to the library, which opens in the
     * reader by itself. Its pages are blank pictures; OCR reading a picture page is step 12's. The library's work runs
     * in the screen's scope, which the Compose test clock drives, so these waits go through [awaitSynced].
     */
    fun aComicBecomesAPhotoBook() {
        backToTabs()
        val added = JourneyDevice.logCount(PHOTO_BOOK_ADDED)
        val uri = JourneyDevice.downloadedBook(COMIC)
        val current = activity()
        JourneyDevice.onMain { current.startActivity(JourneyDevice.openWith(uri, COMIC_TYPE)) }
        awaitSynced("the comic to be added as a photo book") { JourneyDevice.logCount(PHOTO_BOOK_ADDED) > added }
        awaitSynced("the comic to open in the reader") { rule.shows(hasText(COMIC_TITLE)) }
    }

    /**
     * DjVu through DjVuLibre (ADR 2026-10-27-djvu-books): one with a text layer opens as a text book showing its first
     * paragraph, joined across lines and a broken word; a scan (no text layer) becomes a photo book that opens by
     * itself, like a comic.
     */
    fun djvuTextAndScanOpen() {
        backToTabs()
        val textUri = JourneyDevice.downloadedBook(DJVU_TEXT)
        val textHost = activity()
        JourneyDevice.onMain { textHost.startActivity(JourneyDevice.openWith(textUri, DJVU_TYPE)) }
        awaitSynced("$DJVU_TEXT to show \"$DJVU_SENTENCE\" in the reader") {
            rule.shows(hasText(DJVU_SENTENCE, substring = true))
        }
        backToTabs()
        val added = JourneyDevice.logCount(PHOTO_BOOK_ADDED)
        val scanUri = JourneyDevice.downloadedBook(DJVU_SCAN)
        val scanHost = activity()
        JourneyDevice.onMain { scanHost.startActivity(JourneyDevice.openWith(scanUri, DJVU_TYPE)) }
        awaitSynced("the DjVu scan to be added as a photo book") { JourneyDevice.logCount(PHOTO_BOOK_ADDED) > added }
        awaitSynced("the DjVu scan to open in the reader") { rule.shows(hasText(DJVU_SCAN_TITLE)) }
    }

    /** Waits on the Compose test clock (frames and effects run meanwhile); a timeout lists the app's last log lines. */
    private fun awaitSynced(what: String, condition: () -> Boolean) {
        try {
            rule.waitUntil(OPEN_TIMEOUT_MS, condition)
        } catch (e: ComposeTimeoutException) {
            throw AssertionError("Timed out waiting for: $what. Last log lines: ${JourneyDevice.logTail(LOG_TAIL)}", e)
        }
    }

    private companion object {
        const val COMIC = "comic.cbz"
        const val COMIC_TYPE = "application/vnd.comicbook+zip"

        // The photo book is named after the file; the reader's top bar shows it.
        const val COMIC_TITLE = "comic"

        // assets/ebooks, written by scripts/fixtures/make_djvu_fixtures.py: its first paragraph has "remark-" / "able"
        // across a line end, so the sentence shows only when the lines are joined.
        const val DJVU_TEXT = "text.djvu"
        const val DJVU_SCAN = "scan.djvu"
        const val DJVU_TYPE = "image/vnd.djvu"
        const val DJVU_SENTENCE = "The first page of a small DjVu book has a remarkable sentence"
        const val DJVU_SCAN_TITLE = "scan"

        // PhotoBooks' own words once the photo book is in the library.
        const val PHOTO_BOOK_ADDED = "Photo book added"
        const val LOG_TAIL = 30
        const val OPEN_TIMEOUT_MS = 60_000L
    }
}
