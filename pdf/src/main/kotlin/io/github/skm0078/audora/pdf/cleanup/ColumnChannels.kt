// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/cleanup/ColumnChannels.kt
// Purpose: Finds where one table column is really two printed close together: a gap running down it (RI-4a).

package io.github.skm0078.audora.pdf.cleanup

import io.github.skm0078.audora.core.config.TableConfig

/**
 * A column's words, line row by line row, each word's horizontal extent left to right. Two columns become one when
 * they are set closer than `TableConfig.cellGapEm` ("1 Assam", "2 Bihar": one cell a row) or when a cell reaches
 * across both (a header over the serial number and the name); either way the gap between them runs down the column at
 * the same place, while the word spaces of a cell's text fall anywhere and are narrower. A place splits the column
 * when, of the line rows reaching across it, at least `minSplitRows` and `splitShare` have a gap of at least
 * `splitGapEm` there; a row wholly to one side of it does not count (a total row, a short name).
 */
internal class ColumnChannels(private val config: TableConfig) {
    /** Where to split a column of [rows] (each its words' extents, left to right) set in type of [em]; null if not. */
    fun find(rows: List<List<ClosedFloatingPointRange<Float>>>, em: Float): Float? {
        val minGap = config.splitGapEm * em
        val gaps = rows.map { words -> wideGaps(words, minGap) }
        val candidates = gaps.flatten().flatMap { gap -> listOf(gap.start + minGap / 2, gap.endInclusive - minGap / 2) }
        val best = candidates.map { x -> x to agreeing(rows, gaps, x) }
            .filter { (_, agree) -> agree != null }
            .maxByOrNull { (_, agree) -> agree!!.size } ?: return null
        val shared = best.second!!.reduce { a, b -> maxOf(a.start, b.start)..minOf(a.endInclusive, b.endInclusive) }
        return (shared.start + shared.endInclusive) / 2
    }

    /** The gaps holding [x] of the cells that split there, or null when too few or too small a share of them do. */
    private fun agreeing(
        cells: List<List<ClosedFloatingPointRange<Float>>>,
        gaps: List<List<ClosedFloatingPointRange<Float>>>,
        x: Float,
    ): List<ClosedFloatingPointRange<Float>>? {
        val reaching = cells.indices.filter { c ->
            cells[c].isNotEmpty() &&
                x in cells[c].first().start..cells[c].last().endInclusive
        }
        val agree = reaching.mapNotNull { c -> gaps[c].firstOrNull { x in it } }
        val enough = agree.size >= config.minSplitRows && agree.size >= reaching.size * config.splitShare
        return if (enough) agree else null
    }

    private fun wideGaps(words: List<ClosedFloatingPointRange<Float>>, minGap: Float) =
        words.zipWithNext { a, b -> a.endInclusive..b.start }.filter { it.endInclusive - it.start >= minGap }
}
