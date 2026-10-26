// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/reader/PlayerBookmarkTest.kt
// Purpose: The player's one-tap bookmark (CF-2): the button beside the title asks for the sentence being read, shows
//          filled once it has a bookmark, and the notice offers Add note and Undo. Leaves screenshots for review in
//          CI's "player-screens" artifact.

package io.github.skm0078.audora.ui.reader

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.notes.Bookmark
import io.github.skm0078.audora.core.resume.ResumeAnchor
import io.github.skm0078.audora.pdf.render.ViewPoint
import io.github.skm0078.audora.ui.Screens
import io.github.skm0078.audora.ui.theme.AudoraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerBookmarkTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    @Test
    fun theButtonBookmarksAndTheNoticeOffersAddNoteAndUndo() {
        val actions = CountingActions()
        val notesActions = CountingNotes()
        var notes by mutableStateOf(NotesState())
        rule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(PHONE)) {
                AudoraTheme {
                    PlayerScreen(
                        state = playingState(),
                        config = ReaderConfig(),
                        actions = actions,
                        onShowPage = {},
                        modifier = Modifier.testTag(PLAYER),
                        notes = notes,
                        notesActions = notesActions,
                    )
                }
            }
        }
        rule.onNodeWithContentDescription(text(R.string.player_bookmark_add)).performClick()
        assertEquals("the button asks for a bookmark here", 1, actions.quickBookmarks)
        Screens.save("player-bookmark-button", rule.onNodeWithTag(PLAYER).captureToImage().asAndroidBitmap())

        // What the notes model then shows: the sentence has its bookmark and the notice is up.
        val saved = Bookmark(1, anchorOf(UNITS[1]))
        rule.runOnIdle { notes = NotesState(bookmarks = listOf(saved), quickBookmark = saved) }
        rule.onNodeWithContentDescription(text(R.string.player_bookmark_edit)).assertExists()
        rule.onNodeWithText(text(R.string.player_bookmark_saved)).assertExists()
        Screens.save("player-bookmark-notice", rule.onNodeWithTag(PLAYER).captureToImage().asAndroidBitmap())
        rule.onNodeWithText(text(R.string.player_bookmark_note)).performClick()
        rule.onNodeWithText(text(R.string.player_bookmark_undo)).performClick()
        assertEquals(1, notesActions.notes)
        assertEquals(1, notesActions.undos)
    }

    private class CountingNotes : BookNotesActions {
        var notes = 0
        var undos = 0
        override fun noteQuickBookmark() {
            notes++
        }

        override fun undoQuickBookmark() {
            undos++
        }
    }

    private class CountingActions : ReaderActions {
        var quickBookmarks = 0
        override fun onQuickBookmark() {
            quickBookmarks++
        }

        override fun onBack() = Unit
        override fun onPreviousPage() = Unit
        override fun onNextPage() = Unit
        override fun onGoToPage(page: Int) = Unit
        override fun onShowReadingPosition() = Unit
        override fun onSentenceTapped(unit: ReadingUnit) = Unit
        override fun onPageTapped(point: ViewPoint) = Unit
        override fun onViewChanged(view: ReaderView) = Unit
        override fun onPageWidth(widthPx: Int) = Unit
        override fun onPlayPause() = Unit
        override fun onSkipBack() = Unit
        override fun onSkipForward() = Unit
        override fun onSpeed(speed: Float) = Unit
        override fun onSleepTimer(setting: SleepTimerSetting) = Unit
        override fun onPasswordSubmitted(password: String) = Unit
        override fun onPasswordDismissed() = Unit
        override fun onAskForPassword() = Unit
        override fun onRetry() = Unit
    }

    private companion object {
        val PHONE = DpSize(411.dp, 914.dp)
        const val PLAYER = "player"
        val BOOK = BookId("bookmark-book")

        val UNITS: List<ReadingUnit> = run {
            var start = 0
            listOf(
                "It was the best of times, it was the worst of times.",
                "It was the age of wisdom, it was the age of foolishness.",
                "It was the epoch of belief, it was the epoch of incredulity.",
            ).mapIndexed { index, text ->
                ReadingUnit(0, index, start, start + text.length, text, Lang.EN, 0, null).also {
                    start += text.length + 1
                }
            }
        }

        fun anchorOf(unit: ReadingUnit) = ResumeAnchor(
            bookId = BOOK,
            page = unit.page,
            charStart = unit.charStart,
            fingerprint = 0,
            tailFingerprint = 0,
            pipelineVersion = 1,
            savedAtMillis = 0,
        )

        fun playingState() = ReaderState(
            bookId = BOOK,
            title = "A Tale of Two Cities",
            document = DocumentState.Open(pageCount = 80),
            content = PageContent.Ready(UNITS),
            view = ReaderView.TEXT,
            playback = PlaybackSnapshot(
                connection = PlaybackSnapshot.Connection.CONNECTED,
                position = ReadingPosition(BOOK, 0, UNITS[1].charStart),
                canSkipBack = true,
                canSkipForward = true,
            ),
        )
    }
}
