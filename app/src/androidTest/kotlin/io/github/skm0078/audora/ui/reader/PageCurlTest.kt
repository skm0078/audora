// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/reader/PageCurlTest.kt
// Purpose: A PDF page curls as it turns (ADR 2026-10-29-page-curl): a drag lifts it with a shadow at the fold and
//          turns it, a slide has no fold, a corner drag turns too, and a page turned by reading on curls by itself
//          and then settles. Screenshots go to CI's "player-screens" for review.

package io.github.skm0078.audora.ui.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.PageFlipConfig
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.pdf.render.PageTransform
import io.github.skm0078.audora.ui.Screens
import io.github.skm0078.audora.ui.reader.flip.PageTurn
import io.github.skm0078.audora.ui.reader.flip.TurnStyle
import io.github.skm0078.audora.ui.theme.AudoraTheme
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PageCurlTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val turns = mutableListOf<Int>()
    private var state by mutableStateOf(stateOn(PAGE, reading = false))

    private val actions = object : ReaderActions by NoReaderActions {
        override fun onGoToPage(page: Int) {
            turns += page
        }
    }

    private fun show(turn: PageTurn) {
        rule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(PHONE)) {
                AudoraTheme { PageView(state, units(state.page), actions, PageTone.ORIGINAL, PageFlipConfig(), turn) }
            }
        }
        rule.waitForIdle()
    }

    private fun pageNode(page: Int) =
        rule.onNodeWithContentDescription(context.getString(R.string.reader_page_image, page + 1))

    @Test
    fun aDragLiftsThePageWithAShadowAtTheFoldAndTurnsIt() {
        show(PageTurn())
        val bounds = pageNode(PAGE).fetchSemanticsNode().boundsInRoot
        val flat = shadedPixels(rule.onRoot().captureToImage().asAndroidBitmap(), bounds)
        pageNode(PAGE).performTouchInput {
            down(center)
            moveBy(Offset(-width * DRAG_SHARE, 0f))
        }
        rule.waitForIdle()
        val mid = rule.onRoot().captureToImage().asAndroidBitmap()
        Screens.save("page-curl-mid-drag", mid)
        assertTrue("a shadow beside the fold", shadedPixels(mid, bounds) - flat >= SHADED)
        pageNode(PAGE).performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(listOf(PAGE + 1), turns)
    }

    @Test
    fun aSlideMovesThePageFlatWithNoFold() {
        show(PageTurn(style = TurnStyle.SLIDE))
        val bounds = pageNode(PAGE).fetchSemanticsNode().boundsInRoot
        val flat = shadedPixels(rule.onRoot().captureToImage().asAndroidBitmap(), bounds)
        pageNode(PAGE).performTouchInput {
            down(center)
            moveBy(Offset(-width * DRAG_SHARE, 0f))
        }
        rule.waitForIdle()
        val sliding = shadedPixels(rule.onRoot().captureToImage().asAndroidBitmap(), bounds)
        assertTrue("no fold, no shadow", sliding - flat < SHADED)
        pageNode(PAGE).performTouchInput { up() }
    }

    @Test
    fun aDragFromTheBottomCornerCurlsItUpAndTurns() {
        show(PageTurn())
        pageNode(PAGE).performTouchInput {
            down(Offset(width * CORNER_SHARE, height * CORNER_SHARE))
            moveBy(Offset(-width * DRAG_SHARE, -height * LIFT_SHARE))
        }
        rule.waitForIdle()
        Screens.save("page-curl-corner", rule.onRoot().captureToImage().asAndroidBitmap())
        pageNode(PAGE).performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(listOf(PAGE + 1), turns)
    }

    @Test
    fun readingOnToTheNextPageCurlsTheOldOneAwayByItself() {
        show(PageTurn())
        rule.mainClock.autoAdvance = false
        state = stateOn(PAGE + 1, reading = true)
        rule.mainClock.advanceTimeBy(PART_OF_A_TURN_MS)
        val turning = rule.onRoot().captureToImage().asAndroidBitmap()
        Screens.save("page-curl-reading-on", turning)
        rule.mainClock.advanceTimeBy(PAST_A_TURN_MS)
        val settled = rule.onRoot().captureToImage().asAndroidBitmap()
        // Without the curl the new page would already show as it does once settled.
        assertFalse("the old page curling away part way", turning.sameAs(settled))
        assertEquals("no finger turned it", emptyList<Int>(), turns)
        rule.mainClock.autoAdvance = true
    }

    /**
     * Grey pixels over the page, sampled on a grid: a plain white page has only its number's edges; a curl adds its
     * shadow and the shade on the back of the paper, hundreds more even at a low density.
     */
    private fun shadedPixels(image: Bitmap, page: Rect): Int {
        val xs = page.left.roundToInt().coerceAtLeast(0) until page.right.roundToInt().coerceAtMost(image.width)
        val ys = page.top.roundToInt().coerceAtLeast(0) until page.bottom.roundToInt().coerceAtMost(image.height)
        return ys.step(SAMPLE_PX).sumOf { y ->
            xs.step(SAMPLE_PX).count { x ->
                val pixel = image.getPixel(x, y)
                val light = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / CHANNELS
                light in DARKEST_SHADE..LIGHTEST_SHADE
            }
        }
    }

    private fun units(page: Int): List<ReadingUnit> = listOf("Article one.", "Article two.").mapIndexed { index, line ->
        ReadingUnit(page, index, index * line.length, (index + 1) * line.length, line, Lang.EN, 0, null)
    }

    /** Page [page] shown and drawn, its neighbours drawn ahead; [reading] aloud from its start or paused. */
    private fun stateOn(page: Int, reading: Boolean): ReaderState {
        val nearby = listOf(page - 1, page, page + 1).filter { it in 0 until PAGES }.associateWith(::pageImage)
        return ReaderState(
            bookId = BOOK,
            title = "Curl book",
            document = DocumentState.Open(pageCount = PAGES),
            page = page,
            content = PageContent.Ready(units(page)),
            following = reading,
            view = ReaderView.PAGE,
            pageImage = PageImageState.Ready(page, requireNotNull(nearby[page])),
            nearbyPages = nearby,
            playback = PlaybackSnapshot(
                connection = PlaybackSnapshot.Connection.CONNECTED,
                position = ReadingPosition(BOOK, page, 0).takeIf { reading },
                wantsToPlay = reading,
            ),
        )
    }

    /** A white page with its number in the middle. */
    private fun pageImage(page: Int): PageImage {
        val bitmap = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            drawText("${page + 1}", PAGE_WIDTH / 2f, PAGE_HEIGHT / 2f, Paint().apply { textSize = NUMBER_SIZE })
        }
        return TestPage(bitmap.asImageBitmap())
    }

    private class TestPage(private val bitmap: ImageBitmap) : PageImage {
        override val transform = PageTransform.upright(PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat())
        override val aspectRatio = PAGE_WIDTH / PAGE_HEIGHT.toFloat()

        override fun imageBitmap() = bitmap
    }

    private companion object {
        val PHONE = DpSize(411.dp, 914.dp)
        val BOOK = BookId("curl-book")
        const val PAGES = 10
        const val PAGE = 3
        const val PAGE_WIDTH = 612
        const val PAGE_HEIGHT = 792
        const val NUMBER_SIZE = 120f
        const val DRAG_SHARE = 0.4f
        const val CORNER_SHARE = 0.92f
        const val LIFT_SHARE = 0.1f
        const val SAMPLE_PX = 4
        const val CHANNELS = 3
        const val DARKEST_SHADE = 60
        const val LIGHTEST_SHADE = 245
        const val SHADED = 300

        // Half of a reading turn (250 ms), then well past it.
        const val PART_OF_A_TURN_MS = 120L
        const val PAST_A_TURN_MS = 600L
    }
}
