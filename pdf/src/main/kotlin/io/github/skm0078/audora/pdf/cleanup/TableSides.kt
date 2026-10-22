// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/cleanup/TableSides.kt
// Purpose: The one set of rules for telling two tables set side by side from one wide table, by their cells' text.

package io.github.skm0078.audora.pdf.cleanup

import io.github.skm0078.audora.core.config.TableConfig

/**
 * Rows of cells set either side of a wide gutter, each row its cells' text, top to bottom. The finder (blocks XY-cut
 * apart) and the grid (rows layout joined across the gutter) both ask [twoTables], so a table reads the same however
 * layout cut it. They are one table unless they show they are two: a false split loses which cells share a row,
 * while a false join only reads a second table's cells under a longer header.
 *
 * - **The same header** on both sides ([sameHeader]): a long list set in two halves. Two tables.
 * - **A running serial number** opening the right side's rows: a second table's key column. Two tables.
 * - **A row on one side only** ([hasOneSidedRow]), below the header rows: tables of different lengths, or one starting
 *   lower than the other. Two tables. A section row ("A. | Revenue receipts") does not count. When the right side is
 *   figures ([readsAsFigures]: every cell below its header a number or a blank figure) only a row on the right counts:
 *   a label row whose figures are left blank sits on the left alone in a single wide table.
 */
internal object TableSides {
    /** Whether [rows] (each row's cells left and right of the gutter) are two tables side by side. */
    fun twoTables(rows: List<Pair<List<String>, List<String>>>, config: TableConfig): Boolean {
        val left = rows.map { it.first }.filter { it.isNotEmpty() }
        val right = rows.map { it.second }.filter { it.isNotEmpty() }
        val headerRows = headerRowCount(rows.map { (l, r) -> l + r }, config.maxHeaderRows)
        val figures = readsAsFigures(right, config.maxHeaderRows, config.minFigureRows)
        return sameHeader(left, right, config.minSideColumns) ||
            isSerialRun(right.drop(1).mapNotNull { it.firstOrNull() }) ||
            hasOneSidedRow(rows, headerRows, config.minSideColumns, rightOnly = figures)
    }

    /** Both sides open with the same row of at least [minCells] cells. */
    fun sameHeader(left: List<List<String>>, right: List<List<String>>, minCells: Int): Boolean {
        val header = left.firstOrNull().orEmpty()
        return header.size >= minCells && header == right.firstOrNull()
    }

    /**
     * Every cell of [side] below its header (its first row, and up to [maxHeaderRows] rows not all figures: a header
     * set over two lines) is a figure, in at least [minRows] rows, and its rows open with no running serial number.
     */
    fun readsAsFigures(side: List<List<String>>, maxHeaderRows: Int, minRows: Int): Boolean {
        val headerRows = side.indexOfFirst { row -> row.all(::isFigure) }.coerceIn(1, maxHeaderRows)
        val body = side.drop(headerRows)
        return body.size >= minRows && body.all { row -> row.all(::isFigure) } &&
            !isSerialRun(body.mapNotNull { it.firstOrNull() })
    }

    /**
     * A row of at least [minCells] cells on one side with nothing on the other, below the [headerRows] (only on the
     * right when [rightOnly]). A section row ("A. | Revenue receipts", [isSectionRow]) is not one.
     */
    fun hasOneSidedRow(
        rows: List<Pair<List<String>, List<String>>>,
        headerRows: Int,
        minCells: Int,
        rightOnly: Boolean = false,
    ): Boolean = rows.drop(headerRows).any { (left, right) ->
        val side = if (left.isEmpty()) right else left
        val oneSided = right.isEmpty() != left.isEmpty() && (!rightOnly || left.isEmpty())
        oneSided && side.size >= minCells && !isSectionRow(side)
    }

    /** A row opened by a section's letter or Roman numeral alone: "A.", "(b)", "IV.", "क)", "খ.". */
    private fun isSectionRow(cells: List<String>): Boolean = SECTION_LABEL.matches(cells.first().trim())

    private fun isFigure(cell: String): Boolean = isNumber(cell) || BLANK_FIGURE.matches(cell.trim())

    private fun isNumber(cell: String): Boolean = NUMBER_CELL.matches(cell.trim())

    /**
     * Whole numbers of up to three digits each one more than the one before ("1", "2.", "৩"), at least two of them:
     * a key column numbers its rows; phone extensions, years and roll numbers run on too but are longer.
     */
    private fun isSerialRun(cells: List<String>): Boolean {
        val values = cells.map { cell ->
            cell.trim().removeSuffix(".")
                .takeIf { it.length in 1..MAX_SERIAL_DIGITS && it.all(Char::isDigit) }
                ?.fold(0L) { n, c -> n * RADIX + Character.digit(c, RADIX) }
        }
        return values.size >= 2 && values.zipWithNext().all { (a, b) -> a != null && b == a + 1 }
    }

    private const val RADIX = 10
    private const val MAX_SERIAL_DIGITS = 3

    private val SECTION_LABEL = Regex("\\(?(?:[IVXLivxl]{1,4}|\\p{L}\\p{M}*)[.)]")

    /**
     * A figure left blank: a dash, dots, "N.A.", "nil", and the words for nothing in Hindi and Bengali ("निरंक",
     * "शून्य", "শূন্য", "নেই").
     */
    private val BLANK_FIGURE = Regex("[-–—−]+|\\.{2,}|…|(?i)n\\.?\\s?a\\.?|(?i)nil|निरंक|शून्य|শূন্য|নেই")
}
