// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/units/PageCarry.kt
// Purpose: Carries a table and a sentence over a page break: continued tables, the continues-from-last-page signal.

package io.github.skm0078.audora.pdf.units

import io.github.skm0078.audora.core.config.PageCarryConfig
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.TableRowInfo
import io.github.skm0078.audora.core.model.UnitKind

/**
 * What a page hands on to the next ([PageCarry.tailOf]): the table it ends with, if any, whether it ends inside a
 * sentence of body text, a list item or an aside, whether that sentence's last line runs the full width, and whether
 * it is written in a script without capitals ([caseless]: Devanagari, Bengali).
 */
class PageTail internal constructor(
    internal val table: TableTail?,
    internal val openSentence: UnitKind?,
    internal val endsFullLine: Boolean,
    internal val caseless: Boolean = false,
)

/**
 * A table that runs to a page's end: its columns, its header texts, the data rows read so far and the serial number
 * its last row opens with ("68" in "68 | Raj Kumar | 37600"), if any.
 */
internal class TableTail(val columnCount: Int, val headers: List<String>, val rows: Int, val lastSerial: Long?)

/** How a page's opening table carries on the table the page before ended with. */
internal enum class TableCarry {
    /** The page repeats the header row: its summary is marked continued. */
    HEADER_REPEATED,

    /** The page opens with data: a continued summary is set before it and that row becomes data row 0. */
    DATA_FIRST,
}

/** A page's units joined to the page before ([PageCarry.join]). */
internal class JoinedPage(val units: List<ReadingUnit>, val continuesPreviousPage: Boolean, val table: TableCarry?)

/**
 * Pages are read one at a time (ADR-020), so a table printed over two pages started again on the second as a new
 * table with its first data row taken for the header row, and a sentence broken by the page end read as two. Here a
 * page is joined to what the page before it ended with:
 *
 * - **A continued table.** The page before ends with a table (nothing after it but footnotes) and this page opens
 *   with one with the same number of columns, with nothing before it but a caption that says it continues ("Table 2
 *   (contd.)", "जारी", "ক্রমশ"); any other caption opens a new table. When its header row repeats the header before
 *   (more than half of its cells, and at least `PageCarryConfig.minHeaderMatchCells`, read alike), every unit of the
 *   table is marked `TableRowInfo.continued`, with `rowsBefore` the rows already read. When its first row is data
 *   (its serial number continues the last row before, or its cells are written like the column's data rows and none
 *   reads as a year: "2018-19" in a header is not data), a continued summary unit with no text is set before it, at
 *   its start, and the row becomes data row 0 with the header texts carried over. So every continued table opens
 *   with a continued [UnitKind.TABLE_SUMMARY] ("Table continues"), whatever its first row. A first row that is
 *   neither starts a new table as before.
 * - **A table with no header row of its own.** A table (continued or not, on any page) whose header row is data
 *   (its serial number runs on into the next row, or its cells are written like the column's data) has that row read
 *   as data row 0 under a summary of no length that is not continued and has no headers, so its rows are numbered
 *   as heard and the next page numbers on from all of them.
 * - **A sentence carried on.** The page before ends inside a sentence of body text, a list item or an aside (no
 *   full stop, question mark, exclamation or danda at its end) and this page opens with the same kind, lower case.
 *   When either is in a script without capitals (Devanagari, Bengali), the page layout decides instead, in the
 *   page's reading frame ([readingTurn]) and against at least `PageCarryConfig.minColumnLines` other lines of the
 *   column: the sentence's last line reaches the column's usual right edge and this page's first line is not
 *   indented. The page then continues the paragraph, so a quote opened
 *   before the break is still open ([JoinedPage.continuesPreviousPage], plan item 7).
 *
 * Units keep their spans and text; only table info and one unit's kind change, and the continued summary of a page
 * opening with data is a unit of no length at the first row's start (later units' indexes move up by one). So an
 * anchor saved on that row stays valid; playback counts the summary as one character long (`spokenEnd`), so resuming
 * at the row plays "Table continues" first.
 */
internal class PageCarry(private val config: PageCarryConfig) {
    private val cue = ColumnCue(config)

    /** What [units] (one page, in reading order, already joined to its page before) hand on to the next page. */
    fun tailOf(units: List<ReadingUnit>): PageTail {
        val last = units.lastOrNull { it.kind !in TRAILING }
        val table = last?.table?.takeIf { last.kind == UnitKind.TABLE_ROW }?.let { info ->
            val headers = if (hasDataHeader(units, info)) emptyList() else info.headers
            TableTail(info.columnCount, headers, info.rowsBefore + info.rowCount, serialOf(last))
        }
        val open = last?.takeIf { it.kind in PROSE && breaksOff(it.text) }
        val caseless = open?.text?.lastOrNull { it.isLetter() }?.let { !it.isUpperCase() && !it.isLowerCase() } == true
        return PageTail(table, open?.kind, open != null && cue.endsFullLine(units, open), caseless)
    }

    /**
     * [units] (one page) joined to [previous], the tail of the page before (none for a first page or a page read
     * alone), and every table on it whose header row is data read with that row as data row 0 ([demoteHeadless]).
     */
    fun join(units: List<ReadingUnit>, previous: PageTail?): JoinedPage {
        val first = units.firstOrNull { it.kind != UnitKind.CAPTION }
        val carried = previous?.table?.let { tail -> carryTable(units, tail) }
        val continues = previous?.openSentence != null && first?.kind == previous.openSentence &&
            carriesOn(units, first, previous)
        return JoinedPage(demoteHeadless(carried?.first ?: units), continues, carried?.second)
    }

    /**
     * [units] with each table whose header row is data ([hasDataHeader]: a table with no header row of its own) read
     * with that row as data row 0 under a summary of no length, not continued, with no headers: "N rows, M columns."
     * and then every row. So its rows are numbered as heard and the next page numbers on from all of them.
     */
    private fun demoteHeadless(units: List<ReadingUnit>): List<ReadingUnit> {
        var out = units
        while (true) {
            val summary = out.firstOrNull { unit ->
                unit.kind == UnitKind.TABLE_SUMMARY && hasDataHeader(out, requireNotNull(unit.table))
            } ?: return out
            val tableIndex = requireNotNull(summary.table).tableIndex
            val tableUnits = out.indices.filter { out[it].table?.tableIndex == tableIndex }
            out = withDataFirst(
                out,
                tableUnits,
                summaryOf = { info ->
                    TableRowInfo(
                        tableIndex = info.tableIndex,
                        row = null,
                        rowCount = info.rowCount + 1,
                        columnCount = info.columnCount,
                        headers = emptyList(),
                        cells = emptyList(),
                    )
                },
                rowOf = { info, row -> info.copy(row = row, rowCount = info.rowCount + 1, headers = emptyList()) },
            )
        }
    }

    /**
     * The table's header row is data: the page opened a table with no header row of its own, so it hands on no
     * header. A summary already carried over (a continued table) holds the real header, and one of no length (a row
     * already demoted, or a continued table's) has no row to judge.
     */
    private fun hasDataHeader(units: List<ReadingUnit>, info: TableRowInfo): Boolean {
        val table = units.filter { it.table?.tableIndex == info.tableIndex }
        val summary = table.firstOrNull { it.kind == UnitKind.TABLE_SUMMARY }
        if (info.continued || summary == null || summary.charEnd == summary.charStart) return false
        val rows = table.filter { it.kind == UnitKind.TABLE_ROW }
        val summarySerial = serialOf(summary)
        val numbered = summarySerial != null && rows.firstOrNull()?.let(::serialOf) == summarySerial + 1
        return numbered || readsAsData(summary, rows, lastSerial = null)
    }

    /** [units] with their opening table carried on from [tail], or null when the page opens with no such table. */
    private fun carryTable(units: List<ReadingUnit>, tail: TableTail): Pair<List<ReadingUnit>, TableCarry>? {
        val start = units.indexOfFirst { it.kind != UnitKind.CAPTION }
        val opening = units.getOrNull(start)?.takeIf { unit ->
            unit.kind == UnitKind.TABLE_SUMMARY && unit.table?.columnCount == tail.columnCount &&
                units.subList(0, start).all { CONTINUED_CAPTION.containsMatchIn(it.text) }
        } ?: return null
        val info = requireNotNull(opening.table)
        val tableUnits = units.indices.filter { units[it].table?.tableIndex == info.tableIndex }
        val rows = tableUnits.drop(1).map { units[it] }
        return when {
            repeatsHeader(info, opening.text, tail.headers) -> units.mapIndexed { i, unit ->
                if (i in tableUnits) unit.withTable { it.copy(continued = true, rowsBefore = tail.rows) } else unit
            } to TableCarry.HEADER_REPEATED

            readsAsData(opening, rows, tail.lastSerial) -> dataFirst(units, tableUnits, tail) to TableCarry.DATA_FIRST

            else -> null
        }
    }

    /**
     * The table's first row read as data row 0 under the carried header, its other rows numbered on, and a continued
     * summary of no length set before it.
     */
    private fun dataFirst(units: List<ReadingUnit>, tableUnits: List<Int>, tail: TableTail): List<ReadingUnit> =
        withDataFirst(
            units,
            tableUnits,
            summaryOf = { info ->
                TableRowInfo(
                    tableIndex = info.tableIndex,
                    row = null,
                    rowCount = info.rowCount + 1,
                    columnCount = info.columnCount,
                    headers = tail.headers,
                    cells = emptyList(),
                    continued = true,
                    rowsBefore = tail.rows,
                )
            },
            rowOf = { info, row -> info.carried(row, tail) },
        )

    /**
     * More than half of the header cells that hold text, and at least `minHeaderMatchCells`, read like the carried
     * header's (letters, vowel signs and digits only).
     */
    private fun repeatsHeader(info: TableRowInfo, text: String, headers: List<String>): Boolean {
        if (headers.size != info.cells.size) return false
        val pairs = info.cells.map { text.substring(it.start, it.end).folded() }.zip(headers.map { it.folded() })
            .filter { (now, before) -> now.isNotEmpty() || before.isNotEmpty() }
        val same = pairs.count { (now, before) -> now == before }
        return same * 2 > pairs.size && same >= config.minHeaderMatchCells
    }

    /**
     * [first] is a row of data, not a header: its serial number follows [lastSerial] (the only evidence when its
     * years are as long as their columns' figures, [YearCue.UNDECIDED]), or its years say nothing ([yearCue]) and, in
     * at least `minRowFormatShare` of the columns whose [rows] are mostly written one way, it is written that way
     * too; and some such column holds numbers, else a header and a row cannot be told apart.
     */
    private fun readsAsData(first: ReadingUnit, rows: List<ReadingUnit>, lastSerial: Long?): Boolean {
        val numbered = lastSerial != null && serialOf(first) == lastSerial + 1
        return numbered ||
            (yearCue(first, rows) == YearCue.NONE && matchesFormats(first, rows, config.minRowFormatShare))
    }

    /**
     * The text stops inside a sentence: at least `minBrokenWords` words, ending on a letter, vowel sign, comma,
     * semicolon or dash with no full stop, question mark, exclamation or danda; a page number, running footer or
     * print slug the page kept ends on a digit.
     */
    private fun breaksOff(text: String): Boolean {
        val trimmed = text.trimEnd()
        val end = trimmed.trimEnd { it in CLOSERS }.lastOrNull() ?: return false
        val words = trimmed.split(' ').count { word -> word.any { it.isLetter() } }
        return words >= config.minBrokenWords && end !in SENTENCE_ENDS &&
            (end.isLetter() || end in RUN_ON || isCombiningMark(end))
    }

    /**
     * [first], the first unit of [page], opens like the rest of a sentence: with a small letter. When the sentence
     * before is in a script without capitals (or [first] is), the layout decides instead, whatever [first] opens with
     * (a Hindi sentence may go on with "RBI" or "2019"): the sentence before ran to the full width and [first]'s first
     * line is not indented. A page opening with an item's number ("2 ", "3.", "(4)") opens a new item.
     */
    private fun carriesOn(page: List<ReadingUnit>, first: ReadingUnit, previous: PageTail): Boolean {
        val letter = first.text.firstOrNull { it.isLetterOrDigit() } ?: return false
        return when {
            ITEM_NUMBER.containsMatchIn(first.text) -> false

            previous.caseless || !(letter.isDigit() || letter.isUpperCase() || letter.isLowerCase()) ->
                previous.endsFullLine && !cue.isIndented(page, first)

            else -> letter.isLowerCase()
        }
    }

    private companion object {
        /** Units printed after a page's last text: notes at its foot. */
        val TRAILING = setOf(UnitKind.FOOTNOTE)

        val SENTENCE_ENDS = setOf('.', '?', '!', '।', '॥', '…')
        const val CLOSERS = "\"'’”»)]"
        const val RUN_ON = ",;-–—"

        /**
         * A list or section number of one or two digits opening the text: "2.", "3)", "(4)". A bare number before a
         * word ("15 अगस्त को…") may go on the sentence before. Only punctuation and spaces may come before it (`\W`
         * would also pass over Devanagari and Bengali words, which are not ASCII).
         */
        val ITEM_NUMBER = Regex("""^[\p{P}\s]*\p{Nd}{1,2}[.)](\s|$)""")

        /**
         * A caption over the rest of a table: "Table 2 (contd.)", "(continued)", and the Hindi and Bengali words alone,
         * bracketed ("तालिका 2 (जारी)", "(ক্রমশ)") or ending the caption ("…जारी है", "…क्रमशः"): within a caption
         * they are ordinary words too ("जारी किए गए आँकड़े").
         */
        val CONTINUED_CAPTION = Regex(
            """(?i)\b(contd|cont'd|continued)\b|""" +
                """\(\s*(?:जारी|क्रमशः?|ক্রমশঃ?)\s*\)|(?:^|\s)(?:जारी(?:\s+है)?|क्रमशः?|ক্রমশঃ?)\s*[।.]?\s*$""",
        )
    }
}

/**
 * The table at [tableUnits] with its first unit read as data row 0 and its other rows numbered on ([rowOf], given
 * a row's info and its new row number), and a summary of no length ([summaryOf], given the first unit's info)
 * set before it at the first unit's start; every later unit's index moves up by one.
 */
private fun withDataFirst(
    units: List<ReadingUnit>,
    tableUnits: List<Int>,
    summaryOf: (TableRowInfo) -> TableRowInfo,
    rowOf: (TableRowInfo, Int) -> TableRowInfo,
): List<ReadingUnit> {
    val first = tableUnits.first()
    val opening = units[first]
    val rowPause = tableUnits.drop(1).firstOrNull()?.let { units[it].pauseAfterMs } ?: opening.pauseAfterMs
    val summary = opening.copy(
        charEnd = opening.charStart,
        text = "",
        bounds = null,
        speakScore = null,
        table = summaryOf(requireNotNull(opening.table)),
        boldTerms = emptyList(),
    )
    val out = ArrayList<ReadingUnit>(units.size + 1)
    units.forEachIndexed { i, unit ->
        val moved = if (i >= first) unit.copy(index = unit.index + 1) else unit
        when {
            i == first -> {
                out.add(summary)
                out.add(moved.copy(kind = UnitKind.TABLE_ROW, pauseAfterMs = rowPause).withTable { rowOf(it, 0) })
            }

            i in tableUnits -> out.add(moved.withTable { rowOf(it, requireNotNull(it.row) + 1) })

            else -> out.add(moved)
        }
    }
    return out
}

/**
 * Whether [units] (one page) open with a table, after captions: only then does what the page hands on depend on the
 * page before ([PageCarry]).
 */
internal fun opensWithTable(units: List<ReadingUnit>): Boolean =
    units.firstOrNull { it.kind != UnitKind.CAPTION }?.kind == UnitKind.TABLE_SUMMARY

/** Whether [units] (one page) open with body text, a list item or an aside: a sentence it may carry on. */
internal fun opensWithProse(units: List<ReadingUnit>): Boolean =
    units.firstOrNull { it.kind != UnitKind.CAPTION }?.kind in PROSE

/** Kinds a sentence runs on in over a page break. */
internal val PROSE = setOf(UnitKind.BODY, UnitKind.LIST_ITEM, UnitKind.ASIDE)

private fun TableRowInfo.carried(row: Int, tail: TableTail): TableRowInfo = copy(
    row = row,
    rowCount = rowCount + 1,
    headers = tail.headers,
    continued = true,
    rowsBefore = tail.rows,
)

private fun ReadingUnit.withTable(change: (TableRowInfo) -> TableRowInfo): ReadingUnit =
    copy(table = change(requireNotNull(table)))

private fun String.folded(): String = filter { it.isLetterOrDigit() || isCombiningMark(it) }.lowercase()
