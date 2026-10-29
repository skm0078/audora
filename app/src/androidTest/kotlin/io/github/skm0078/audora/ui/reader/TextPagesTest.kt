// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/reader/TextPagesTest.kt
// Purpose: "Turn pages" in the text view (ADR 2026-10-28-text-pages): a swipe or the arrow turns a screenful with no
//          sentence cut at the top, the last screen turns on to the book's next page, the first turns back to the page
//          before (to open at its end), a turn slides in from the side it came from or curls over the text without
//          moving it (ADR 2026-10-29-page-curl), and reading on keeps the spoken sentence on screen. Bengali at double
//          text size; screenshots go to CI's "player-screens".

package io.github.skm0078.audora.ui.reader

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.ui.Screens
import io.github.skm0078.audora.ui.reader.flip.PageTurn
import io.github.skm0078.audora.ui.reader.flip.TurnStyle
import io.github.skm0078.audora.ui.theme.AudoraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TextPagesTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val calls = mutableListOf<String>()
    private val turnBack = TextTurnBack()
    private var state by mutableStateOf(stateOn(page = 1, reading = null))

    private val actions = object : ReaderActions by NoReaderActions {
        override fun onNextPage() {
            calls += "next page"
        }

        override fun onPreviousPage() {
            calls += "previous page"
        }
    }

    // One sentence per row, so the list's rows are the sentences and the test can find them by index.
    private var look = ReadingLook(turnPages = true, reflow = false)

    private fun show() {
        rule.setContent {
            val config = DeviceConfigurationOverride.ForcedSize(PHONE) then DeviceConfigurationOverride.FontScale(2f)
            DeviceConfigurationOverride(config) {
                AudoraTheme { Sentences(state, units(state.page), actions, look, turnBack = turnBack) }
            }
        }
        rule.waitForIdle()
    }

    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    @Test
    fun aSwipeTurnsAScreenfulAndTheLastTurnsOnToTheNextPage() {
        show()
        Screens.save("text-pages-bn-2x", rule.onRoot().captureToImage().asAndroidBitmap())
        val first = shownFirst()
        rule.onRoot().performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertTrue("turned on", shownFirst() > first)
        rule.onNodeWithText(text(R.string.text_pages_continued, 2, PAGES)).assertExists()
        Screens.save("text-pages-bn-2x-turned", rule.onRoot().captureToImage().asAndroidBitmap())

        // On to the page's end: the next turn shows the book's next page.
        repeat(SENTENCES) {
            if (calls.isEmpty()) rule.onNodeWithContentDescription(text(R.string.text_pages_next)).performClick()
            rule.waitForIdle()
        }
        assertEquals(listOf("next page"), calls)
    }

    @Test
    fun theFirstScreenTurnsBackToThePageBeforeAtItsEnd() {
        show()
        rule.onRoot().performTouchInput { swipeRight() }
        rule.waitForIdle()
        assertEquals(listOf("previous page"), calls)
        assertTrue("the page before opens at its end", turnBack.openAtEnd)
    }

    @Test
    fun aTurnSlidesTheNewScreenInFromTheRightAndSettlesWhereItBelongs() {
        look = look.copy(turn = PageTurn(style = TurnStyle.SLIDE))
        show()
        val settled = leftOf(shownFirst())
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription(text(R.string.text_pages_next)).performClick()
        rule.mainClock.advanceTimeBy(PART_OF_A_TURN_MS)
        val turned = shownFirst()
        assertTrue("turned on", turned > 0)
        assertTrue("sliding in from the right", leftOf(turned) > settled + 1f)
        rule.mainClock.advanceTimeBy(PAST_A_TURN_MS)
        assertEquals(settled, leftOf(turned), 0.5f)
        rule.mainClock.autoAdvance = true
    }

    @Test
    fun aCurlDrawsOverTheTextWhileItTurnsAndNeverMovesIt() {
        show()
        val settled = leftOf(shownFirst())
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription(text(R.string.text_pages_next)).performClick()
        // The screen is kept as a picture first (on the GPU, in real time), then the list turns under it.
        var waited = 0
        while (shownFirst() == 0 && waited < MAX_WAIT_FRAMES) {
            rule.mainClock.advanceTimeByFrame()
            Thread.sleep(FRAME_MS)
            waited++
        }
        val turned = shownFirst()
        assertTrue("turned on", turned > 0)
        rule.mainClock.advanceTimeBy(PART_OF_A_CURL_MS)
        assertEquals("a curl never moves the text itself", settled, leftOf(turned), 0.5f)
        val mid = rule.onRoot().captureToImage().asAndroidBitmap()
        Screens.save("text-pages-bn-2x-curl", mid)
        rule.mainClock.advanceTimeBy(PAST_A_CURL_MS)
        val end = rule.onRoot().captureToImage().asAndroidBitmap()
        assertFalse("the old screen curled over the new one part way", mid.sameAs(end))
        rule.mainClock.autoAdvance = true
    }

    @Test
    fun readingOnKeepsTheSpokenSentenceOnScreen() {
        show()
        state = stateOn(page = 1, reading = SENTENCES - 1)
        rule.waitForIdle()
        rule.onNodeWithText(units(1).last().text).assertExists()
        assertTrue("the list moved on", shownFirst() > 0)
    }

    /** The first row on screen: the list composes only the rows it shows. */
    private fun shownFirst(): Int = units(state.page).indexOfFirst { unit ->
        rule.onAllNodes(hasText(unit.text)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun leftOf(row: Int): Float =
        rule.onNodeWithText(units(state.page)[row].text).fetchSemanticsNode().boundsInRoot.left

    private fun units(page: Int): List<ReadingUnit> = List(SENTENCES) { index ->
        val line = "$BENGALI ${index + 1}."
        ReadingUnit(page, index, index * LINE_CHARS, index * LINE_CHARS + line.length, line, Lang.BN, 0, null)
    }

    private fun stateOn(page: Int, reading: Int?): ReaderState = ReaderState(
        bookId = BOOK,
        title = "Sample",
        document = DocumentState.Open(pageCount = PAGES),
        page = page,
        content = PageContent.Ready(units(page)),
        following = true,
        view = ReaderView.TEXT,
        playback = PlaybackSnapshot(
            connection = PlaybackSnapshot.Connection.CONNECTED,
            position = reading?.let { ReadingPosition(BOOK, page, it * LINE_CHARS) },
            wantsToPlay = reading != null,
        ),
    )

    private companion object {
        val BOOK = BookId("text-pages-test")
        val PHONE = DpSize(411.dp, 700.dp)
        const val PAGES = 5

        // A turn's slide takes 200 ms: part way it is still to the side, past it the screen is back in place.
        const val PART_OF_A_TURN_MS = 64L
        const val PAST_A_TURN_MS = 400L

        // A curl by hand takes 400 ms at normal speed.
        const val PART_OF_A_CURL_MS = 150L
        const val PAST_A_CURL_MS = 800L
        const val MAX_WAIT_FRAMES = 60
        const val FRAME_MS = 16L
        const val SENTENCES = 30
        const val LINE_CHARS = 200
        const val BENGALI = "সমস্ত মানুষ স্বাধীনভাবে সমান মর্যাদা এবং অধিকার নিয়ে জন্মগ্রহণ করে"
    }
}
