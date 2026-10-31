// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/MissingFileJourney.kt
// Purpose: The release journey's file-check step on the installed app (ADR 2026-10-31-lighter-file-checks): a book
//          whose file is deleted while the app is in the background says so when the app comes back, through the
//          batched check. Kept apart from ReleaseJourneyTest so neither file grows past detekt's class size.

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

    /**
     * A book of its own is added and found by a search, with no warning. Its file is deleted, the app goes to the
     * background and comes back (pause and resume, as switching apps does), and the row says the file has gone. The
     * book is removed at the end, also when a check fails.
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
            tap(hasContentDescription(text(R.string.library_search)))
            rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput(TITLE)
            val row = hasText(TITLE, substring = true) or hasContentDescription(TITLE, substring = true)
            rule.waitUntil(UI_TIMEOUT_MS) { rule.shows(row) }
            val missing = hasText(text(R.string.library_file_missing), substring = true)
            check(!rule.shows(missing)) { "The book's file was flagged before it was deleted" }

            check(file.delete()) { "The book's file could not be deleted" }
            val running = checkNotNull(scenario()) { "The journey has no running app" }
            running.moveToState(Lifecycle.State.STARTED)
            running.moveToState(Lifecycle.State.RESUMED)

            rule.waitUntil(UI_TIMEOUT_MS) { rule.shows(missing) }
        } finally {
            runBlocking { library.remove(id) }
            repeat(MAX_BACKS) { if (rule.shows(hasSetTextAction())) JourneyDevice.pressBackKey() }
            backToTabs()
        }
    }

    private companion object {
        const val UI_TIMEOUT_MS = 15_000L
        const val MAX_BACKS = 3

        /** One word no other journey book has in its title, so the search shows this book alone. */
        const val TITLE = "Vanishing"
    }
}
