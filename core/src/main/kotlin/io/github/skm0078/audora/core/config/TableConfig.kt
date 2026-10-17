// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/TableConfig.kt
// Purpose: Tunables for turning a found table into rows and columns (RI-4a, ADR-035).

package io.github.skm0078.audora.core.config

/**
 * `:pdf` keeps a table as a header and rows of cells instead of dropping it (ADR-035); playback reads it by setting.
 * Distances are in em of the line's size.
 *
 * @property cellGapEm runs of one line closer than this belong to the same cell ("R1 (s" and its superscript "−1").
 * @property edgeRowGapRatio a split line right above or below a run of rows joins it as a header or last row only
 *   when it is at most this many times the run's typical row pitch away.
 * @property maxColumnCellEm a block of one-run lines beside a table is one of its columns only when its lines are at
 *   most this wide on average: table cells are short, body text beside a table runs the column's width.
 * @property minColumnPairShare such a block's lines must each sit within the table's height, and at least this share
 *   of them on a row of the table (a cell spanning two rows sits between them).
 * @property denseColumnShare a column with a cell on at least this share of the table's lines that fill at least
 *   [rowStartShare] of the columns is dense: every row has a cell there (a result list's roll numbers). Without a
 *   dense column every row has a first cell. A line lacking one of those cells carries on the row above (cells
 *   wrapped over lines).
 * @property rowStartShare a line with cells in more than this share of the columns always starts a row: a record
 *   whose first cell spans the row above ("1st" beside two names) is not a wrapped line.
 * @property wrapGapEm a line carries on the row above only when it starts at most this far below that row's last
 *   line: further down is a new row with empty cells.
 * @property minFilledShare a grid with fewer of its cells filled is a chart's labels, not a table.
 * @property maxSingleLetterRunShare a grid with more of its runs a single letter is a chart's rotated axis labels
 *   (set one letter at a time), not a table. Single digits do not count: tables are full of them.
 * @property headerOverhangEm a short line right above a table is a header line set apart from it (cut into blocks
 *   of its own) when it lies within the table's width give or take this much: a centred header sticks out.
 * @property maxHeaderRows a header runs over at most this many rows (the rows before the first with a number).
 * @property maxRowsPerTable a run of more lines than this is not treated as one table (a page of aligned figures).
 */
data class TableConfig(
    val cellGapEm: Float = 1f,
    val edgeRowGapRatio: Float = 2f,
    val maxColumnCellEm: Float = 15f,
    val minColumnPairShare: Float = 0.5f,
    val denseColumnShare: Float = 0.9f,
    val rowStartShare: Float = 0.5f,
    val wrapGapEm: Float = 0.8f,
    val minFilledShare: Float = 0.5f,
    val maxSingleLetterRunShare: Float = 0.2f,
    val headerOverhangEm: Float = 2f,
    val maxHeaderRows: Int = 3,
    val maxRowsPerTable: Int = 400,
) {
    init {
        require(cellGapEm > 0f && edgeRowGapRatio > 0f && maxColumnCellEm > 0f) { "table distances must be > 0" }
        val shares =
            listOf(minColumnPairShare, denseColumnShare, rowStartShare, minFilledShare, maxSingleLetterRunShare)
        require(shares.all { it in 0f..1f }) { "table shares must be in 0..1, were $shares" }
        require(headerOverhangEm >= 0f) { "headerOverhangEm must be >= 0, was $headerOverhangEm" }
        require(wrapGapEm >= 0f) { "wrapGapEm must be >= 0, was $wrapGapEm" }
        require(maxHeaderRows >= 1) { "maxHeaderRows must be >= 1, was $maxHeaderRows" }
        require(maxRowsPerTable >= 2) { "maxRowsPerTable must be >= 2, was $maxRowsPerTable" }
    }
}
