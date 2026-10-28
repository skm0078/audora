// File: playback/src/androidTest/kotlin/io/github/skm0078/audora/playback/export/video/QuoteVideoPainterDeviceTest.kt
// Purpose: ADR 2026-10-28-quote-video on a device: the video's words light one at a time in the accent's pill, a
//          60-word Bengali quote stays inside its space above the card's credit, and the label sizes to the frame.

package io.github.skm0078.audora.playback.export.video

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.share.QuoteReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuoteVideoPainterDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun painter(quote: String, script: Lang) = QuoteVideoPainter(
        QuoteVideoText(quote, QuoteVideoFonts.of(context, script), INK, ACCENT),
        WIDTH,
        HEIGHT,
    )

    private fun drawn(painter: QuoteVideoPainter, lit: Int): Bitmap =
        Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).also { bitmap ->
            Canvas(bitmap).apply {
                drawColor(BACKGROUND)
                painter.draw(this, lit)
            }
        }

    @Test
    fun theWordsAreTheReadingsWordsAndOnlyALitOneHasThePill() {
        val painter = painter(ENGLISH, Lang.EN)
        assertEquals(QuoteReading.wordsOf(ENGLISH).size, painter.wordCount)

        assertFalse("no pill before a word is said", drawn(painter, NONE).has(ACCENT))
        assertTrue("the first word's pill", drawn(painter, 0).has(ACCENT))
        assertTrue("the last word's pill", drawn(painter, painter.wordCount - 1).has(ACCENT))
    }

    @Test
    fun aLongBengaliQuoteStaysAboveTheCredit() {
        val long = List(LONG_REPEATS) { BENGALI }.joinToString(" ")
        val bitmap = drawn(painter(long, Lang.BN), NONE)
        val creditTop = (HEIGHT * CREDIT_SHARE).toInt()
        for (y in creditTop until HEIGHT step ROW_STEP) {
            for (x in 0 until WIDTH step COLUMN_STEP) {
                assertEquals("nothing drawn at ($x, $y)", BACKGROUND, bitmap.getPixel(x, y))
            }
        }
        assertTrue("the words are drawn", bitmap.has(INK))
    }

    @Test
    fun theLabelSizesToTheFrameNotTheCanvas() {
        val small = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val large = Bitmap.createBitmap(WIDTH * 2, HEIGHT * 2, Bitmap.Config.ARGB_8888)
        val label = AiVoiceLabel("AI voice")
        label.draw(Canvas(small))
        label.draw(Canvas(large), frameHeight = HEIGHT)
        // The same pill in the same place: a pixel just past the small frame's pill is empty on both.
        val outside = (WIDTH * OUTSIDE_SHARE).toInt()
        assertEquals(Color.alpha(small.getPixel(outside, PILL_ROW)), Color.alpha(large.getPixel(outside, PILL_ROW)))
        assertTrue("the pill is drawn", Color.alpha(large.getPixel(PILL_COLUMN, PILL_ROW)) > 0)
    }

    private fun Bitmap.has(color: Int): Boolean {
        for (y in 0 until height step ROW_STEP) {
            for (x in 0 until width step COLUMN_STEP) {
                if (getPixel(x, y) == color) return true
            }
        }
        return false
    }

    private companion object {
        const val WIDTH = 720
        const val HEIGHT = 1280
        const val NONE = -1
        const val INK = Color.WHITE
        const val ACCENT = 0xFFFFD66B.toInt()
        const val BACKGROUND = 0xFF16161C.toInt()
        const val ROW_STEP = 2
        const val COLUMN_STEP = 2
        const val LONG_REPEATS = 6

        // The card's credit starts below this share of the height; the painter keeps above 0.72.
        const val CREDIT_SHARE = 0.73f

        // On a 1280 px frame the pill runs from 32 px to about 160 px across and 32 to 90 px down.
        const val OUTSIDE_SHARE = 0.4f
        const val PILL_ROW = 60
        const val PILL_COLUMN = 40
        const val ENGLISH = "All human beings are born free and equal in dignity and rights."
        const val BENGALI = "সমস্ত মানুষ স্বাধীনভাবে সমান মর্যাদা এবং অধিকার নিয়ে জন্মগ্রহণ করে।"
    }
}
