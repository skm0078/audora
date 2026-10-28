// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/library/BookSourceScreensTest.kt
// Purpose: A Drive book is marked on its list row and grid tile and a phone book is not; the details sheet says where
//          each lives and offers "Keep on this phone" only for the Drive one (ADR 2026-10-28-book-source). Screenshots
//          go to CI's "player-screens" artifact.

package io.github.skm0078.audora.ui.library

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
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
import io.github.skm0078.audora.core.library.LibraryQuery
import io.github.skm0078.audora.ui.Screens
import io.github.skm0078.audora.ui.theme.AudoraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookSourceScreensTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val asked = mutableListOf<String>()

    private val actions = object : LibraryActions {
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

        override fun onQuery(query: LibraryQuery) = Unit

        override fun onBookAction(book: Book, action: BookAction) {
            asked += "${action.name} ${book.title}"
        }
    }

    private fun text(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun showLibrary(layout: LibraryLayout) = rule.setContent {
        DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(TEST_PHONE_SIZE)) {
            AudoraTheme {
                LibraryScreen(
                    listState = LibraryListState.Loaded(listOf(DRIVE_BOOK, PHONE_BOOK)),
                    busy = false,
                    message = null,
                    pendingRemoval = null,
                    actions = actions,
                    view = LibraryLayoutState(layout) {},
                )
            }
        }
    }

    private fun showDetails(item: LibraryItem) = rule.setContent {
        AudoraTheme { BookDetailsSheet(item, actions, CoverSource.None, onEdit = {}, onDismiss = {}) }
    }

    @Test
    fun aListRowSaysDriveOnlyForTheDriveBook() {
        showLibrary(LibraryLayout.LIST)
        val drive = text(R.string.book_source_google_drive)

        rule.onAllNodes(hasText(drive)).assertCountEquals(1)
        // Phone books are most of a library: they carry no label on the row.
        rule.onAllNodes(hasText(text(R.string.book_source_phone))).assertCountEquals(0)
        Screens.save("book-source-list", rule.onAllNodes(isRoot()).onFirst().captureToImage().asAndroidBitmap())
    }

    @Test
    fun aGridTileTellsTalkBackWhereTheDriveBookIs() {
        showLibrary(LibraryLayout.GRID)
        val drive = text(R.string.book_source_google_drive)

        val named = { title: String -> hasContentDescription(title, substring = true) }
        rule.onAllNodes(named("Gitanjali") and named(drive)).onFirst().assertExists()
        rule.onAllNodes(named("Gora") and named(drive)).assertCountEquals(0)
        Screens.save("book-source-grid", rule.onAllNodes(isRoot()).onFirst().captureToImage().asAndroidBitmap())
    }

    @Test
    fun theDriveBooksDetailsSayWhereAndOfferToKeepACopy() {
        showDetails(DRIVE_BOOK)
        val drive = text(R.string.book_source_google_drive)

        rule.onNodeWithText(text(R.string.book_details_source, drive)).assertExists()
        rule.onNodeWithText(text(R.string.book_source_needs_app, drive)).assertExists()
        Screens.save("book-source-details", rule.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap())
        rule.onNodeWithText(text(R.string.library_keep_on_phone)).performScrollTo().performClick()
        rule.waitForIdle()

        assertEquals(listOf("KEEP_ON_PHONE Gitanjali"), asked)
    }

    @Test
    fun aPhoneBooksDetailsSayOnThisPhoneWithNoCopyOffered() {
        showDetails(PHONE_BOOK)

        rule.onNodeWithText(text(R.string.book_details_source, text(R.string.book_source_phone))).assertExists()
        rule.onNodeWithText(text(R.string.library_keep_on_phone)).assertDoesNotExist()
    }

    private companion object {
        val TEST_PHONE_SIZE = DpSize(411.dp, 914.dp)
        val DRIVE_BOOK = LibraryItem(
            Book(
                BookId("drive"),
                "content://com.google.android.apps.docs.storage/document/acc%3D1%3Bdoc%3D9",
                "Gitanjali",
                1L,
                "fp-drive",
                12,
                0L,
                null,
            ),
            BookAvailability.AVAILABLE,
        )
        val PHONE_BOOK = LibraryItem(
            Book(
                BookId("phone"),
                "content://com.android.externalstorage.documents/document/primary%3AGora.pdf",
                "Gora",
                1L,
                "fp-phone",
                30,
                0L,
                null,
            ),
            BookAvailability.AVAILABLE,
        )
    }
}
