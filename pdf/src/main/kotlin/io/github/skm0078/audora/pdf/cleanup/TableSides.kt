// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/cleanup/TableSides.kt
// Purpose: The one set of rules for telling two tables set side by side from one wide table, by their cells' text.

package io.github.skm0078.audora.pdf.cleanup

/**
 * Rows of cells set either side of a wide gutter, each row its cells' text, top to bottom. The finder (blocks XY-cut
 * apart) and the grid (rows layout joined across the gutter) ask the same questions of them:
 *
 * - **The same header** on both sides ([sameHeader]): a long list set in two halves. Two tables.
 * - **Figures** on the right ([readsAsFigures]): every cell below its header a number or a blank figure ("–", "..",
 *   "N.A."), and no running serial number opening its rows (a second table's key column). One table, its figures set
 *   apart from its labels.
 * - **A row on one side only** ([hasOneSidedRow]): tables of different lengths, or one starting lower than the other.
 *   A section row ("A. | Revenue receipts") and a header's second line do not count, and a row whose figures are
 *   left blank sits beside figures, so a single wide table with them shows none.
 */
internal object TableSides {
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
     * A row of at least [minCells] cells on one side with nothing on the other, below the [headerRows] and the first
     * row full on both sides (a header's second line sits on one side). A section row ("A. | Revenue receipts",
     * [isSectionRow]) is not one.
     */
    fun hasOneSidedRow(rows: List<Pair<List<String>, List<String>>>, headerRows: Int, minCells: Int): Boolean {
        val firstFull = rows.indexOfFirst { (left, right) -> left.size >= minCells && right.size >= minCells }
        return firstFull >= 0 && rows.drop(maxOf(firstFull + 1, headerRows)).any { (left, right) ->
            val side = if (left.isEmpty()) right else left
            (left.isEmpty() || right.isEmpty()) && side.size >= minCells && !isSectionRow(side)
        }
    }

    /** A row opened by a section's letter or Roman numeral alone: "A.", "(b)", "IV.", "क)", "খ.". */
    private fun isSectionRow(cells: List<String>): Boolean = SECTION_LABEL.matches(cells.first().trim())

    private fun isFigure(cell: String): Boolean = isNumber(cell) || BLANK_FIGURE.matches(cell.trim())

    private fun isNumber(cell: String): Boolean = NUMBER_CELL.matches(cell.trim())

    /** Whole numbers each one more than the one before ("1", "2.", "৩"), at least two of them. */
    private fun isSerialRun(cells: List<String>): Boolean {
        val values = cells.map { cell ->
            cell.trim().removeSuffix(".")
                .takeIf { it.length in 1..MAX_SERIAL_DIGITS && it.all(Char::isDigit) }
                ?.fold(0L) { n, c -> n * RADIX + Character.digit(c, RADIX) }
        }
        return values.size >= 2 && values.zipWithNext().all { (a, b) -> a != null && b == a + 1 }
    }

    private const val RADIX = 10
    private const val MAX_SERIAL_DIGITS = 6

    private val SECTION_LABEL = Regex("\\(?(?:[A-Za-z]|[IVXLivxl]{1,4}|\\p{L}\\p{M}*)[.)]")

    /** A figure left blank: a dash, dots, "N.A.", "nil". */
    private val BLANK_FIGURE = Regex("[-–—−]+|\\.{2,}|…|(?i)n\\.?\\s?a\\.?|(?i)nil")
}
