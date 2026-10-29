// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/library/MakeItYoursScreenTest.kt
// Purpose: "Make it yours" opens from the side panel, and each choice shows on the library at
//          once: the name in the greeting, a part turned on or off, the chips hidden, a part moved up (ADR
//          2026-10-28-make-it-yours).

package io.github.skm0078.audora.ui.library

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.home.HomeLayout
import io.github.skm0078.audora.core.home.HomeSection
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookAvailability
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.BookProgress
import io.github.skm0078.audora.ui.theme.AudoraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MakeItYoursScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var saved = HomeLayout()

    private fun text(id: Int, vararg args: Any): String = rule.activity.getString(id, *args)

    private fun showLibrary(requested: LibraryAction? = null, onRequestTaken: () -> Unit = {}) {
        rule.setContent {
            var layout by remember { mutableStateOf(saved) }
            AudoraTheme {
                LibraryScreen(
                    listState = LibraryListState.Loaded(BOOKS, latest = BOOKS.first()),
                    busy = false,
                    message = null,
                    pendingRemoval = null,
                    actions = NoActions,
                    home = LibraryHome(layout, onLayout = {
                        layout = it
                        saved = it
                    }),
                    requested = requested,
                    onRequestTaken = onRequestTaken,
                )
            }
        }
    }

    /** Scrolls to [matcher] first only inside a scrolling list (the sheet). */
    private fun tap(matcher: SemanticsMatcher) {
        val node = rule.onAllNodes(matcher).onFirst()
        val scrolls = rule.onAllNodes(matcher and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes().isNotEmpty()
        if (scrolls) node.performScrollTo()
        node.performClick()
        rule.waitForIdle()
    }

    /** The library with Make it yours asked for, as the side panel's row asks: the ⋮ menu that held it is gone. */
    private fun openSheet() {
        showLibrary(LibraryAction.MAKE_IT_YOURS)
        rule.onNode(hasText(text(R.string.home_sheet_body))).assertExists()
    }

    private fun switchFor(id: Int) = hasContentDescription(text(id)) and hasClickAction()

    /** The side panel's rows reach the library as a request: each opens its own sheet, once. */
    @Test
    fun theSidePanelOpensTheLibrarysOwnSheets() {
        var taken = 0
        showLibrary(LibraryAction.MAKE_IT_YOURS) { taken++ }
        rule.onNode(hasText(text(R.string.home_sheet_body))).assertExists()
        rule.runOnIdle { assertEquals(1, taken) }
    }

    @Test
    fun theSidePanelOpensTheCollectionsSheet() {
        showLibrary(LibraryAction.EDIT_COLLECTIONS)
        rule.onNode(hasText(text(R.string.collections_title))).assertExists()
    }

    @Test
    fun aNameIsGreetedAtOnce() {
        openSheet()
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement("  Ria ")
        rule.waitForIdle()
        assertEquals("Ria", saved.name)
        val named = listOf(
            R.string.library_greeting_morning_named,
            R.string.library_greeting_afternoon_named,
            R.string.library_greeting_evening_named,
        ).map { hasText(text(it, "Ria")) }.reduce { one, other -> one or other }
        assertTrue(rule.onAllNodes(named).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun recentlyAddedTurnsOnAndMovesUp() {
        openSheet()
        rule.onNode(switchFor(R.string.home_section_recent)).assertIsOff()
        tap(switchFor(R.string.home_section_recent))
        assertTrue(saved.shows(HomeSection.RECENTLY_ADDED))
        tap(hasContentDescription(text(R.string.home_move_up, text(R.string.home_section_recent))))
        tap(hasContentDescription(text(R.string.home_move_up, text(R.string.home_section_recent))))
        assertEquals(HomeSection.RECENTLY_ADDED, saved.order.first())
    }

    @Test
    fun theChipsAndTheContinueCardCanBeTurnedOff() {
        openSheet()
        tap(switchFor(R.string.home_section_chips))
        tap(switchFor(R.string.home_section_continue))
        assertFalse(saved.shows(HomeSection.SHELF_CHIPS))
        assertFalse(saved.shows(HomeSection.CONTINUE))
    }

    private object NoActions : LibraryActions {
        override fun onAddPdf(documentUri: Uri) = Unit

        override fun onLocate(id: BookId, documentUri: Uri) = Unit

        override fun onOpenBook(book: Book) = Unit

        override fun onContinue(book: Book) = Unit

        override fun onRequestRemoval(book: Book) = Unit

        override fun onExportAudiobook(book: Book) = Unit

        override fun onConfirmRemoval() = Unit

        override fun onCancelRemoval() = Unit

        override fun onMessageShown() = Unit

        override fun onScreenResumed() = Unit
    }

    private companion object {
        val BOOKS = (1..3).map { index ->
            LibraryItem(
                Book(BookId("b$index"), "content://b$index", "Book $index", 1L, "fp$index", 10, index.toLong(), null),
                BookAvailability.AVAILABLE,
                BookProgress(page = 1, pageCount = 10, savedAtMillis = 0),
            )
        }
    }
}
