// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/layout/TextTurn.kt
// Purpose: Finds which way a page's text runs (sideways tables, upside-down pages) and turns its boxes upright.

package io.github.skm0078.audora.pdf.layout

import io.github.skm0078.audora.core.config.LayoutConfig
import io.github.skm0078.audora.core.model.TextBox
import io.github.skm0078.audora.pdf.extract.PageText
import kotlin.math.abs
import kotlin.math.max

/**
 * How a page's glyph boxes are turned so its text runs left to right (ADR-032). PDF space has y pointing up; a turn
 * is a rotation about the origin, so distances and overlaps are kept and only the frame changes.
 */
enum class TextTurn {
    /** The text already runs left to right. */
    NONE,

    /** Text running up the page (a table set sideways, read with the head tilted left): turned clockwise. */
    CLOCKWISE,

    /** Text running down the page: turned counter-clockwise. */
    COUNTER_CLOCKWISE,

    /** Text running right to left, upside down. */
    HALF,
    ;

    /** [box] in the turned frame. */
    fun apply(box: TextBox): TextBox = when (this) {
        NONE -> box

        // (x, y) → (y, −x)
        CLOCKWISE -> TextBox(left = box.bottom, top = -box.left, right = box.top, bottom = -box.right)

        // (x, y) → (−y, x)
        COUNTER_CLOCKWISE -> TextBox(left = -box.top, top = box.right, right = -box.bottom, bottom = box.left)

        // (x, y) → (−x, −y)
        HALF -> TextBox(left = -box.right, top = -box.bottom, right = -box.left, bottom = -box.top)
    }
}

/**
 * Reads the direction from neighbouring glyphs inside words: the step from one glyph's centre to the next. A page
 * whose steps mostly go up, down or left (at least [LayoutConfig.minTurnedShare] of at least
 * [LayoutConfig.minDirectionSteps] steps) is turned. Steps longer than [LayoutConfig.maxGlyphStepEm] glyph sizes
 * (a jump to another line or cell) are not counted. Indic pre-base signs step backwards, but far less often than
 * forwards, so they never tip a page.
 */
internal object TextDirection {
    fun of(page: PageText, config: LayoutConfig): TextTurn {
        val counts = IntArray(TextTurn.entries.size)
        var previous: TextBox? = null
        for (offset in page.text.indices) {
            val box = if (page.text[offset].isWhitespace()) null else page.boxAt(offset)
            val before = previous
            previous = box
            if (box == null || before == null) continue
            stepTurn(before, box, config)?.let { counts[it.ordinal]++ }
        }
        val steps = counts.sum()
        if (steps < config.minDirectionSteps) return TextTurn.NONE
        val turned = TextTurn.entries.filter { it != TextTurn.NONE }.maxBy { counts[it.ordinal] }
        return if (counts[turned.ordinal] >= config.minTurnedShare * steps) turned else TextTurn.NONE
    }

    /** The turn that makes the step from [a] to [b] run left to right; null when the step is too long or empty. */
    private fun stepTurn(a: TextBox, b: TextBox, config: LayoutConfig): TextTurn? {
        val dx = (b.left + b.right - a.left - a.right) / 2
        val dy = (b.top + b.bottom - a.top - a.bottom) / 2
        val size = max(max(a.width, a.height), max(b.width, b.height))
        val step = max(abs(dx), abs(dy))
        if (size <= 0f || step <= 0f || step > config.maxGlyphStepEm * size) return null
        return when {
            abs(dx) >= abs(dy) -> if (dx > 0) TextTurn.NONE else TextTurn.HALF
            dy > 0 -> TextTurn.CLOCKWISE
            else -> TextTurn.COUNTER_CLOCKWISE
        }
    }
}
