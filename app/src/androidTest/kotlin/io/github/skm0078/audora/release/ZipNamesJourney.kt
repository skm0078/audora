// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/ZipNamesJourney.kt
// Purpose: The release journey's zip-names step on the installed app: an EPUB and a comic whose zips name a file in
//          the PC's code page (Windows' built-in zip, older tools) open instead of crashing the app (ZipNames). Kept
//          apart from ReleaseJourneyTest so neither file grows past detekt's class size.

package io.github.skm0078.audora.release

import android.app.Activity
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import io.github.skm0078.audora.release.JourneyDevice.shows

/** [activity] is the app's screen now; [backToTabs] leaves any screen for the main tabs, as the journey does. */
internal class ZipNamesJourney(
    private val rule: ComposeTestRule,
    private val activity: () -> Activity,
    private val backToTabs: () -> Unit,
) {
    /**
     * assets/ebooks, written by scripts/fixtures/make_zip_name_fixtures.py. The EPUB shows its sentence in the
     * reader; the comic becomes a photo book named after the file, which the reader's top bar shows. Before ZipNames
     * the first crashed the reader and the second the app.
     */
    fun booksWithCodePageNamesOpen() {
        open(EPUB, EPUB_TYPE)
        await("the EPUB with a code-page name to show its sentence") {
            rule.shows(hasText(SENTENCE, substring = true))
        }
        val added = JourneyDevice.logCount(PHOTO_BOOK_ADDED)
        open(COMIC, COMIC_TYPE)
        await("the comic with code-page names to be added as a photo book") {
            JourneyDevice.logCount(PHOTO_BOOK_ADDED) > added
        }
        await("the comic with code-page names to open in the reader") { rule.shows(hasText(COMIC_TITLE)) }
    }

    private fun open(asset: String, type: String) {
        backToTabs()
        val uri = JourneyDevice.downloadedBook(asset)
        val current = activity()
        JourneyDevice.onMain { current.startActivity(JourneyDevice.openWith(uri, type)) }
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
        const val EPUB = "names-cp437.epub"
        const val EPUB_TYPE = "application/epub+zip"
        const val SENTENCE = "A book zipped with names that are not Unicode still opens."
        const val COMIC = "names-cp437.cbz"
        const val COMIC_TYPE = "application/vnd.comicbook+zip"

        // The photo book is named after the file.
        const val COMIC_TITLE = "names-cp437"

        // PhotoBooks' own words once the photo book is in the library.
        const val PHOTO_BOOK_ADDED = "Photo book added"
        const val OPEN_TIMEOUT_MS = 60_000L
        const val LOG_TAIL = 30
    }
}
