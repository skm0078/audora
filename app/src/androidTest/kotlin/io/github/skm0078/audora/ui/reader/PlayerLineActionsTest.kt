// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/reader/PlayerLineActionsTest.kt
// Purpose: The player's buttons for the line being read (ADR 2026-10-30-player-line-actions): each shows its name,
//          Highlight marks the sentence being read and says when it is marked, Share opens that sentence's Share
//          choices, and before the page has words Share offers the book's card. Leaves a screenshot for review in
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
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.notes.Highlight
import io.github.skm0078.audora.core.resume.ResumeAnchor
import io.github.skm0078.audora.ui.Screens
import io.github.skm0078.audora.ui.theme.AudoraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerLineActionsTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int) = rule.activity.getString(id)

    private val notesActions = Recording()
    private val reader = object : ReaderActions by NoReaderActions {
        var bookCards = 0
        override fun onShareBook() {
            bookCards++
        }
    }

    private fun showPlayer(state: ReaderState, notes: () -> NotesState = { NotesState() }) = rule.setContent {
        DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(PHONE)) {
            AudoraTheme {
                PlayerScreen(
                    state = state,
                    config = ReaderConfig(),
                    actions = reader,
                    onShowPage = {},
                    modifier = Modifier.testTag(PLAYER),
                    notes = notes(),
                    notesActions = notesActions,
                )
            }
        }
    }

    @Test
    fun highlightMarksTheLineBeingReadAndSaysSo() {
        var notes by mutableStateOf(NotesState())
        showPlayer(playingState()) { notes }
        listOf(R.string.player_line_bookmark, R.string.player_line_highlight, R.string.reader_share).forEach {
            rule.onNodeWithText(text(it), useUnmergedTree = true).assertExists()
        }
        rule.onNodeWithContentDescription(text(R.string.sentence_menu_highlight)).performClick()
        assertEquals(listOf(UNITS[1]), notesActions.highlighted)

        // What the notes model then shows: the line has its highlight, so the button offers to remove it.
        val marked = Highlight(1, anchorOf(UNITS[1]), 0, UNITS[1].charEnd, UNITS[1].text)
        rule.runOnIdle {
            notes = NotesState(placed = PlacedHighlights(page = 0, byIndex = mapOf(UNITS[1].index to listOf(marked))))
        }
        rule.onNodeWithContentDescription(text(R.string.sentence_menu_unhighlight)).assertExists()
        rule.onNodeWithText(text(R.string.player_line_highlighted), useUnmergedTree = true).assertExists()
        Screens.save("player-line-actions", rule.onNodeWithTag(PLAYER).captureToImage().asAndroidBitmap())
    }

    @Test
    fun shareOpensTheLineBeingReadsChoices() {
        showPlayer(playingState())
        rule.onNodeWithContentDescription(text(R.string.reader_share)).performClick()
        assertEquals(listOf(UNITS[1]), notesActions.shared)
        assertEquals(0, reader.bookCards)
    }

    @Test
    fun beforeThePageHasWordsShareOffersTheBookAndTheOthersWait() {
        showPlayer(playingState().copy(content = PageContent.Loading))
        rule.onNodeWithContentDescription(text(R.string.sentence_menu_highlight)).assertIsNotEnabled()
        rule.onNodeWithContentDescription(text(R.string.player_bookmark_add)).assertIsNotEnabled()
        rule.onNodeWithContentDescription(text(R.string.reader_share)).performClick()
        rule.runOnIdle { assertEquals(1, reader.bookCards) }
        assertEquals(emptyList<ReadingUnit>(), notesActions.shared)
    }

    private class Recording : BookNotesActions {
        val highlighted = mutableListOf<ReadingUnit>()
        val shared = mutableListOf<ReadingUnit>()

        override fun toggleHighlight(unit: ReadingUnit) {
            highlighted += unit
        }

        override fun openShare(unit: ReadingUnit) {
            shared += unit
        }
    }

    private companion object {
        val PHONE = DpSize(411.dp, 914.dp)
        const val PLAYER = "player"
        val BOOK = BookId("line-actions-book")

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
