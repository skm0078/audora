// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/library/RemovalUndoScreenTest.kt
// Purpose: The "Removed" snackbar offers Undo for the undo window and then closes by itself (CF-3), on a
//          phone-sized library; a screenshot of it for review in CI's "player-screens" artifact.

package io.github.skm0078.audora.ui.library

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookAvailability
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.ui.Screens
import io.github.skm0078.audora.ui.theme.AudoraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemovalUndoScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val undone = mutableListOf<BookId>()
    private var shown = 0

    private val actions = object : LibraryActions {
        override fun onAddPdf(documentUri: Uri) = Unit

        override fun onLocate(id: BookId, documentUri: Uri) = Unit

        override fun onOpenBook(book: Book) = Unit

        override fun onContinue(book: Book) = Unit

        override fun onRequestRemoval(book: Book) = Unit

        override fun onExportAudiobook(book: Book) = Unit

        override fun onConfirmRemoval() = Unit

        override fun onCancelRemoval() = Unit

        override fun onUndoRemoval(id: BookId) {
            undone += id
        }

        override fun onMessageShown() {
            shown++
        }

        override fun onScreenResumed() = Unit
    }

    private fun showRemoved(undoMs: Long) {
        rule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(TEST_PHONE_SIZE)) {
                AudoraTheme {
                    LibraryScreen(
                        listState = LibraryListState.Loaded(listOf(LibraryItem(GORA, BookAvailability.AVAILABLE))),
                        busy = false,
                        message = LibraryMessage.Removed("Gitanjali", undo = GITA, undoMs = undoMs),
                        pendingRemoval = null,
                        actions = actions,
                    )
                }
            }
        }
    }

    @Test
    fun undoOnTheSnackbarBringsTheBookBack() {
        showRemoved(undoMs = LONG_WINDOW_MS)
        val undo = context.getString(R.string.library_removed_undo)
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(hasText(undo)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(context.getString(R.string.library_removed, "Gitanjali")).assertExists()
        Screens.save("library-removed-undo", rule.onNode(isRoot()).captureToImage().asAndroidBitmap())

        rule.onNodeWithText(undo).performClick()
        rule.waitUntil(TIMEOUT_MS) { shown == 1 }

        assertEquals(listOf(GITA), undone)
    }

    @Test
    fun theSnackbarClosesByItselfWhenTheWindowEnds() {
        showRemoved(undoMs = SHORT_WINDOW_MS)
        val undo = context.getString(R.string.library_removed_undo)
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(hasText(undo)).fetchSemanticsNodes().isNotEmpty() }

        rule.waitUntil(TIMEOUT_MS) { shown == 1 }
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(hasText(undo)).fetchSemanticsNodes().isEmpty() }

        assertTrue("Nothing was undone", undone.isEmpty())
    }

    private companion object {
        val TEST_PHONE_SIZE = DpSize(411.dp, 914.dp)
        val GITA = BookId("gita")
        val GORA = Book(BookId("gora"), "content://test/gora", "Gora", 1_000, "f-gora", 10, 1, null)
        const val LONG_WINDOW_MS = 60_000L
        const val SHORT_WINDOW_MS = 1_500L
        const val TIMEOUT_MS = 10_000L
    }
}
