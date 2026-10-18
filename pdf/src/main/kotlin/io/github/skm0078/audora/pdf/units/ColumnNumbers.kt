// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/units/ColumnNumbers.kt
// Purpose: Tells a table row that numbers its columns ("1 | 2 | 3 | 4"), a header, from a row of data.

package io.github.skm0078.audora.pdf.units

import io.github.skm0078.audora.core.model.ReadingUnit

/**
 * [first] numbers its columns: its numbers count on one per column, from 0 or 1, across the row ("1 | 2 | 3 | 4",
 * "Item | 1 | 2 | 3", "Name | Class | 1 | 2 | 3", "(1) | (2) | (3)"), in at least [MIN_COUNTED_COLUMNS] columns,
 * and its other cells are words. A grid numbered on row by row ("1 | 2 | 3 | 4" over "5 | 6 | 7 | 8") is data: the
 * first of [rows] carries the count on. Small counts ("Ram | 1 | 2", "Ram | 0 | 1") are data too.
 */
internal fun countsColumns(first: ReadingUnit, rows: List<ReadingUnit>): Boolean {
    val texts = cellsOf(first)
    val cells = texts.map(::columnNumber)
    val numbers = texts.indices.filter { texts[it].any(Char::isDigit) }
    val values = numbers.map { cells[it] }
    val offsets = numbers.map { column -> cells[column]?.minus(column) }
    val counted = numbers.size >= MIN_COUNTED_COLUMNS && (values.first() == 0L || values.first() == 1L) &&
        offsets.all { it != null && it == offsets.first() }
    val next = rows.firstOrNull()?.let(::cellsOf)?.map(::columnNumber)
    val countsOn = { column: Int -> next?.getOrNull(column) == requireNotNull(cells[column]) + cells.size }
    return counted && (next == null || !numbers.all(countsOn))
}

/** The number a cell holds, brackets and a full stop around it allowed ("(3)", "3."), any script's digits. */
private fun columnNumber(cell: String): Long? = asciiDigits(cell.trim().trim('(', ')', '.')).toLongOrNull()

/** Column numbers count on ("1 | 2 | 3") across at least this many columns before they name the columns. */
private const val MIN_COUNTED_COLUMNS = 3
