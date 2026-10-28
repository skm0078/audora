// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/reader/ReaderSidePanelTest.kt
// Purpose: On a tablet the reader shows contents, notes and people beside the page, and a chapter or "Bookmark here"
//          acts with the panel left up; a phone keeps the top bar's sheets. Screenshots for review in CI's
//          "player-screens" artifact (ADR 2026-10-28-tablet-panes).

package io.github.skm0078.audora.ui.reader

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.R
import io.github.skm0078.audora.battery.OnboardingPreferences
import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.pdf.outline.ChapterEntry
import io.github.skm0078.audora.pdf.render.ViewPoint
import io.github.skm0078.audora.ui.Screens
import io.github.skm0078.audora.ui.theme.AudoraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderSidePanelTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val asked = mutableListOf<String>()

    private fun show(size: DpSize) {
        // The one-time gesture tips grow the controls at the bottom; other tests may leave them pending or not.
        OnboardingPreferences(context).gestureTipsSeen = true
        rule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size)) {
                AudoraTheme {
                    ReaderScreen(
                        STATE,
                        ReaderConfig(),
                        Recording(),
                        people = { modifier -> Text(PEOPLE_SLOT, modifier) },
                        windowWidth = size.width,
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun text(id: Int): String = context.getString(id)

    private val inPanel = hasAnyAncestor(hasTestTag(SIDE_PANEL_TAG))

    @Test
    fun onATabletAChapterOpensBesideThePageAndThePanelStays() {
        show(TABLET_SIZE)
        rule.onNodeWithTag(SIDE_PANEL_TAG).assertExists()
        rule.onNodeWithText(text(R.string.reader_panel_contents)).assertIsSelected()
        // The top bar's sheets would repeat the panel.
        rule.onNodeWithContentDescription(text(R.string.chapters_title)).assertDoesNotExist()
        Screens.save("reader-side-panel", rule.onRoot().captureToImage().asAndroidBitmap())

        rule.onNode(hasText(CHAPTERS[1].title) and hasClickAction() and inPanel).performClick()
        rule.waitForIdle()

        assertEquals(listOf("chapter ${CHAPTERS[1].page}"), asked)
        rule.onNodeWithTag(SIDE_PANEL_TAG).assertExists()
    }

    @Test
    fun theNotesAndPeopleTabsShowTheirListsInPlace() {
        show(TABLET_SIZE)
        rule.onNodeWithText(text(R.string.reader_panel_notes)).performClick()
        rule.onNode(hasText(text(R.string.bookmarks_add_here)) and inPanel).performClick()
        rule.waitForIdle()
        assertEquals(listOf("bookmark here"), asked)
        Screens.save("reader-side-panel-notes", rule.onRoot().captureToImage().asAndroidBitmap())

        rule.onNodeWithText(text(R.string.reader_panel_people)).performClick()
        rule.onNode(hasText(PEOPLE_SLOT) and inPanel).assertExists()
    }

    @Test
    fun onAPhoneThereIsNoPanelAndTheTopBarKeepsItsSheets() {
        show(PHONE_SIZE)
        rule.onAllNodes(hasTestTag(SIDE_PANEL_TAG)).fetchSemanticsNodes().let { assertEquals(0, it.size) }
        rule.onNodeWithContentDescription(text(R.string.chapters_title)).assertExists()
    }

    private inner class Recording : ReaderActions {
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
        override fun onChapter(chapter: ChapterEntry) {
            asked += "chapter ${chapter.page}"
        }
        override fun onBookmarkHere() {
            asked += "bookmark here"
        }
    }

    private companion object {
        val TABLET_SIZE = DpSize(1280.dp, 800.dp)
        val PHONE_SIZE = DpSize(411.dp, 914.dp)
        const val PEOPLE_SLOT = "Binodini, a young widow"
        val BOOK = BookId("panel-book")
        val CHAPTERS = listOf(
            ChapterEntry("The Wedding That Was Not", page = 0, depth = 0),
            ChapterEntry("Binodini Arrives", page = 4, depth = 0),
            ChapterEntry("Letters", page = 8, depth = 0),
        )
        val STATE = ReaderState(
            bookId = BOOK,
            title = "Chokher Bali",
            document = DocumentState.Open(pageCount = 10),
            page = 2,
            content = PageContent.Ready(
                listOf(ReadingUnit(2, 0, 0, 23, "Mahendra's mother wept.", Lang.EN, 0, null)),
            ),
            view = ReaderView.TEXT,
            chapters = CHAPTERS,
        )
    }
}
