// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/library/FolderSyncScreenTest.kt
// Purpose: Sync through a folder (ADR 2026-10-26-folder-sync) in the library: the ⋮ entry is offered, the sheet says
//          what is written where before a folder is picked, offers Pick a folder and the book copies switch, and
//          closes on Done.

package io.github.skm0078.audora.ui.library

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.theme.AudoraTheme
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderSyncScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun text(id: Int): String = context.getString(id)

    @Before
    fun show() {
        rule.setContent { AudoraTheme { FolderSyncRoute() } }
    }

    @After
    fun close() {
        rule.runOnUiThread { FolderSyncUi.open = false }
    }

    @Test
    fun theSidePanelOffersFolderSync() {
        assertTrue(LibraryAction.SYNC_FOLDER in LibraryAction.inPanel)
    }

    @Test
    fun theSheetExplainsAndOffersAFolderBeforeOneIsPicked() {
        rule.runOnUiThread { FolderSyncUi.open = true }

        rule.onNodeWithText(text(R.string.sync_explain)).assertExists()
        rule.onNodeWithText(text(R.string.sync_no_folder)).assertExists()
        rule.onNodeWithText(text(R.string.sync_pick)).performScrollTo().assertExists()
        // Sync now and Stop need a folder first.
        rule.onNodeWithText(text(R.string.sync_now)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.sync_stop)).assertDoesNotExist()

        rule.onNodeWithText(text(R.string.sync_done)).performClick()
        rule.waitForIdle()
        assertFalse(FolderSyncUi.open)
    }

    @Test
    fun theBookCopiesSwitchTurnsOffAndOnAgain() {
        rule.runOnUiThread { FolderSyncUi.open = true }
        rule.onNodeWithText(text(R.string.sync_copy_books_hint), substring = true).performScrollTo().performClick()
        rule.waitUntil(TIMEOUT_MS) { runCatching { copiesSwitch().assertIsOff() }.isSuccess }
        rule.onNodeWithText(text(R.string.sync_copy_books_hint), substring = true).performClick()
        rule.waitUntil(TIMEOUT_MS) { runCatching { copiesSwitch().assertIsOn() }.isSuccess }
    }

    /** The row is one toggleable node; its text merges into it. */
    private fun copiesSwitch() = rule.onNodeWithText(text(R.string.sync_copy_books), substring = true)

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
