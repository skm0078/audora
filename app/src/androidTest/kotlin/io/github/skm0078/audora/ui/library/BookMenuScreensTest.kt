// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/library/BookMenuScreensTest.kt
// Purpose: The book menu opens from a long press, its buttons and Save as tiles reach the right action, and the sort
//          sheet's choices apply at once, grid to list included; screenshots of both sheets for review in CI's
//          "player-screens" artifact (ADR 2026-10-28-book-menu-sort-sheet).

package io.github.skm0078.audora.ui.library

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookAvailability
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.BookProgress
import io.github.skm0078.audora.core.library.LibraryQuery
import io.github.skm0078.audora.core.library.LibrarySort
import io.github.skm0078.audora.ui.Screens
import io.github.skm0078.audora.ui.theme.AudoraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookMenuScreensTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val asked = mutableListOf<String>()
    private var query by mutableStateOf(LibraryQuery())
    private var layout by mutableStateOf(LibraryLayout.AUTO)

    private val actions = object : LibraryActions {
        override fun onAddPdf(documentUri: Uri) = Unit

        override fun onLocate(id: BookId, documentUri: Uri) = Unit

        override fun onOpenBook(book: Book) = Unit

        override fun onContinue(book: Book) {
            asked += "play ${book.title}"
        }

        override fun onRequestRemoval(book: Book) {
            asked += "remove ${book.title}"
        }

        override fun onExportAudiobook(book: Book) {
            asked += "audiobook ${book.title}"
        }

        override fun onConfirmRemoval() = Unit

        override fun onCancelRemoval() = Unit

        override fun onMessageShown() = Unit

        override fun onScreenResumed() = Unit

        override fun onQuery(query: LibraryQuery) {
            this@BookMenuScreensTest.query = query
        }

        override fun onBookAction(book: Book, action: BookAction) {
            asked += "${action.name} ${book.title}"
        }
    }

    private fun showLibrary() = rule.setContent {
        DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(TEST_PHONE_SIZE)) {
            AudoraTheme {
                val items = listOf(GORA_HALF_READ)
                LibraryScreen(
                    listState = LibraryListState.Loaded(items, query = query),
                    busy = false,
                    message = null,
                    pendingRemoval = null,
                    actions = actions,
                    view = LibraryLayoutState(layout) { layout = it },
                )
            }
        }
    }

    private fun text(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun openMenuByLongPress() {
        // The cover and the ⋮ both name the book; the cover comes first.
        rule.onAllNodes(hasContentDescription("Gora", substring = true) and hasClickAction()).onFirst()
            .performTouchInput { longClick() }
        rule.waitForIdle()
        rule.onNodeWithContentDescription(text(R.string.book_actions_title, "Gora")).assertExists()
    }

    private fun sheet() = rule.onAllNodes(isRoot()).onLast()

    @Test
    fun aLongPressOpensTheMenuAndPlayPlaysTheBook() {
        showLibrary()
        openMenuByLongPress()
        Screens.save("book-menu", sheet().captureToImage().asAndroidBitmap())

        rule.onNodeWithText(text(R.string.menu_play)).performClick()
        rule.waitForIdle()

        assertEquals(listOf("play Gora"), asked)
        rule.onNodeWithContentDescription(text(R.string.book_actions_title, "Gora")).assertDoesNotExist()
    }

    @Test
    fun saveAsOpensInPlaceAndItsTilesReachTheirActions() {
        showLibrary()
        openMenuByLongPress()
        rule.onAllNodes(hasText(text(R.string.menu_save_audiobook))).fetchSemanticsNodes().let {
            assertEquals("Save as starts closed", 0, it.size)
        }

        rule.onNodeWithText(text(R.string.menu_save_as)).performClick()
        rule.onNodeWithText(text(R.string.menu_save_talking_book)).performScrollTo().assertExists()
        Screens.save("book-menu-save-as", sheet().captureToImage().asAndroidBitmap())
        rule.onNodeWithText(text(R.string.menu_save_audiobook)).performScrollTo().performClick()
        rule.waitForIdle()

        assertEquals(listOf("audiobook Gora"), asked)
    }

    @Test
    fun theHandleClosesTheMenuRightAfterSaveAsOpens() {
        showLibrary()
        openMenuByLongPress()
        rule.onNodeWithText(text(R.string.menu_save_as)).performClick()
        // The handle, not Material's scrim, which carries the same name.
        rule.onNode(
            hasContentDescription(text(R.string.a11y_sheet_close)) and
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
        ).performClick()
        rule.waitForIdle()

        rule.onNodeWithContentDescription(text(R.string.book_actions_title, "Gora")).assertDoesNotExist()
        assertEquals(emptyList<String>(), asked)
    }

    @Test
    fun finishedMarksTheBookAndRemoveAsksToRemoveIt() {
        showLibrary()
        openMenuByLongPress()
        rule.onNodeWithText(text(R.string.library_status_finished)).performClick()
        rule.waitForIdle()

        rule.onNodeWithContentDescription(text(R.string.library_book_menu_named, "Gora")).performClick()
        rule.onNodeWithText(text(R.string.library_remove)).performScrollTo().performClick()
        rule.waitForIdle()

        assertEquals(listOf("MARK_FINISHED Gora", "remove Gora"), asked)
    }

    @Test
    fun theSortSheetAppliesSortAndLayoutAtOnce() {
        showLibrary()
        val openBook = text(R.string.a11y_open_book)
        val listRow = SemanticsMatcher("a list row") { node ->
            node.config.getOrNull(SemanticsActions.OnClick)?.label == openBook
        }
        rule.onAllNodes(listRow).fetchSemanticsNodes().let { assertEquals("Covers first", 0, it.size) }

        rule.onNodeWithContentDescription(text(R.string.library_sort_filter)).performClick()
        rule.onNodeWithText(text(R.string.library_sort_title)).performClick()
        rule.runOnIdle { assertEquals(LibrarySort.TITLE, query.sort) }
        Screens.save("library-sort-sheet", sheet().captureToImage().asAndroidBitmap())

        rule.onNodeWithText(text(R.string.library_layout_list)).performScrollTo().performClick()
        rule.runOnIdle { assertEquals(LibraryLayout.LIST, layout) }
        rule.onNodeWithText(context.resources.getQuantityString(R.plurals.library_sort_show, 1, 1))
            .performScrollTo()
            .performClick()
        rule.waitForIdle()

        rule.onAllNodes(listRow).fetchSemanticsNodes().let { assertEquals("The list shows right away", 1, it.size) }

        rule.onNodeWithContentDescription(text(R.string.library_sort_filter)).performClick()
        rule.onNodeWithText(text(R.string.library_sort_reset)).performClick()
        rule.runOnIdle {
            assertEquals(LibrarySort.RECENT, query.sort)
            assertEquals(LibraryLayout.AUTO, layout)
        }
    }

    private companion object {
        val TEST_PHONE_SIZE = DpSize(411.dp, 914.dp)
        val GORA = Book(BookId("gora"), "content://test/gora", "Gora", 1_000, "f-gora", 10, 1, null)
        val GORA_HALF_READ =
            LibraryItem(GORA, BookAvailability.AVAILABLE, BookProgress(page = 4, pageCount = 10, savedAtMillis = 1L))
    }
}
