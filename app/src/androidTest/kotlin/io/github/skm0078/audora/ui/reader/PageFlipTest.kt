// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/reader/PageFlipTest.kt
// Purpose: Swiping a PDF page on the reader screen (ADR 2026-10-28-page-flip): a swipe turns it with the next page
//          sliding in, a nudge or a swipe past the first page does not, and "Read from here" reads on from the page
//          turned to. Screenshots (English, Hindi and Bengali at double text size) go to CI's "player-screens".

package io.github.skm0078.audora.ui.reader

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.Locales
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.then
import androidx.compose.ui.text.intl.LocaleList as ComposeLocales
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
import io.github.skm0078.audora.pdf.render.PageTransform
import io.github.skm0078.audora.pdf.render.ViewPoint
import io.github.skm0078.audora.ui.Screens
import io.github.skm0078.audora.ui.theme.AudoraTheme
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PageFlipTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val turns = mutableListOf<Int>()
    private val readFrom = mutableListOf<ReadingUnit>()

    private fun show(page: Int, language: String = "en", fontScale: Float = 1f) {
        // The one-time gesture tips grow the controls at the bottom; other tests may leave them pending or not.
        OnboardingPreferences(context).gestureTipsSeen = true
        val state = stateOn(page)
        rule.setContent {
            val config = DeviceConfigurationOverride.ForcedSize(TEST_PHONE_SIZE) then
                DeviceConfigurationOverride.FontScale(fontScale) then
                DeviceConfigurationOverride.Locales(ComposeLocales(language))
            DeviceConfigurationOverride(config) {
                AudoraTheme { ReaderScreen(state, ReaderConfig(), Recording()) }
            }
        }
        rule.waitForIdle()
    }

    private fun pageNode(page: Int, language: String = "en") =
        rule.onNodeWithContentDescription(text(R.string.reader_page_image, language, page + 1))

    @Test
    fun aSwipeLeftSlidesTheNextPageInAndTurnsToIt() {
        show(PAGE)
        pageNode(PAGE).performTouchInput {
            down(center)
            moveBy(Offset(-width * DRAG_SHARE, 0f))
        }
        rule.waitForIdle()
        Screens.save("page-flip-mid-swipe", rule.onRoot().captureToImage().asAndroidBitmap())
        pageNode(PAGE).performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(listOf(PAGE + 1), turns)
    }

    @Test
    fun aSlowNudgeSettlesBack() {
        show(PAGE)
        pageNode(PAGE).performTouchInput {
            swipe(center, center + Offset(-width * NUDGE_SHARE, 0f), durationMillis = SLOW_MS)
        }
        rule.waitForIdle()
        assertEquals(emptyList<Int>(), turns)
    }

    @Test
    fun theFirstPageHasNothingBeforeIt() {
        show(0)
        pageNode(0).performTouchInput { swipeRight() }
        rule.waitForIdle()
        assertEquals(emptyList<Int>(), turns)
    }

    @Test
    fun readFromHereInHindiAtDoubleTextSize() = readFromHere("hi")

    @Test
    fun readFromHereInBengaliAtDoubleTextSize() = readFromHere("bn")

    /** Turned away from the reading position: "Read from here" reads on from the shown page's first sentence. */
    private fun readFromHere(language: String) {
        show(PAGE, language, fontScale = 2f)
        Screens.save("page-flip-read-from-here-$language-2x", rule.onRoot().captureToImage().asAndroidBitmap())
        rule.onNodeWithText(text(R.string.reader_read_from_here, language)).assertIsDisplayed().performClick()
        rule.waitForIdle()
        assertEquals(listOf(PAGE), readFrom.map { it.page })
        assertEquals(0, readFrom.single().index)
    }

    private fun text(id: Int, language: String, vararg args: Any): String {
        val config = Configuration(context.resources.configuration)
        config.setLocales(LocaleList(Locale.forLanguageTag(language)))
        return context.createConfigurationContext(config).getString(id, *args)
    }

    /** Page [page] of [PAGES] shown and drawn, its neighbours drawn ahead, reading paused back on the first page. */
    private fun stateOn(page: Int): ReaderState {
        val units = listOf("Article one.", "Article two.").mapIndexed { index, line ->
            ReadingUnit(page, index, index * line.length, (index + 1) * line.length, line, Lang.EN, 0, null)
        }
        val nearby = listOf(page - 1, page, page + 1).filter { it in 0 until PAGES }.associateWith(::pageImage)
        return ReaderState(
            bookId = BOOK,
            title = "Universal Declaration",
            document = DocumentState.Open(pageCount = PAGES),
            page = page,
            content = PageContent.Ready(units),
            following = false,
            view = ReaderView.PAGE,
            pageImage = PageImageState.Ready(page, requireNotNull(nearby[page])),
            nearbyPages = nearby,
            playback = PlaybackSnapshot(
                connection = PlaybackSnapshot.Connection.CONNECTED,
                position = ReadingPosition(BOOK, 0, 0),
            ),
        )
    }

    /** A plain page with its number on it, so the screenshots show which page slides in. */
    private fun pageImage(page: Int): PageImage {
        val bitmap = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(android.graphics.Color.WHITE)
            drawText("${page + 1}", PAGE_WIDTH / 2f, PAGE_HEIGHT / 2f, Paint().apply { textSize = NUMBER_SIZE })
        }
        return TestPage(bitmap.asImageBitmap())
    }

    private class TestPage(private val bitmap: ImageBitmap) : PageImage {
        override val transform = PageTransform.upright(PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat())
        override val aspectRatio = PAGE_WIDTH / PAGE_HEIGHT.toFloat()

        override fun imageBitmap() = bitmap
    }

    private inner class Recording : ReaderActions {
        override fun onBack() = Unit
        override fun onPreviousPage() = Unit
        override fun onNextPage() = Unit
        override fun onGoToPage(page: Int) {
            turns += page
        }
        override fun onShowReadingPosition() = Unit
        override fun onSentenceTapped(unit: ReadingUnit) {
            readFrom += unit
        }
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
        val TEST_PHONE_SIZE = DpSize(411.dp, 914.dp)
        val BOOK = BookId("flip-book")
        const val PAGES = 10
        const val PAGE = 3
        const val PAGE_WIDTH = 612
        const val PAGE_HEIGHT = 792
        const val NUMBER_SIZE = 120f
        const val DRAG_SHARE = 0.4f
        const val NUDGE_SHARE = 0.1f
        const val SLOW_MS = 1_500L
    }
}
