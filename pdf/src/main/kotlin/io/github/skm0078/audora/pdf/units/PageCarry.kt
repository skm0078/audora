// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/units/PageCarry.kt
// Purpose: Carries a table and a sentence over a page break: continued tables, the continues-from-last-page signal.

package io.github.skm0078.audora.pdf.units

import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.TableRowInfo
import io.github.skm0078.audora.core.model.UnitKind

/**
 * What a page hands on to the next ([PageCarry.tailOf]): the table it ends with, if any, and whether it ends inside
 * a sentence of body text or a list item.
 */
class PageTail internal constructor(internal val table: TableTail?, internal val openSentence: UnitKind?)

/** A table that runs to a page's end: its columns, its header texts and the data rows read so far. */
internal class TableTail(val columnCount: Int, val headers: List<String>, val rows: Int)

/** A page's units joined to the page before ([PageCarry.join]). */
internal class JoinedPage(val units: List<ReadingUnit>, val continuesPreviousPage: Boolean)

/**
 * Pages are read one at a time (ADR-020), so a table printed over two pages started again on the second as a new
 * table with its first data row taken for the header row, and a sentence broken by the page end read as two. Here a
 * page is joined to what the page before it ended with:
 *
 * - **A continued table.** The page before ends with a table (nothing after it but footnotes) and this page opens
 *   with one (nothing before it but a caption) with the same number of columns. When its header row repeats the
 *   header before (at least half of its cells read alike), it is that header: every unit of the table is marked
 *   `TableRowInfo.continued`, with `rowsBefore` the rows already read. When its first row is data, shaped like the
 *   rows under it (numbers in the same columns), it becomes data row 0 with the header texts carried over and the
 *   other rows numbered on after it, so the table has no summary unit on this page. A first row that is neither (a
 *   new table that happens to have as many columns) starts a new table as before.
 * - **A sentence carried on.** The page before ends inside a sentence of body text or a list item (no full stop,
 *   question mark, exclamation or danda at its end) and this page opens with the same kind: the page continues the
 *   paragraph, so a quote opened before the break is still open ([JoinedPage.continuesPreviousPage], plan item 7).
 *
 * Units keep their spans and text; only table info and one unit's kind change, so resume anchors stay valid.
 */
internal object PageCarry {
    /** What [units] (one page, in reading order, already joined to its page before) hand on to the next page. */
    fun tailOf(units: List<ReadingUnit>): PageTail {
        val last = units.lastOrNull { it.kind !in TRAILING }
        val table = last?.table?.takeIf { last.kind == UnitKind.TABLE_ROW }?.let { info ->
            val dataHeader = hasDataHeader(units, info)
            val rows = info.rowsBefore + info.rowCount + if (dataHeader) 1 else 0
            TableTail(info.columnCount, if (dataHeader) emptyList() else info.headers, rows)
        }
        val open = last?.takeIf { it.kind in PROSE && breaksOff(it.text) }?.kind
        return PageTail(table, open)
    }

    /**
     * The table's header row is shaped like its data rows: the page opened a table with no header row of its own,
     * so it hands on no header and counts that row as data.
     */
    private fun hasDataHeader(units: List<ReadingUnit>, info: TableRowInfo): Boolean {
        val table = units.filter { it.table?.tableIndex == info.tableIndex }
        val summary = table.firstOrNull { it.kind == UnitKind.TABLE_SUMMARY }
        return summary != null && !info.continued && isShapedLikeRows(summary, table.drop(1))
    }

    /** [units] (one page) joined to [previous], the tail of the page before; unchanged without one. */
    fun join(units: List<ReadingUnit>, previous: PageTail?): JoinedPage {
        if (previous == null) return JoinedPage(units, continuesPreviousPage = false)
        val first = units.firstOrNull { it.kind != UnitKind.CAPTION }
        val carried = previous.table?.let { tail -> carryTable(units, tail) } ?: units
        val continues = previous.openSentence != null && first?.kind == previous.openSentence && carriesOn(first.text)
        return JoinedPage(carried, continues)
    }

    /** [units] with their opening table carried on from [tail], or null when the page opens with no such table. */
    private fun carryTable(units: List<ReadingUnit>, tail: TableTail): List<ReadingUnit>? {
        val start = units.indexOfFirst { it.kind != UnitKind.CAPTION }
        val opening = units.getOrNull(start)?.takeIf { it.kind == UnitKind.TABLE_SUMMARY } ?: return null
        val info = requireNotNull(opening.table)
        val tableUnits = units.indices.filter { units[it].table?.tableIndex == info.tableIndex }
        val rows = tableUnits.drop(1).map { units[it] }
        return when {
            info.columnCount != tail.columnCount -> null

            repeatsHeader(info, opening.text, tail.headers) -> units.mapIndexed { i, unit ->
                if (i in tableUnits) unit.withTable { it.copy(continued = true, rowsBefore = tail.rows) } else unit
            }

            isShapedLikeRows(opening, rows) -> headerAsRow(units, tableUnits, tail)

            else -> null
        }
    }

    /** The table's first row read as data row 0 under the carried header, its other rows numbered on. */
    private fun headerAsRow(units: List<ReadingUnit>, tableUnits: List<Int>, tail: TableTail): List<ReadingUnit> {
        val first = tableUnits.first()
        val rowPause = tableUnits.drop(1).firstOrNull()?.let { units[it].pauseAfterMs } ?: units[first].pauseAfterMs
        return units.mapIndexed { i, unit ->
            when {
                i == first -> unit.copy(kind = UnitKind.TABLE_ROW, pauseAfterMs = rowPause).withTable {
                    it.carried(row = 0, tail)
                }

                i in tableUnits -> unit.withTable { it.carried(row = requireNotNull(it.row) + 1, tail) }

                else -> unit
            }
        }
    }

    /** At least half of the header cells that hold text read like the carried header's (letters and digits only). */
    private fun repeatsHeader(info: TableRowInfo, text: String, headers: List<String>): Boolean {
        if (headers.size != info.cells.size) return false
        val pairs = info.cells.map { text.substring(it.start, it.end).folded() }.zip(headers.map { it.folded() })
            .filter { (now, before) -> now.isNotEmpty() || before.isNotEmpty() }
        return pairs.isNotEmpty() && pairs.count { (now, before) -> now == before } * 2 >= pairs.size
    }

    /**
     * The first row is data: in at least [MIN_SHAPE_SHARE] of the columns whose rows are mostly numbers or mostly
     * words, it is the same; and some column is mostly numbers, else a header and a row cannot be told apart.
     */
    private fun isShapedLikeRows(opening: ReadingUnit, rows: List<ReadingUnit>): Boolean {
        val columns = requireNotNull(opening.table).columnCount
        val firstShape = shapeOf(opening)
        val rowShapes = rows.map(::shapeOf)
        val majority = (0 until columns).map { c ->
            val numeric = rowShapes.count { it.getOrNull(c) == true }
            val wordy = rowShapes.count { it.getOrNull(c) == false }
            if (numeric == wordy) null else numeric > wordy
        }
        val judged = majority.indices.filter { majority[it] != null }
        val same = judged.count { firstShape.getOrNull(it) == majority[it] }
        return majority.any { it == true } && judged.isNotEmpty() && same >= judged.size * MIN_SHAPE_SHARE
    }

    /**
     * The text stops inside a sentence: at least [MIN_BROKEN_WORDS] words, ending on a letter, comma, semicolon or
     * dash with no full stop, question mark, exclamation or danda; a page number, running footer or print slug the
     * page kept ends on a digit.
     */
    private fun breaksOff(text: String): Boolean {
        val trimmed = text.trimEnd()
        val end = trimmed.trimEnd { it in CLOSERS }.lastOrNull() ?: return false
        val words = trimmed.split(' ').count { word -> word.any { it.isLetter() } }
        return words >= MIN_BROKEN_WORDS && end !in SENTENCE_ENDS && (end.isLetter() || end in RUN_ON || isMark(end))
    }

    /** The next page opens like the rest of a sentence: not with a capital or a number. */
    private fun carriesOn(text: String): Boolean {
        val first = text.firstOrNull { it.isLetterOrDigit() } ?: return false
        return !first.isDigit() && !first.isUpperCase() && !first.isTitleCase()
    }

    /** Units printed after a page's last text: notes at its foot. */
    private val TRAILING = setOf(UnitKind.FOOTNOTE)

    /** Kinds a sentence runs on in over a page break. */
    private val PROSE = setOf(UnitKind.BODY, UnitKind.LIST_ITEM)

    private val SENTENCE_ENDS = setOf('.', '?', '!', '।', '॥', '…')
    private const val CLOSERS = "\"'’”»)]"
    private const val RUN_ON = ",;-–—"
    private const val MIN_BROKEN_WORDS = 3
    private const val MIN_SHAPE_SHARE = 0.75f
}

private fun TableRowInfo.carried(row: Int, tail: TableTail): TableRowInfo = copy(
    row = row,
    rowCount = rowCount + 1,
    headers = tail.headers,
    continued = true,
    rowsBefore = tail.rows,
)

private fun ReadingUnit.withTable(change: (TableRowInfo) -> TableRowInfo): ReadingUnit =
    copy(table = change(requireNotNull(table)))

private fun String.folded(): String = filter { it.isLetterOrDigit() }.lowercase()

/** Per cell: mostly digits (true), mostly letters (false), or empty (null). */
private fun shapeOf(unit: ReadingUnit): List<Boolean?> = requireNotNull(unit.table).cells.map { cell ->
    val chars = unit.text.substring(cell.start, cell.end).filter { it.isLetterOrDigit() }
    if (chars.isEmpty()) null else chars.count(Char::isDigit) * 2 >= chars.length
}

/** A vowel sign or virama ends an Indic word: "है" ends on a sign, not a letter. */
private fun isMark(c: Char): Boolean = Character.getType(c).let {
    it == Character.NON_SPACING_MARK.toInt() || it == Character.COMBINING_SPACING_MARK.toInt()
}
