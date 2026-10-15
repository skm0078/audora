// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/units/LineBoxes.kt
// Purpose: Merges a sentence's glyph boxes into one box per line, for highlighting (P4-03).

package io.github.skm0078.audora.pdf.units

import io.github.skm0078.audora.core.model.TextBox
import io.github.skm0078.audora.pdf.layout.ReadingText

internal class LineBoxes(private val lineOverlapRatio: Float) {
    /** One box per line covered by [start] until [end] of [text]; null when no glyph has a box. */
    fun of(text: ReadingText, start: Int, end: Int): List<TextBox>? {
        val lines = ArrayList<TextBox>()
        var current: TextBox? = null
        for (offset in start until end) {
            val box = (if (text.text[offset].isWhitespace()) null else text.boxAt(offset)) ?: continue
            val line = current
            current = when {
                line == null -> box

                sameLine(line, box) -> line.union(box)

                else -> {
                    lines.add(line)
                    box
                }
            }
        }
        current?.let { lines.add(it) }
        return lines.ifEmpty { null }
    }

    /** Same line: overlaps vertically enough and does not jump back to the left (a wrapped line). */
    private fun sameLine(line: TextBox, box: TextBox): Boolean {
        val overlap = minOf(line.top, box.top) - maxOf(line.bottom, box.bottom)
        val minHeight = minOf(line.height, box.height)
        val overlapsEnough = minHeight <= 0f || overlap >= minHeight * lineOverlapRatio
        return overlapsEnough && box.left >= line.left
    }
}
