// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/video/QuoteVideoFrames.kt
// Purpose: What a quote video shows, frame by frame, and the "AI voice" label the writer adds to every frame.

package io.github.skm0078.audora.playback.export.video

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/**
 * The pictures of a quote video. Most frames look like the one before (the highlight moves a few times a second, the
 * video runs at 30 frames a second), so the writer draws only when [lookOf] changes and shows the last picture again
 * otherwise: about one frame in ten is drawn.
 */
interface QuoteVideoFrames {
    /** Frames with the same look are drawn the same: for a quote card, the index of the word lit. */
    fun lookOf(frame: Int): Int

    /** Draws [frame] over the whole of [canvas] (the video's size), opaque: no earlier frame shows through. */
    fun draw(canvas: Canvas, frame: Int)
}

/**
 * The label every frame carries, drawn by the writer itself after the frames, so no video can leave it out: a voice
 * made by a computer is said to be one (the maintainer's rule, as the ListenLock mark on a card). [text] is the
 * label in the listener's language, such as "AI voice".
 */
class AiVoiceLabel(val text: String) {
    init {
        require(text.isNotBlank()) { "The AI voice label must say something" }
    }

    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BACKING }

    /** Top left, in a dark pill that reads on any card colour; sized to the frame's height. */
    internal fun draw(canvas: Canvas) {
        val unit = canvas.height / UNITS
        ink.textSize = unit * TEXT_UNITS
        val width = ink.measureText(text)
        val metrics = ink.fontMetrics
        val box = RectF(
            unit * MARGIN_UNITS,
            unit * MARGIN_UNITS,
            unit * (MARGIN_UNITS + 2 * PADDING_UNITS) + width,
            unit * (MARGIN_UNITS + 2 * PADDING_UNITS) + (metrics.descent - metrics.ascent),
        )
        canvas.drawRoundRect(box, box.height() / 2, box.height() / 2, pill)
        canvas.drawText(text, box.left + unit * PADDING_UNITS, box.top + unit * PADDING_UNITS - metrics.ascent, ink)
    }

    private companion object {
        /** Sizes in 1/640ths of the frame's height: at 1280 px, 2 px each. */
        const val UNITS = 640f
        const val TEXT_UNITS = 13f
        const val MARGIN_UNITS = 16f
        const val PADDING_UNITS = 6f
        const val BACKING = 0x99000000.toInt()
    }
}
