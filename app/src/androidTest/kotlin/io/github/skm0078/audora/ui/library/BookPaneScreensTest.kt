// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/library/BookPaneScreensTest.kt
// Purpose: On a tablet the book pane sits beside the books: a first tap picks a book, a second opens it, and the
//          pane's buttons act without closing it; a phone keeps the sheet. Screenshots for review in CI's
//          "player-screens" artifact (ADR 2026-10-28-tablet-panes).

package io.github.skm0078.audora.ui.library

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookAvailability
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.BookProgress
import io.github.skm0078.audora.ui.Screens
import io.github.skm0078.audora.ui.theme.AudoraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookPaneScreensTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val asked = mutableListOf<String>()

    private val actions = object : LibraryActions {
        override fun onAddPdf(documentUri: Uri) = Unit

        override fun onLocate(id: BookId, documentUri: Uri) = Unit

        override fun onOpenBook(book: Book) {
            asked += "open ${book.title}"
        }

        override fun onContinue(book: Book) {
            asked += "play ${book.title}"
        }

        override fun onRequestRemoval(book: Book) = Unit

        override fun onExportAudiobook(book: Book) = Unit

        override fun onConfirmRemoval() = Unit

        override fun onCancelRemoval() = Unit

        override fun onMessageShown() = Unit

        override fun onScreenResumed() = Unit

        override fun onBookAction(book: Book, action: BookAction) {
            asked += "${action.name} ${book.title}"
        }
    }

    private fun show(size: DpSize) = rule.setContent {
        DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size)) {
            AudoraTheme {
                LibraryScreen(
                    listState = LibraryListState.Loaded(listOf(GORA_HALF_READ, CHOKHER_BALI)),
                    busy = false,
                    message = null,
                    pendingRemoval = null,
                    actions = actions,
                )
            }
        }
    }

    private fun text(id: Int, vararg args: Any): String = context.getString(id, *args)

    private val inPane = hasAnyAncestor(hasTestTag(BOOK_PANE_TAG))

    /** The book's cover in the grid, not its title in the pane. */
    private fun cover(title: String) =
        rule.onAllNodes(hasContentDescription(title, substring = true) and hasClickAction() and !inPane).onFirst()

    private fun paneShows(title: String) =
        rule.onNode(hasContentDescription(text(R.string.book_actions_title, title)) and inPane).assertExists()

    @Test
    fun onATabletAFirstTapPicksTheBookAndASecondOpensIt() {
        show(TABLET_SIZE)
        rule.onNodeWithTag(BOOK_PANE_TAG).assertExists()
        paneShows("Gora")
        Screens.save("library-book-pane", rule.onRoot().captureToImage().asAndroidBitmap())

        cover("Chokher Bali").performClick()
        rule.waitForIdle()
        paneShows("Chokher Bali")
        cover("Chokher Bali").assertIsSelected()
        assertEquals("A first tap only picks", emptyList<String>(), asked)

        cover("Chokher Bali").performClick()
        rule.waitForIdle()
        assertEquals(listOf("open Chokher Bali"), asked)
    }

    @Test
    fun thePanesButtonsActAndThePaneStays() {
        show(TABLET_SIZE)
        rule.onNode(hasText(text(R.string.menu_play)) and inPane).performClick()
        // Save as starts open in the pane: its tiles show without a tap.
        rule.onNode(hasText(text(R.string.menu_save_audiobook)) and inPane).performScrollTo().assertExists()
        rule.waitForIdle()

        assertEquals(listOf("play Gora"), asked)
        paneShows("Gora")
    }

    @Test
    fun onAPhoneATapOpensTheBookAndThereIsNoPane() {
        show(PHONE_SIZE)
        rule.onAllNodes(hasTestTag(BOOK_PANE_TAG)).fetchSemanticsNodes().let { assertEquals(0, it.size) }

        cover("Chokher Bali").performClick()
        rule.waitForIdle()
        assertEquals(listOf("open Chokher Bali"), asked)
    }

    private companion object {
        val TABLET_SIZE = DpSize(1280.dp, 800.dp)
        val PHONE_SIZE = DpSize(411.dp, 914.dp)
        val GORA = Book(BookId("gora"), "content://test/gora", "Gora", 1_000, "f-gora", 10, 1, null)
        val GORA_HALF_READ =
            LibraryItem(GORA, BookAvailability.AVAILABLE, BookProgress(page = 4, pageCount = 10, savedAtMillis = 1L))
        val CHOKHER_BALI = LibraryItem(
            Book(BookId("chokher-bali"), "content://test/cb", "Chokher Bali", 900, "f-cb", 12, 1, null),
            BookAvailability.AVAILABLE,
        )
    }
}
