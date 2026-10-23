// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/layout/LineBuilder.kt
// Purpose: Groups one block's glyph runs into visual lines with their size and boldness.

package io.github.skm0078.audora.pdf.layout

import io.github.skm0078.audora.core.config.LayoutConfig
import io.github.skm0078.audora.core.model.TextBox

internal class LineBuilder(private val config: LayoutConfig) {
    /** Groups a block's runs into lines, top to bottom, each line's runs left to right. */
    fun linesOf(runs: List<GlyphRun>, styles: GlyphStyles): List<TextLine> {
        val lines = ArrayList<MutableList<GlyphRun>>()
        var lineBox: TextBox? = null
        for (run in runs.sortedByDescending { it.box.top }) {
            val current = lineBox
            if (current != null && sameLine(current, run.box) && lines.last().none { stacked(it, run) }) {
                lines.last().add(run)
                lineBox = current.union(run.box)
            } else {
                lines.add(mutableListOf(run))
                lineBox = run.box
            }
        }
        return lines.map { line -> lineOf(line.sortedBy { it.box.left }, styles) }
    }

    private fun sameLine(line: TextBox, box: TextBox): Boolean {
        val overlap = minOf(line.top, box.top) - maxOf(line.bottom, box.bottom)
        val minHeight = minOf(line.height, box.height)
        return minHeight <= 0f || overlap >= minHeight * config.lineOverlapRatio
    }

    /**
     * [a] and [b] are runs of words on different rows, one above the other: their cores ([GlyphRun.core]) do not
     * share a row though their boxes do, through a tall glyph's box.
     */
    private fun stacked(a: GlyphRun, b: GlyphRun): Boolean {
        val across = minOf(a.core.right, b.core.right) > maxOf(a.core.left, b.core.left)
        return a.prose && b.prose && across && !sameLine(a.core, b.core)
    }

    private fun lineOf(runs: List<GlyphRun>, styles: GlyphStyles): TextLine {
        val ranges = runs.map { it.start until it.end }
        return TextLine(
            spans = runs.map { TextSpan(it.start, it.end) },
            box = runs.map { it.box }.reduce(TextBox::union),
            size = styles.dominantSize(ranges),
            bold = styles.isMostlyBold(ranges),
        )
    }
}
