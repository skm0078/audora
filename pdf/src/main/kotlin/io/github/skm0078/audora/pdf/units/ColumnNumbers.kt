// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/units/ColumnNumbers.kt
// Purpose: Tells a table row that numbers its columns ("1 | 2 | 3 | 4"), a header, from a row of data.

package io.github.skm0078.audora.pdf.units

import io.github.skm0078.audora.core.model.ReadingUnit

/**
 * [first] numbers its columns: every cell holding a number holds its column's place, counted from 0 or 1 alike
 * across the row ("1 | 2 | 3 | 4", "Item | 1 | 2 | 3"), in at least [MIN_COUNTED_COLUMNS] columns, and its other
 * cells are words. A grid numbered on row by row ("1 | 2 | 3 | 4" over "5 | 6 | 7 | 8") is data: the first of
 * [rows] carries the count on. Small counts ("Ram | 1 | 2", "Ram | 0 | 1") are data too.
 */
internal fun countsColumns(first: ReadingUnit, rows: List<ReadingUnit>): Boolean {
    val cells = cellsOf(first).map { asciiDigits(it.trim()) }
    val numbers = cells.withIndex().filter { (_, cell) -> cell.any(Char::isDigit) }
    val offsets = numbers.map { (column, cell) -> cell.toLongOrNull()?.minus(column) }
    val offset = offsets.firstOrNull()
    val counted = numbers.size >= MIN_COUNTED_COLUMNS && (offset == 0L || offset == 1L) && offsets.all { it == offset }
    val next = rows.firstOrNull()?.let(::cellsOf)?.map { asciiDigits(it.trim()).toLongOrNull() }
    return counted &&
        (next == null || !numbers.all { (column, cell) -> next.getOrNull(column) == cell.toLong() + cells.size })
}

/** Column numbers count on ("1 | 2 | 3") across at least this many columns before they name the columns. */
private const val MIN_COUNTED_COLUMNS = 3
