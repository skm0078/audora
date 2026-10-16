// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/layout/GlyphRuns.kt
// Purpose: Cuts a page's text into runs of glyphs that sit side by side on one line: the atoms of the layout.

package io.github.skm0078.audora.pdf.layout

import io.github.skm0078.audora.core.config.LayoutConfig
import io.github.skm0078.audora.core.model.TextBox
import io.github.skm0078.audora.pdf.extract.PageText

/**
 * A run of glyphs in extraction order on one line, with no column-sized gap inside. [start] and [end] are
 * offsets into the page text, trimmed of whitespace. [boxedGlyphs] counts the glyphs that had a box.
 */
internal class GlyphRun(val start: Int, val end: Int, val box: TextBox, val boxedGlyphs: Int)

internal class GlyphRunSplitter(private val config: LayoutConfig) {
    /** Result of [split]: the runs, and how many non-space glyphs the page has and how many had a box. */
    class Result(val runs: List<GlyphRun>, val glyphs: Int, val boxedGlyphs: Int)

    /**
     * Walks the text in extraction order. A run ends at a line break, a jump to another line, a gap of at least
     * [LayoutConfig.columnGapEm] (PDFium joins two columns' lines when the content stream alternates between
     * them) or a large jump back left. Glyphs without a box ride along with the run they are in.
     */
    fun split(page: PageText, styles: GlyphStyles): Result {
        val text = page.text
        val out = RunCollector()
        var glyphs = 0
        var offset = 0
        while (offset < text.length) {
            val codePoint = text.codePointAt(offset)
            val next = offset + Character.charCount(codePoint)
            when {
                codePoint == LF || codePoint == CR -> out.close()

                Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint) -> Unit

                else -> {
                    glyphs++
                    out.addGlyph(offset, next, page.boxAt(offset), styles.sizeAt(offset))
                }
            }
            offset = next
        }
        out.finish()
        return Result(out.runs, glyphs, out.boxedGlyphs)
    }

    private inner class RunCollector {
        val runs = ArrayList<GlyphRun>()
        var boxedGlyphs = 0
        private var start = NONE
        private var end = NONE
        private var box: TextBox? = null
        private var last: TextBox? = null
        private var boxed = 0

        /** Unboxed glyphs seen while no run was open: they join the next run, or the last one at the end. */
        private var pendingStart = NONE
        private var pendingEnd = NONE

        fun addGlyph(from: Int, to: Int, box: TextBox?, size: Float) {
            if (box == null) {
                addUnboxed(from, to)
                return
            }
            if (!continuesWith(box, em = if (size > 0f) size else box.height)) close()
            addBoxed(from, to, box)
        }

        private fun continuesWith(next: TextBox, em: Float): Boolean {
            val previous = last ?: return false
            val overlap = minOf(previous.top, next.top) - maxOf(previous.bottom, next.bottom)
            val minHeight = minOf(previous.height, next.height)
            val sameLine = minHeight <= 0f || overlap >= minHeight * config.lineOverlapRatio
            val gap = next.left - previous.right
            return sameLine && gap < config.columnGapEm * em && next.left >= previous.left - config.backJumpEm * em
        }

        private fun addBoxed(from: Int, to: Int, glyph: TextBox) {
            if (start == NONE) {
                start = if (pendingStart != NONE) pendingStart else from
                pendingStart = NONE
            }
            end = to
            box = box?.union(glyph) ?: glyph
            last = glyph
            boxed++
            boxedGlyphs++
        }

        private fun addUnboxed(from: Int, to: Int) {
            if (start != NONE) {
                end = to
                return
            }
            if (pendingStart == NONE) pendingStart = from
            pendingEnd = to
        }

        fun close() {
            val runBox = box
            if (start != NONE && runBox != null) runs.add(GlyphRun(start, end, runBox, boxed))
            start = NONE
            box = null
            last = null
            boxed = 0
        }

        fun finish() {
            close()
            if (pendingStart == NONE) return
            // A page with no boxed glyph at all keeps the extraction order (minBoxedShare), so dropping is moot.
            val tail = runs.removeLastOrNull() ?: return
            runs.add(GlyphRun(tail.start, pendingEnd, tail.box, tail.boxedGlyphs))
        }
    }

    private companion object {
        const val NONE = -1
        const val LF = '\n'.code
        const val CR = '\r'.code
    }
}
