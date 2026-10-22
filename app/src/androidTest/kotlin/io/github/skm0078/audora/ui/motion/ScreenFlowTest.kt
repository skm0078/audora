// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/motion/ScreenFlowTest.kt
// Purpose: The app's routing on a device: library → reader → player and back, Settings → Storage → Voices and
//          back, all through AnimatedScreens and the system back button, as the screens are wired in MainActivity.

package io.github.skm0078.audora.ui.motion

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.ui.AppDestination
import io.github.skm0078.audora.ui.AppTab
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val book = BookId("b1")
    private var destination by mutableStateOf<AppDestination>(AppDestination.Library)

    /** Which composition last drew each route, so a rebuilt screen shows up as a new number. */
    private var built = 0
    private val compositions = mutableMapOf<String, Int>()

    private fun showScreens() = rule.setContent {
        AnimatedScreens(destination, onBack = { destination = it }) { screen ->
            val composition = remember { ++built }
            compositions[screen.toRoute()] = composition
            Text(screen.toRoute())
        }
    }

    private fun go(next: AppDestination) {
        rule.runOnIdle { destination = next }
        rule.waitForIdle()
    }

    private fun systemBack() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    private fun assertShowing(route: String) {
        rule.onNodeWithText(route).assertExists()
        assertEquals(route, destination.toRoute())
    }

    @Test
    fun libraryToReaderToPlayerAndBack() {
        showScreens()
        assertShowing("library")
        go(AppDestination.Reader(book))
        assertShowing("book/b1")
        systemBack()
        assertShowing("library")

        go(AppDestination.Player(book))
        assertShowing("player/b1")
        go(AppDestination.Reader(book, viaPlayer = true))
        assertShowing("book/b1?via=player")
        systemBack()
        assertShowing("player/b1")
        systemBack()
        assertShowing("library")
    }

    @Test
    fun oneBooksReaderAndPlayerAreOneScreen() {
        showScreens()
        val player = AppDestination.Player(book, AppTab.NOTES)
        val reader = AppDestination.Reader(book, AppTab.NOTES, viaPlayer = true)
        go(player)
        go(reader)
        assertShowing(reader.toRoute())
        // The same composition: switching kept the screen, and so the book's one ReaderModel (ADR-036 §6).
        assertEquals(compositions[player.toRoute()], compositions[reader.toRoute()])
        systemBack()
        systemBack()
        assertShowing("notes")
    }

    @Test
    fun settingsToStorageToVoicesAndBack() {
        showScreens()
        go(AppDestination.Settings)
        go(AppDestination.Storage)
        assertShowing("settings/storage")
        go(AppDestination.Voices(fromStorage = true))
        systemBack()
        assertShowing("settings/storage")
        systemBack()
        assertShowing("settings")
    }

    @Test
    fun aBackSwipeShowsWhereBackLeadsAndCancellingStays() {
        showScreens()
        go(AppDestination.Storage)
        go(AppDestination.Voices(fromStorage = true))
        swipeBack(PROGRESS_HALF)
        // Mid-swipe the screen below is drawn under the leaving one, but nothing has navigated yet.
        rule.onNodeWithText("settings/storage").assertExists()
        assertEquals("settings/storage/voices", destination.toRoute())
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        rule.waitForIdle()
        assertShowing("settings/storage/voices")
        rule.onNodeWithText("settings/storage").assertDoesNotExist()
    }

    @Test
    fun aBackSwipeLetGoFinishesBack() {
        showScreens()
        go(AppDestination.Storage)
        go(AppDestination.Voices(fromStorage = true))
        swipeBack(PROGRESS_HALF)
        systemBack()
        assertShowing("settings/storage")
        rule.onNodeWithText("settings/storage/voices").assertDoesNotExist()
    }

    /** A system back swipe from the left edge, held at [progress]. */
    private fun swipeBack(progress: Float) {
        rule.runOnUiThread {
            val dispatcher = rule.activity.onBackPressedDispatcher
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(0f, 0f, progress, BackEventCompat.EDGE_LEFT))
        }
        rule.waitForIdle()
    }

    private companion object {
        const val PROGRESS_HALF = 0.5f
    }
}
