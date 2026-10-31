// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/MissingFileJourney.kt
// Purpose: The release journey's file-check step on the installed app (ADR 2026-10-31-lighter-file-checks): a book
//          whose file is deleted while the app is in the background says so when the app comes back, through the
//          batched check. Kept apart from ReleaseJourneyTest so neither file grows past detekt's class size.

package io.github.skm0078.audora.release

import android.net.Uri
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import io.github.skm0078.audora.MainActivity
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.di.LibraryEntryPoint
import io.github.skm0078.audora.playback.documents.SharedPdfCopies
import io.github.skm0078.audora.release.JourneyDevice.app
import io.github.skm0078.audora.release.JourneyDevice.shows
import kotlinx.coroutines.runBlocking
import org.junit.Assert.fail

/** [scenario] is the journey's running app; [backToTabs] leaves any screen for the main tabs, as the journey does. */
internal class MissingFileJourney(
    private val rule: ComposeTestRule,
    private val scenario: () -> ActivityScenario<MainActivity>?,
    private val backToTabs: () -> Unit,
) {
    private fun text(id: Int): String = app.getString(id)

    private fun tap(matcher: SemanticsMatcher) {
        rule.waitUntil(UI_TIMEOUT_MS) { rule.shows(matcher) }
        rule.onAllNodes(matcher).onFirst().performClick()
    }

    /** Scrolls the library's list until [matcher] is on screen (the list composes only the rows in view). */
    private fun scrollTo(matcher: SemanticsMatcher) {
        val list = hasScrollToIndexAction()
        rule.waitUntil(UI_TIMEOUT_MS) { rule.shows(list) }
        rule.onAllNodes(list).onFirst().performScrollToNode(matcher)
        rule.waitUntil(UI_TIMEOUT_MS) { rule.shows(matcher) }
    }

    /**
     * A book of its own is added and shown in the library's list, with no warning. Its file is deleted, the app goes
     * to the background and comes back (pause and resume, as switching apps does), and the row says the file has gone.
     * The book is removed at the end, also when a check fails.
     */
    fun flagsAGoneFile() {
        val library = LibraryEntryPoint.of(app).library()
        val file = SharedPdfCopies(app).newCopy("$TITLE.txt")
        file.writeText("The book whose file goes away while the app is in the background.\n")
        val id: BookId = when (val added = runBlocking { library.add(Uri.fromFile(file).toString()) }) {
            is Outcome.Success -> added.value.book.id
            is Outcome.Failure -> return fail("The book was not added: ${added.error}")
        }
        try {
            backToTabs()
            tap(hasText(text(R.string.tab_library)) and hasClickAction())
            // The library's own list, not the app's search: only the library screen re-checks files on its return.
            val row = hasText(TITLE, substring = true) and hasClickAction()
            scrollTo(row)
            val flagged = row and hasText(text(R.string.library_file_missing), substring = true)
            check(!rule.shows(flagged)) { "The book's file was flagged before it was deleted" }

            check(file.delete()) { "The book's file could not be deleted" }
            val running = checkNotNull(scenario()) { "The journey has no running app" }
            running.moveToState(Lifecycle.State.STARTED)
            running.moveToState(Lifecycle.State.RESUMED)

            scrollTo(row)
            rule.waitUntil(UI_TIMEOUT_MS) { rule.shows(flagged) }
        } finally {
            runBlocking { library.remove(id) }
            backToTabs()
        }
    }

    private companion object {
        const val UI_TIMEOUT_MS = 15_000L

        /** One word no other journey book has in its title, so the row found is this book's. */
        const val TITLE = "Vanishing"
    }
}
