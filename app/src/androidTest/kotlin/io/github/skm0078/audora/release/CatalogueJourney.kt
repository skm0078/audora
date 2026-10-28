// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/CatalogueJourney.kt
// Purpose: The release journey's book-catalogue step on the installed app (ADR 2026-10-28-curated-catalogues), kept
//          apart from ReleaseJourneyTest so that file stays under detekt's class size.

package io.github.skm0078.audora.release

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import io.github.skm0078.audora.R
import io.github.skm0078.audora.playback.web.CuratedCatalogue
import io.github.skm0078.audora.release.JourneyDevice.app
import io.github.skm0078.audora.release.JourneyDevice.shows
import io.github.skm0078.audora.ui.web.aboutOf
import org.junit.Assert.assertTrue

/** [backToTabs] leaves any screen for the main tabs, as the journey does. Nothing here touches the network. */
internal class CatalogueJourney(private val rule: ComposeTestRule, private val backToTabs: () -> Unit) {
    private fun text(id: Int): String = app.getString(id)

    private fun waitForNode(matcher: SemanticsMatcher) {
        rule.waitUntil(UI_TIMEOUT_MS) { rule.shows(matcher) }
    }

    private fun tap(matcher: SemanticsMatcher) {
        waitForNode(matcher)
        rule.onAllNodes(matcher).onFirst().performClick()
    }

    /**
     * The library's ⋮ menu opens Free book catalogues: every curated catalogue is listed with what it offers, and
     * there is no field to type a catalogue's address. Close goes back to the library.
     */
    fun theCuratedCataloguesAreListed() {
        backToTabs()
        tap(hasText(text(R.string.tab_library)) and hasClickAction())
        tap(hasContentDescription(text(R.string.reader_more)))
        tap(hasText(text(R.string.library_book_catalogues)))
        val explain = hasText(text(R.string.catalogues_explain))
        waitForNode(explain)
        val list = rule.onNode(hasScrollToNodeAction() and hasAnyDescendant(explain))
        CuratedCatalogue.entries.forEach { curated ->
            list.performScrollToNode(hasText(text(aboutOf(curated))))
            assertTrue("${curated.title} is listed", rule.shows(hasText(curated.title)))
        }
        assertTrue("no field to add a catalogue", rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty())
        tap(hasContentDescription(text(R.string.catalogues_close)))
        waitForNode(hasText(text(R.string.library_title)))
    }

    private companion object {
        const val UI_TIMEOUT_MS = 15_000L
    }
}
