// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/LoadJourney.kt
// Purpose: The release journey's big-library step on the installed app (ADR 2026-10-28-performance-budget): a
//          thousand books are added, the library's search finds the last one inside LibraryLoadBudget, and the books
//          are removed again so later steps see the library as it was. Kept apart from ReleaseJourneyTest so neither
//          file grows past detekt's class size.

package io.github.skm0078.audora.release

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.LibraryLoadBudget
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.Library
import io.github.skm0078.audora.core.library.SyntheticLibrary
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.di.LibraryEntryPoint
import io.github.skm0078.audora.playback.documents.SharedPdfCopies
import io.github.skm0078.audora.release.JourneyDevice.app
import io.github.skm0078.audora.release.JourneyDevice.shows
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.fail

/** [backToTabs] leaves any screen for the main tabs, as the journey does. */
internal class LoadJourney(private val rule: ComposeTestRule, private val backToTabs: () -> Unit) {
    private val budget = LibraryLoadBudget()

    private fun text(id: Int): String = app.getString(id)

    private fun node(matcher: SemanticsMatcher, timeoutMs: Long = UI_TIMEOUT_MS): SemanticsNodeInteraction {
        rule.waitUntil(timeoutMs) { rule.shows(matcher) }
        return rule.onAllNodes(matcher).onFirst()
    }

    /**
     * [LibraryLoadBudget.journeyBooks] small text books, each its own app-owned copy as a shared book is, added through
     * the real library one at a time (as a folder of books arrives). The library shows them, a search for the last
     * one's title shows it within [LibraryLoadBudget.journeySearchMs], and every seeded book is removed at the end,
     * also when a check fails.
     */
    fun lastOfManyIsFound() {
        val library = LibraryEntryPoint.of(app).library()
        val seeded = mutableListOf<BookId>()
        try {
            val addMs = timed { seed(library, budget.journeyBooks) { seeded += it } }
            backToTabs()
            node(hasText(text(R.string.tab_library))).performClick()
            node(hasText(text(R.string.library_title)))
            val last = titleOf(budget.journeyBooks - 1)
            node(hasContentDescription(text(R.string.library_search))).performClick()
            node(hasSetTextAction()).performTextInput(last)
            val found = (hasContentDescription(last, substring = true) or hasText(last, substring = true)) and
                hasClickAction()
            val searchMs = timed { node(found, budget.journeySearchMs) }
            Log.i(TAG, "Journey books=${budget.journeyBooks} addAllMs=$addMs searchShowsMs=$searchMs")
            assertTrue(
                "The search took $searchMs ms (budget ${budget.journeySearchMs} ms)",
                searchMs <= budget.journeySearchMs,
            )
            closeSearch()
        } finally {
            runBlocking { seeded.forEach { library.remove(it) } }
        }
    }

    private fun seed(library: Library, count: Int, added: (BookId) -> Unit) {
        val copies = SharedPdfCopies(app)
        repeat(count) { i ->
            val file = copies.newCopy("${titleOf(i)}.txt")
            // Its own words, so each book has its own fingerprint.
            file.writeText("Book ${i + 1} of the big-library check. It has one sentence.\n")
            when (val result = runBlocking { library.add(Uri.fromFile(file).toString()) }) {
                is Outcome.Success -> added(result.value.book.id)
                is Outcome.Failure -> fail("Book ${i + 1} was not added: ${result.error}")
            }
        }
    }

    /** Back closes the search; the library's title shows again. */
    private fun closeSearch() {
        repeat(MAX_BACKS) {
            if (!rule.shows(hasSetTextAction())) return@repeat
            JourneyDevice.pressBackKey()
            rule.waitForIdle()
        }
        node(hasText(text(R.string.library_title)))
    }

    private inline fun timed(block: () -> Unit): Long {
        val start = SystemClock.elapsedRealtime()
        block()
        return SystemClock.elapsedRealtime() - start
    }

    private companion object {
        const val TAG = "AudoraLoadTest"
        const val UI_TIMEOUT_MS = 15_000L
        const val MAX_BACKS = 3
        const val TITLE = "Load"

        /** Every title has 4 digits, so none is part of another: a search for one finds that book alone. */
        fun titleOf(i: Int) = SyntheticLibrary.titleOf(i, TITLE)
    }
}
