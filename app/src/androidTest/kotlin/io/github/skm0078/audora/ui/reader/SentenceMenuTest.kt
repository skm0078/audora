// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/reader/SentenceMenuTest.kt
// Purpose: The sentence menu says what each item is for and acts on a highlight as a whole (sharing and copying it);
//          picking a passage's end and the fixes after "This sounded wrong" work on screen. Screenshots go to CI's
//          "player-screens" artifact.

package io.github.skm0078.audora.ui.reader

import android.content.ClipboardManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.TableMode
import io.github.skm0078.audora.core.model.TextBox
import io.github.skm0078.audora.core.notes.Highlight
import io.github.skm0078.audora.core.resume.ResumeAnchor
import io.github.skm0078.audora.pdf.render.ViewPoint
import io.github.skm0078.audora.playback.report.SoundedWrongReason
import io.github.skm0078.audora.ui.Screens
import io.github.skm0078.audora.ui.theme.AudoraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SentenceMenuTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** What the menu asked for, in order. */
    private val calls = mutableListOf<String>()

    private val notes = object : BookNotesActions {
        override fun dismissMenu() {
            calls += "dismiss"
        }

        override fun startRange(unit: ReadingUnit) {
            calls += "start range ${unit.index}"
        }

        override fun cancelRange() {
            calls += "cancel range"
        }
    }

    private val reader = object : ReaderActions by NoReaderActions {
        override fun onShareClip(unit: ReadingUnit) {
            calls += "clip ${unit.index}"
        }

        override fun onShareHighlight(highlight: Highlight) {
            calls += "passage ${highlight.anchor.page}-${highlight.endPage}"
        }

        override fun onSharePicture(lines: String, page: Int) {
            calls += "picture $page: $lines"
        }

        override fun onShareVideo(lines: String, page: Int) {
            calls += "video $page: $lines"
        }

        override fun onSharePagePart(unit: ReadingUnit) {
            calls += "page part ${unit.index}"
        }
    }

    private fun text(id: Int) = context.getString(id)

    @Test
    fun everyItemSaysWhatItIsForAndHighlightFromHereStartsAPassage() {
        rule.setContent { AudoraTheme { NotesOverlays(NotesState(menuFor = UNIT), notes, reader) } }

        rule.onNodeWithText(text(R.string.sentence_hint_highlight_range)).assertExists()
        rule.onNodeWithText(text(R.string.sentence_hint_report)).performScrollTo().assertExists()
        Screens.save("sentence-menu", rule.onNode(isDialog()).captureToImage().asAndroidBitmap())

        rule.onNodeWithText(text(R.string.sentence_menu_highlight_range)).performScrollTo().performClick()
        assertEquals(listOf("start range ${UNIT.index}"), calls)
    }

    @Test
    fun onAHighlightRemoveAndShareActOnTheWholePassage() {
        val placed = PlacedHighlights(UNIT.page, mapOf(UNIT.index to listOf(PASSAGE)))
        rule.setContent { AudoraTheme { NotesOverlays(NotesState(menuFor = UNIT, placed = placed), notes, reader) } }

        rule.onNodeWithText(text(R.string.sentence_hint_unhighlight)).assertExists()
        Screens.save("sentence-menu-highlighted", rule.onNode(isDialog()).captureToImage().asAndroidBitmap())

        // Share opens its choices in the same dialog; As audio shares the whole passage.
        rule.onNodeWithText(text(R.string.sentence_menu_share)).performScrollTo().performClick()
        rule.onNodeWithText(text(R.string.sentence_hint_share_highlight)).assertExists()
        rule.onNodeWithText(text(R.string.share_choices_note)).performScrollTo().assertExists()
        Screens.save("sentence-menu-share-choices", rule.onNode(isDialog()).captureToImage().asAndroidBitmap())
        rule.onNodeWithText(text(R.string.sentence_menu_share_clip)).performClick()
        assertEquals(listOf("dismiss", "passage ${PASSAGE.anchor.page}-${PASSAGE.endPage}"), calls)
    }

    @Test
    fun shareAsAPictureSendsTheWholeHighlightedQuoteAndItsPage() {
        val placed = PlacedHighlights(UNIT.page, mapOf(UNIT.index to listOf(PASSAGE)))
        rule.setContent { AudoraTheme { NotesOverlays(NotesState(menuFor = UNIT, placed = placed), notes, reader) } }

        rule.onNodeWithText(text(R.string.sentence_menu_share)).performScrollTo().performClick()
        rule.onNodeWithText(text(R.string.share_hint_picture)).assertExists()
        rule.onNodeWithText(text(R.string.share_as_picture)).performClick()
        assertEquals(listOf("dismiss", "picture ${PASSAGE.anchor.page}: ${PASSAGE.quote}"), calls)
    }

    @Test
    fun shareAsAVideoSendsTheWholeHighlightedQuoteAndItsPage() {
        val placed = PlacedHighlights(UNIT.page, mapOf(UNIT.index to listOf(PASSAGE)))
        rule.setContent { AudoraTheme { NotesOverlays(NotesState(menuFor = UNIT, placed = placed), notes, reader) } }

        rule.onNodeWithText(text(R.string.sentence_menu_share)).performScrollTo().performClick()
        rule.onNodeWithText(text(R.string.share_hint_video)).performScrollTo().assertExists()
        rule.onNodeWithText(text(R.string.share_as_video)).performScrollTo().performClick()
        assertEquals(listOf("dismiss", "video ${PASSAGE.anchor.page}: ${PASSAGE.quote}"), calls)
    }

    @Test
    fun sharePartOfThePageShowsOnlyForASentenceOnADrawnPage() {
        var menuFor by mutableStateOf(UNIT)
        rule.setContent { AudoraTheme { NotesOverlays(NotesState(menuFor = menuFor), notes, reader) } }

        rule.onNodeWithText(text(R.string.sentence_menu_share)).performScrollTo().performClick()
        rule.onNodeWithText(text(R.string.share_page_part)).assertDoesNotExist()

        menuFor = PLACED
        rule.onNodeWithText(text(R.string.sentence_menu_share)).performScrollTo().performClick()
        rule.onNodeWithText(text(R.string.share_hint_page_part)).performScrollTo().assertExists()
        rule.onNodeWithText(text(R.string.share_page_part)).performScrollTo().performClick()
        assertEquals("page part ${PLACED.index}", calls.last())
    }

    @Test
    fun shareAsAPictureOnAPlainSentenceSendsThatSentence() {
        rule.setContent { AudoraTheme { NotesOverlays(NotesState(menuFor = UNIT), notes, reader) } }

        rule.onNodeWithText(text(R.string.sentence_hint_share)).performScrollTo().assertExists()
        rule.onNodeWithText(text(R.string.sentence_menu_share)).performClick()
        rule.onNodeWithText(text(R.string.sentence_hint_share_clip)).assertExists()
        rule.onNodeWithText(text(R.string.share_as_picture)).performClick()
        assertEquals(listOf("dismiss", "picture ${UNIT.page}: ${UNIT.text}"), calls)
    }

    @Test
    fun copyTextOnAHighlightCopiesTheWholePassage() {
        val placed = PlacedHighlights(UNIT.page, mapOf(UNIT.index to listOf(PASSAGE)))
        rule.setContent { AudoraTheme { NotesOverlays(NotesState(menuFor = UNIT, placed = placed), notes, reader) } }

        rule.onNodeWithText(text(R.string.sentence_hint_copy_highlight)).performScrollTo().assertExists()
        rule.onNodeWithText(text(R.string.sentence_hint_share)).performScrollTo().assertExists()
        rule.onNodeWithText(text(R.string.sentence_menu_copy)).performScrollTo().performClick()
        assertEquals(listOf("dismiss"), calls)
        // The test's own window has focus, so it may read back what it copied.
        var copied: CharSequence? = null
        rule.runOnUiThread {
            copied = context.getSystemService(ClipboardManager::class.java)?.primaryClip?.getItemAt(0)?.text
        }
        assertEquals(PASSAGE.quote, copied?.toString())
    }

    @Test
    fun theBarWhilePickingAPassagesEndCancels() {
        rule.setContent { AudoraTheme { HighlightRangeBar(UNIT, notes::cancelRange) } }

        rule.onNodeWithText(context.getString(R.string.highlight_range_prompt, UNIT.page + 1)).assertExists()
        Screens.save("highlight-range-bar", rule.onRoot().captureToImage().asAndroidBitmap())
        rule.onNodeWithText(text(R.string.password_cancel)).performClick()
        assertEquals(listOf("cancel range"), calls)
    }

    @Test
    fun aTableReportOffersTheOtherTableModes() {
        val playback = PlaybackSnapshot(PlaybackSnapshot.Connection.CONNECTED)
        val offer = SoundedWrongOffer(
            UNIT,
            SoundedWrongReason.TABLE,
            SoundedWrongFixes.offered(SoundedWrongReason.TABLE, playback),
        )
        val picked = mutableListOf<SoundedWrongFix>()
        rule.setContent { AudoraTheme { SoundedWrongFixDialog(offer, onFix = { picked += it }, onDone = {}) } }

        rule.onNodeWithText(text(R.string.sounded_wrong_fix_hear_again)).assertExists()
        Screens.save("sounded-wrong-fixes", rule.onNode(isDialog()).captureToImage().asAndroidBitmap())
        rule.onNodeWithText(text(R.string.sounded_wrong_fix_tables_rows)).performClick()
        assertEquals(listOf<SoundedWrongFix>(SoundedWrongFix.Tables(TableMode.ROWS)), picked)
    }

    private companion object {
        val BOOK = BookId("menu-book")
        const val TEXT = "Some years ago, never mind how long precisely, I thought I would sail about a little."
        val UNIT = ReadingUnit(4, 1, 20, 20 + TEXT.length, TEXT, Lang.EN, 0, null)

        // The same sentence with its line's box on the page: a PDF's, drawn as a page.
        val PLACED = UNIT.copy(index = 2, bounds = listOf(TextBox(72f, 700f, 520f, 686f)))
        val PASSAGE = Highlight(
            id = 7,
            anchor = ResumeAnchor(BOOK, page = 3, charStart = 400, fingerprint = 0L, tailFingerprint = 0L, 1, 0L),
            endPage = 4,
            endChar = UNIT.charEnd,
            quote = "It is a way I have of driving off the spleen. $TEXT",
        )
    }
}
