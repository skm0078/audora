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
 * @property ruleSnapEm on a scanned page, rules this close are one rule, and a rule reaching this close to another
 *   meets it (a scan's rules stop short of each other or overshoot).
 * @property minProseLines on a scanned page, an OCR paragraph with at least this many lines of running text is
 *   prose (or a cell wrapped over lines), never a table's rows: Tesseract reads each row of a table as a line of its
 *   own.
 * @property minProseWords on a scanned page, a stretch of at least this many words, or one wider than
 *   [maxColumnCellEm], is running text rather than a cell. OCR's em is a line's height, taller than the type, so
 *   width alone misses narrow prose columns.
 * @property maxRowGapEm on a scanned page, a borderless table's rows are at most this far apart (a double-spaced
 *   table's are about 1 em apart); a wider gap ends it, so a heading below it is not read as its last row.
 * @property splitGapEm two columns printed closer than [cellGapEm] ("1 Assam", serial number and state) are split
 *   where a gap at least this wide (wider than a word space) runs down the column at the same place
 * @property minSplitRows in at least this many of its cells
 * @property splitShare and in at least this share of the cells reaching that place (a header across both may not).
 * @property sideBySideGapRatio two runs of rows side by side are one table cut apart by XY-cut only when the gutter
 *   between them is at most this many times their own widest cell gap (and their rows pair one for one); XY-cut cuts
 *   at the widest gap, so it may be somewhat wider than the rest. A wider gutter sets two tables side by side.
 * @property ruleBreakEm on a scanned page, rules in line with each other are one rule when less than this far apart
 *   along their length (a rule the scan broke); farther apart they are the rules of two tables side by side.
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
    val ruleSnapEm: Float = 0.5f,
    val minProseLines: Int = 2,
    val minProseWords: Int = 5,
    val maxRowGapEm: Float = 2f,
    val splitGapEm: Float = 0.5f,
    val minSplitRows: Int = 3,
    val splitShare: Float = 0.8f,
    val sideBySideGapRatio: Float = 2f,
    val ruleBreakEm: Float = 2f,
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
        require(ruleSnapEm >= 0f) { "ruleSnapEm must be >= 0, was $ruleSnapEm" }
        require(minProseWords >= 2) { "minProseWords must be >= 2, was $minProseWords" }
        require(minProseLines >= 1) { "minProseLines must be >= 1, was $minProseLines" }
        require(maxRowGapEm > 0f) { "maxRowGapEm must be > 0, was $maxRowGapEm" }
        require(splitGapEm in 0f..cellGapEm && splitGapEm > 0f) { "splitGapEm must be in (0, cellGapEm]" }
        require(minSplitRows >= 2 && splitShare in 0f..1f) { "minSplitRows must be >= 2, splitShare in 0..1" }
        require(sideBySideGapRatio > 0f) { "sideBySideGapRatio must be > 0, was $sideBySideGapRatio" }
        require(ruleBreakEm >= ruleSnapEm) { "ruleBreakEm must be >= ruleSnapEm, was $ruleBreakEm" }
    }
}
