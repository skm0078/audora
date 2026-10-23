// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/layout/GlyphRuns.kt
// Purpose: Cuts a page's text into runs of glyphs that sit side by side on one line: the atoms of the layout.

package io.github.skm0078.audora.pdf.layout

import io.github.skm0078.audora.core.config.LayoutConfig
import io.github.skm0078.audora.core.model.TextBox
import io.github.skm0078.audora.pdf.extract.PageText

/**
 * A run of glyphs in extraction order on one line, with no column-sized gap inside. [start] and [end] are
 * offsets into the page text, trimmed of whitespace. [boxedGlyphs] counts the glyphs that had a box. [core] is
 * [box] as tall as its glyphs of usual height ([LayoutConfig.tallGlyphRatio]): a math font's "≳" has a box that
 * reaches into the line below. [prose] tells a run of words ([LayoutConfig.proseLetterShare]) from a formula's.
 * [monospace] runs are mostly set in a monospace font ([LayoutConfig.monospaceFontMarkers]): preformatted text.
 */
internal class GlyphRun(
    val start: Int,
    val end: Int,
    val box: TextBox,
    val boxedGlyphs: Int,
    val core: TextBox = box,
    val traits: RunTraits = RunTraits(),
) {
    val prose: Boolean get() = traits.prose
    val monospace: Boolean get() = traits.monospace
}

/** What a [GlyphRun]'s glyphs are: mostly letters ([GlyphRun.prose]), mostly monospace ([GlyphRun.monospace]). */
internal data class RunTraits(val prose: Boolean = false, val monospace: Boolean = false)

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
        val out = RunCollector(page, styles)
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
                    out.addGlyph(offset, next, page.boxAt(offset), styles.sizeAt(offset), Character.isLetter(codePoint))
                }
            }
            offset = next
        }
        out.finish()
        return Result(out.runs, glyphs, out.boxedGlyphs)
    }

    private inner class RunCollector(private val page: PageText, private val styles: GlyphStyles) {
        val runs = ArrayList<GlyphRun>()
        var boxedGlyphs = 0
        private var start = NONE
        private var end = NONE
        private var box: TextBox? = null
        private val glyphBoxes = ArrayList<TextBox>()
        private var last: TextBox? = null
        private var boxed = 0
        private var letters = 0

        /** Unboxed glyphs seen while no run was open: they join the next run, or the last one at the end. */
        private var pendingStart = NONE
        private var pendingEnd = NONE

        fun addGlyph(from: Int, to: Int, box: TextBox?, size: Float, letter: Boolean) {
            if (box == null) {
                addUnboxed(from, to)
                return
            }
            if (!continuesWith(box, em = if (size > 0f) size else box.height)) close()
            addBoxed(from, to, box)
            if (letter) letters++
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
            glyphBoxes.add(glyph)
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
            if (start != NONE && runBox != null) {
                val prose = boxed >= config.proseMinGlyphs && letters >= boxed * config.proseLetterShare
                val monospace = styles.isMostlyMonospace(start until end, page.text)
                val traits = RunTraits(prose, monospace)
                runs.add(GlyphRun(start, end, runBox, boxed, coreOf(glyphBoxes) ?: runBox, traits))
            }
            start = NONE
            box = null
            glyphBoxes.clear()
            last = null
            boxed = 0
            letters = 0
        }

        fun finish() {
            close()
            if (pendingStart == NONE) return
            // A page with no boxed glyph at all keeps the extraction order (minBoxedShare), so dropping is moot.
            val tail = runs.removeLastOrNull() ?: return
            runs.add(
                GlyphRun(tail.start, pendingEnd, tail.box, tail.boxedGlyphs, tail.core, tail.traits),
            )
        }
    }

    /** The union of [glyphs] within [LayoutConfig.tallGlyphRatio] of their typical height, or null for none. */
    private fun coreOf(glyphs: List<TextBox>): TextBox? {
        if (glyphs.isEmpty()) return null
        val heights = glyphs.map { it.height }.sorted()
        val typical = heights[((heights.size - 1) * config.typicalGlyphQuantile).toInt()]
        return glyphs.filter { it.height <= typical * config.tallGlyphRatio }.reduceOrNull(TextBox::union)
    }

    private companion object {
        const val NONE = -1
        const val LF = '\n'.code
        const val CR = '\r'.code
    }
}
