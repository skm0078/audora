// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/layout/XyCut.kt
// Purpose: Orders a page's glyph runs into reading-order blocks by recursive XY-cut, columns first.

package io.github.skm0078.audora.pdf.layout

import io.github.skm0078.audora.core.config.LayoutConfig
import io.github.skm0078.audora.core.model.TextBox

/**
 * Recursive XY-cut (Spec §5 step 4; OpenDataLoader-PDF's XY-Cut++ as a reference), columns first:
 *
 * 1. An empty vertical strip at least [LayoutConfig.columnGapEm] wide and [LayoutConfig.minColumnHeightEm] tall,
 *    with at least [LayoutConfig.minColumnWidthEm] of text on each side, is a gutter: cut there, left side first.
 *    No run crosses such a strip, so a full-width title or figure caption keeps the cut from happening above or
 *    below it. A single line is never cut: a wide justified space is not a gutter.
 * 2. Otherwise cut at every empty horizontal strip of at least [LayoutConfig.yCutShare] of the widest one, top first.
 * 3. A group that cannot be cut is a leaf.
 *
 * Trying columns first is what keeps two aligned columns apart: their line gaps line up into full-width strips
 * that a rows-first cut would take, interleaving the columns line by line.
 *
 * Row cuts go down to single lines in evenly spaced text, so consecutive leaves stacked in one column are joined
 * again into one block: paragraphs are found inside blocks, from line spacing and indents (ParagraphBuilder).
 */
internal class XyCut(private val config: LayoutConfig) {
    /** Blocks in reading order; each keeps its runs in input order. */
    fun blocks(runs: List<GlyphRun>, em: Float): List<List<GlyphRun>> {
        val leaves = ArrayList<List<GlyphRun>>()
        cut(runs, em, depth = 0, leaves)
        return joinStacked(leaves, em)
    }

    /** Joins each leaf to the previous one when it sits right below it, overlapping it horizontally. */
    private fun joinStacked(leaves: List<List<GlyphRun>>, em: Float): List<List<GlyphRun>> {
        val blocks = ArrayList<MutableList<GlyphRun>>()
        var previous: TextBox? = null
        for (leaf in leaves) {
            val box = leaf.map { it.box }.reduce(TextBox::union)
            val above = previous
            if (above != null && isStackedBelow(above, box, em)) {
                blocks.last().addAll(leaf)
                previous = above.union(box)
            } else {
                blocks.add(leaf.toMutableList())
                previous = box
            }
        }
        return blocks
    }

    private fun isStackedBelow(above: TextBox, box: TextBox, em: Float): Boolean {
        val below = box.top <= above.bottom + em * config.lineOverlapRatio
        val overlap = minOf(above.right, box.right) - maxOf(above.left, box.left)
        return below && overlap >= minOf(above.width, box.width) * config.lineOverlapRatio
    }

    private fun cut(runs: List<GlyphRun>, em: Float, depth: Int, out: MutableList<List<GlyphRun>>) {
        if (runs.isEmpty()) return
        val column = if (runs.size > 1 && depth < config.maxCutDepth) columnCut(runs, em) else null
        val parts = when {
            runs.size <= 1 || depth >= config.maxCutDepth -> null
            column != null -> runs.partition { it.box.right <= column }.toList()
            else -> rowCut(runs)
        }
        if (parts == null) out.add(runs) else parts.forEach { cut(it, em, depth + 1, out) }
    }

    /** The x of the widest valid gutter's left edge, or null. */
    private fun columnCut(runs: List<GlyphRun>, em: Float): Float? {
        val minGap = config.columnGapEm * em
        val minWidth = config.minColumnWidthEm * em
        val groupHeight = runs.maxOf { it.box.top } - runs.minOf { it.box.bottom }
        if (groupHeight < config.minColumnHeightEm * em) return null
        val groupLeft = runs.minOf { it.box.left }
        val groupRight = runs.maxOf { it.box.right }
        var best: Float? = null
        var bestGap = 0f
        for (gap in gaps(runs.map { it.box.left to it.box.right })) {
            val wide = gap.second - gap.first >= minGap
            val sidesWideEnough = gap.first - groupLeft >= minWidth && groupRight - gap.second >= minWidth
            if (wide && sidesWideEnough && gap.second - gap.first > bestGap) {
                best = gap.first
                bestGap = gap.second - gap.first
            }
        }
        return best
    }

    /** Runs split into bands at the widest horizontal gaps, top band first; null when there is no gap. */
    private fun rowCut(runs: List<GlyphRun>): List<List<GlyphRun>>? {
        // Flip y so that "ascending" means top to bottom.
        val gaps = gaps(runs.map { -it.box.top to -it.box.bottom })
        val widest = gaps.maxOfOrNull { it.second - it.first } ?: return null
        val cuts = gaps.filter { it.second - it.first >= widest * config.yCutShare }.map { it.first }
        val bands = List(cuts.size + 1) { ArrayList<GlyphRun>() }
        for (run in runs) {
            // Cuts ascend; the band is the number of cuts above the run's (flipped) bottom.
            val found = cuts.binarySearch(-run.box.bottom)
            bands[if (found >= 0) found else -found - 1].add(run)
        }
        return bands.filter { it.isNotEmpty() }
    }

    /** Empty intervals between the union of [intervals] (each start to end, start <= end), ascending. */
    private fun gaps(intervals: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
        val sorted = intervals.sortedBy { it.first }
        val out = ArrayList<Pair<Float, Float>>()
        var reach = sorted.first().second
        for (i in 1 until sorted.size) {
            val (start, end) = sorted[i]
            if (start > reach) out.add(reach to start)
            reach = maxOf(reach, end)
        }
        return out
    }
}
