// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/video/QuoteVideoPainter.kt
// Purpose: Draws a quote video's words with the one being said lit, and the frames that put them on the card's
//          backdrop (ADR 2026-10-28-quote-video).

package io.github.skm0078.audora.playback.export.video

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import io.github.skm0078.audora.core.share.QuoteReading
import io.github.skm0078.audora.core.share.VideoTimeline

/**
 * The quote's look on a video: its [typeface], [ink] for the words (said ones full, the rest softer) and [accent], the
 * pill behind the word being said. Colours are ARGB ints, as the card's look has them.
 */
data class QuoteVideoText(val quote: String, val typeface: Typeface, val ink: Int, val accent: Int)

/**
 * The words laid out once, at the largest size that fits the space between the top label and the card's credit, for a
 * [widthPx] x [heightPx] frame; [draw] lights one word. The card screen's preview draws with this same painter, so the
 * preview is the video's own words. Not thread-safe: one painter per thread.
 */
class QuoteVideoPainter(private val text: QuoteVideoText, widthPx: Int, heightPx: Int) {
    init {
        require(widthPx > 0 && heightPx > 0) { "A frame of $widthPx x $heightPx" }
    }

    private val unit = widthPx / DESIGN_WIDTH
    private val area = Rect(
        (SIDE * unit).toInt(),
        (heightPx * TOP_SHARE).toInt(),
        widthPx - (SIDE * unit).toInt(),
        (heightPx * BOTTOM_SHARE).toInt(),
    )
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = text.typeface
        color = text.ink
    }
    private val words: List<IntRange> = WORD.findAll(text.quote).map { it.range }.toList()
    private val layout: StaticLayout = fitted()
    private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = text.accent }
    private val litInk = if (luminance(text.accent) > LIGHT_ACCENT) DARK_INK else Color.WHITE
    private val wordPath = Path()
    private val wordBox = RectF()

    /** How many words the quote has: [draw]'s `lit` is an index below it. */
    val wordCount: Int get() = words.size

    /**
     * The words onto [canvas] (the frame's size), [lit] in its pill: the words before it in full ink, those after it
     * softer. [lit] below 0 (nothing said yet, or the preview) draws every word in full ink.
     */
    fun draw(canvas: Canvas, lit: Int) {
        val shown = if (lit in words.indices) spoken(lit) else layout
        canvas.save()
        canvas.translate(area.left.toFloat(), area.top + (area.height() - layout.height) / 2f)
        if (lit in words.indices) {
            val range = words[lit]
            layout.getSelectionPath(range.first, range.last + 1, wordPath)
            wordPath.computeBounds(wordBox, true)
            val pad = PILL_PAD * unit
            wordBox.inset(-pad, -pad / 2)
            canvas.drawRoundRect(wordBox, wordBox.height() / 2, wordBox.height() / 2, pill)
        }
        shown.draw(canvas)
        canvas.restore()
    }

    /** The same lines with the words after [lit] softer and [lit] in the pill's ink: the breaks are [layout]'s. */
    private fun spoken(lit: Int): StaticLayout {
        val styled = SpannableString(text.quote)
        val range = words[lit]
        styled.setSpan(ForegroundColorSpan(litInk), range.first, range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (range.last + 1 < text.quote.length) {
            val ahead = Color.argb(AHEAD_ALPHA, Color.red(text.ink), Color.green(text.ink), Color.blue(text.ink))
            styled.setSpan(
                ForegroundColorSpan(ahead),
                range.last + 1,
                text.quote.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        return build(styled, layout.paint.textSize)
    }

    /** Steps the size down from the largest until the words fit the area's height. */
    private fun fitted(): StaticLayout {
        var size = MAX_TEXT * unit
        var built = build(text.quote, size)
        while (built.height > area.height() && size > MIN_TEXT * unit) {
            size -= STEP * unit
            built = build(text.quote, size)
        }
        return built
    }

    private fun build(source: CharSequence, size: Float): StaticLayout {
        paint.textSize = size
        return StaticLayout.Builder.obtain(source, 0, source.length, TextPaint(paint), area.width())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, LINE_SPACING)
            .setIncludePad(false)
            .build()
    }

    private companion object {
        // QuoteReading's word, so index i here is the reading's word i.
        val WORD = QuoteReading.WORD

        // Sizes in card dp (a card is 360 wide), the quote card's own: 32 at the sides, text from 34 down to 16.
        const val DESIGN_WIDTH = 360f
        const val SIDE = 32f
        const val MAX_TEXT = 34f
        const val MIN_TEXT = 16f
        const val STEP = 1f
        const val PILL_PAD = 4f
        const val LINE_SPACING = 1.45f

        // Below the "AI voice" label at the top; above the card's credit (title, author, page) at the bottom.
        const val TOP_SHARE = 0.14f
        const val BOTTOM_SHARE = 0.72f

        // Words still to be said, at about 60 % of the ink.
        const val AHEAD_ALPHA = 150
        const val LIGHT_ACCENT = 0.5f
        val DARK_INK = Color.rgb(0x1B, 0x1B, 0x21)

        fun luminance(color: Int): Float = Color.luminance(color)
    }
}

/**
 * The frames of a quote video: [backdrop] (the card without its quote: look, cover or picture, credit and the
 * ListenLock mark, at the video's size) with [painter]'s words over it, the word [timeline] says lit. A frame looks
 * like the one before until the next word starts, so the writer draws about one frame in ten.
 */
class QuoteVideoCard(
    private val backdrop: Bitmap,
    private val painter: QuoteVideoPainter,
    private val timeline: VideoTimeline,
) : QuoteVideoFrames {
    override fun lookOf(frame: Int): Int = timeline.wordAt(frame)

    override fun draw(canvas: Canvas, frame: Int) {
        canvas.drawBitmap(backdrop, null, Rect(0, 0, canvas.width, canvas.height), null)
        painter.draw(canvas, timeline.wordAt(frame))
    }
}
