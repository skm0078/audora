// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/MissingFileJourney.kt
// Purpose: The release journey's file-check step on the installed app (ADR 2026-10-31-lighter-file-checks): a book
//          whose file is deleted while the library is away says so when the library shows again, through the batched
//          check. Kept apart from ReleaseJourneyTest so neither file grows past detekt's class size.

package io.github.skm0078.audora.release

import android.net.Uri
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.di.LibraryEntryPoint
import io.github.skm0078.audora.playback.documents.SharedPdfCopies
import io.github.skm0078.audora.release.JourneyDevice.app
import io.github.skm0078.audora.release.JourneyDevice.shows
import kotlinx.coroutines.runBlocking
import org.junit.Assert.fail

/** [backToTabs] leaves any screen for the main tabs, as the journey does. */
internal class MissingFileJourney(private val rule: ComposeTestRule, private val backToTabs: () -> Unit) {
    private fun text(id: Int): String = app.getString(id)

    private fun tap(matcher: SemanticsMatcher) {
        rule.waitUntil(UI_TIMEOUT_MS) { rule.shows(matcher) }
        rule.onAllNodes(matcher).onFirst().performClick()
    }

    /** The library tab, narrowed by its search to this step's book (the list composes only the rows in view). */
    private fun showTheBook() {
        tap(hasText(text(R.string.tab_library)) and hasClickAction())
        if (!rule.shows(hasSetTextAction())) {
            tap(hasContentDescription(text(R.string.library_search)))
            rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput(TITLE)
        }
        rule.waitUntil(UI_TIMEOUT_MS) { rule.shows(says(TITLE) and hasClickAction()) }
    }

    /**
     * A list row shows [words] as text; a grid tile folds its lines into its description (BookTile), so both count.
     * Not the search field's own text, which also holds the title.
     */
    private fun says(words: String): SemanticsMatcher =
        (hasText(words, substring = true) and !hasSetTextAction()) or hasContentDescription(words, substring = true)

    /**
     * A book of its own is added and shown in the library, with no warning. Its file is deleted while another tab is
     * open, and when the library shows again (the same check a return from another app runs) its row says the file
     * has gone. The book is removed at the end, also when a check fails.
     */
    fun flagsAGoneFile() {
        val library = LibraryEntryPoint.of(app).library()
        val file = SharedPdfCopies(app).newCopy("$TITLE.txt")
        file.writeText("The book whose file goes away while the library is not on screen.\n")
        val id: BookId = when (val added = runBlocking { library.add(Uri.fromFile(file).toString()) }) {
            is Outcome.Success -> added.value.book.id
            is Outcome.Failure -> return fail("The book was not added: ${added.error}")
        }
        val flagged = says(text(R.string.library_file_missing))
        try {
            backToTabs()
            showTheBook()
            check(!rule.shows(flagged)) { "The book's file was flagged before it was deleted" }

            tap(hasText(text(R.string.tab_you)) and hasClickAction())
            check(file.delete()) { "The book's file could not be deleted" }
            val before = JourneyDevice.logCount(UNAVAILABLE_LOG)
            showTheBook()

            runCatching { rule.waitUntil(UI_TIMEOUT_MS) { rule.shows(flagged) } }.onFailure {
                val checked = JourneyDevice.logCount(UNAVAILABLE_LOG) - before
                throw AssertionError("No \"file missing\" on the row; the library logged $checked missing file(s)", it)
            }
        } finally {
            runBlocking { library.remove(id) }
            repeat(MAX_BACKS) { if (rule.shows(hasSetTextAction())) JourneyDevice.pressBackKey() }
            backToTabs()
        }
    }

    private companion object {
        const val UI_TIMEOUT_MS = 15_000L
        const val MAX_BACKS = 3

        /** Library.availability's log line for a file it cannot reach. */
        const val UNAVAILABLE_LOG = "Book file unavailable"

        /** One word no other journey book has in its title, so the search shows this book alone. */
        const val TITLE = "Vanishing"
    }
}
