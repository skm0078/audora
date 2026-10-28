// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/SourceJourney.kt
// Purpose: The release journey's book-source step on the installed app (ADR 2026-10-28-book-source): a book added
//          with "Open with" says it is on this phone and offers no copy. Kept apart from ReleaseJourneyTest so neither
//          file grows past detekt's class size.

package io.github.skm0078.audora.release

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.printToString
import io.github.skm0078.audora.R
import io.github.skm0078.audora.release.JourneyDevice.app
import io.github.skm0078.audora.release.JourneyDevice.shows

/** [backToTabs] leaves any screen for the main tabs, as the journey does. */
internal class SourceJourney(private val rule: ComposeTestRule, private val backToTabs: () -> Unit) {
    private fun text(id: Int, vararg args: Any): String = app.getString(id, *args)

    private fun screen(): String = rule.onAllNodes(isRoot()).printToString()

    private fun tap(matcher: SemanticsMatcher) {
        rule.waitUntil(UI_TIMEOUT_MS) { rule.shows(matcher) }
        rule.onAllNodes(matcher).onFirst().performClick()
    }

    /**
     * The journey's books came from Downloads through "Open with", so the first one's details say "Where: On this
     * phone", and "Keep on this phone" is not offered. The details sheet closes by its handle, as the book menu's do.
     */
    fun aPhoneBookSaysWhereItLives() {
        backToTabs()
        tap(hasText(text(R.string.tab_library)) and hasClickAction())
        rule.waitUntil(UI_TIMEOUT_MS) { rule.shows(hasText(text(R.string.library_title))) }
        val namedMenu = text(R.string.library_book_menu_named, NAME_MARK).split(NAME_MARK).maxBy { it.length }.trim()
        val menuButton = hasContentDescription(namedMenu, substring = true) and hasClickAction()
        // Composed is not on screen: the list scrolls the first book's menu into view before the tap, as step 20 does.
        val list = hasScrollToIndexAction()
        runCatching { rule.waitUntil(UI_TIMEOUT_MS) { rule.shows(list) } }
        check(rule.shows(list)) { "No book list on the library:\n${screen()}" }
        rule.onAllNodes(list).onFirst().performScrollToNode(menuButton)
        tap(menuButton)
        // The emulator's screen is small: the menu's Details row can sit below the sheet's fold.
        val details = hasText(text(R.string.book_details_title)) and hasClickAction()
        rule.waitUntil(UI_TIMEOUT_MS) { rule.shows(details) }
        rule.onAllNodes(details).onFirst().performScrollTo().performClick()
        val where = hasText(text(R.string.book_details_source, text(R.string.book_source_phone)))
        rule.waitUntil(UI_TIMEOUT_MS) { rule.shows(where) }
        check(!rule.shows(hasText(text(R.string.library_keep_on_phone)))) { "A phone book was offered a copy" }
        tap(
            hasContentDescription(text(R.string.a11y_sheet_close)) and
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
        )
        rule.waitUntil(UI_TIMEOUT_MS) { !rule.shows(where) }
    }

    private companion object {
        /** Stands in for the book's name, to find the words around it in this language. */
        const val NAME_MARK = "\u0001"
        const val UI_TIMEOUT_MS = 15_000L
    }
}
