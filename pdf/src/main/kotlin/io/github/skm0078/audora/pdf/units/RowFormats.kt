// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/units/RowFormats.kt
// Purpose: How a table row's cells are written (numbers, words, years, serial numbers), to tell data from a header.

package io.github.skm0078.audora.pdf.units

import io.github.skm0078.audora.core.model.ReadingUnit

/** The texts of a table unit's cells, in column order. */
internal fun cellsOf(unit: ReadingUnit): List<String> =
    requireNotNull(unit.table).cells.map { unit.text.substring(it.start, it.end) }

/**
 * [first] names years, as a header does: every cell of it with a number is a year or a span of years ("2018-19",
 * "१९९८–२००३") in a column whose [rows] are not years themselves (a table with a row per year), and the years show it:
 * a span, two or more of them, or one year above numbers of fewer figures ("2020" over "120"). So "68 | Raj Kumar |
 * 2000" is data (its serial number is no year), and so is a salary of 2000 among salaries of five figures.
 */
internal fun namesYears(first: ReadingUnit, rows: List<ReadingUnit>): Boolean {
    val numbers = cellsOf(first).withIndex().filter { (_, cell) -> cell.any(Char::isDigit) }
    val years = numbers.mapNotNull { (column, cell) ->
        YEAR.matchEntire(ascii(cell.trim()))?.takeUnless { holdsYears(rows, column) }?.let { column to it }
    }
    val single = years.singleOrNull()
    return when {
        years.isEmpty() || years.size < numbers.size -> false

        single == null -> true

        else -> {
            val (column, year) = single
            val longest = columnValues(rows, column).maxOfOrNull { value -> value.count(Char::isDigit) } ?: 0
            year.groupValues[2].isNotEmpty() || year.groupValues[1].length > longest
        }
    }
}

/** The non-empty values of [column] in [rows]. */
private fun columnValues(rows: List<ReadingUnit>, column: Int): List<String> =
    rows.mapNotNull { row -> cellsOf(row).getOrNull(column)?.trim()?.takeIf { it.isNotEmpty() } }

/** At least half of [column]'s values in [rows] are years, or it has none: a year there may be data. */
private fun holdsYears(rows: List<ReadingUnit>, column: Int): Boolean {
    val values = columnValues(rows, column)
    return values.isEmpty() || values.count { YEAR.matches(ascii(it)) } * 2 >= values.size
}

/** [text] with every script's digits written as ASCII digits ("१९९८" to "1998"). */
private fun ascii(text: String): String = text.map { c -> c.digitToIntOrNull()?.let { '0' + it } ?: c }.joinToString("")

/**
 * In at least [share] of the columns whose [rows] are mostly written one way, [first] is written that way too, and
 * some such column holds numbers, else a header and a row cannot be told apart.
 */
internal fun matchesFormats(first: ReadingUnit, rows: List<ReadingUnit>, share: Float): Boolean {
    val firstFormats = formatsOf(first)
    val rowFormats = rows.map(::formatsOf)
    val majority = (0 until requireNotNull(first.table).columnCount).map { c ->
        rowFormats.mapNotNull { it.getOrNull(c) }.groupingBy { it }.eachCount().maxByOrNull { it.value }
            ?.takeIf { it.value * 2 > rowFormats.size }?.key
    }
    val judged = majority.indices.filter { majority[it] != null }
    val same = judged.count { firstFormats.getOrNull(it) == majority[it] }
    return judged.any { requireNotNull(majority[it]).contains(DIGIT) } && same >= judged.size * share
}

/** The number a row's first cell holds alone ("12", "12.", "१२)"), any script's digits; null when it holds more. */
internal fun serialOf(unit: ReadingUnit): Long? {
    val first = cellsOf(unit).firstOrNull()?.trim()?.trimEnd('.', ')')
        ?.takeIf { it.isNotEmpty() && it.length <= MAX_SERIAL_DIGITS && it.all(Char::isDigit) }
    return first?.let(::ascii)?.toLongOrNull()
}

/** A vowel sign or virama: "है" ends on a sign, not a letter, and "नाम" is not "नमी". */
internal fun isCombiningMark(c: Char): Boolean = Character.getType(c).let {
    it == Character.NON_SPACING_MARK.toInt() || it == Character.COMBINING_SPACING_MARK.toInt()
}

/** Per cell, how it is written ("28,400" and "5739": "9"; "Ram Lal": "a"; "2018-19": "9-9"); null when empty. */
private fun formatsOf(unit: ReadingUnit): List<String?> = cellsOf(unit).map(::formatOf)

private fun formatOf(cell: String): String? {
    val text = cell.trim()
    if (text.none { it.isLetterOrDigit() }) return null
    val out = StringBuilder()
    text.forEachIndexed { i, c ->
        val digitGroup = (c == ',' || c == '.') && text.getOrNull(i + 1)?.isDigit() == true &&
            text.getOrNull(i - 1)?.isDigit() == true
        val kind = when {
            c.isWhitespace() -> null
            c.isDigit() || digitGroup -> DIGIT
            c.isLetter() || isCombiningMark(c) -> LETTER
            else -> c
        }
        if (kind != null && out.lastOrNull() != kind) out.append(kind)
    }
    return out.toString()
}

/** A year, alone or as a span (group 2). */
private val YEAR = Regex("""^((?:1[89]|20)\d\d)(\s*[-–/]\s*\d{2,4})?$""")

/** How cells are written ([formatOf]): a run of digits (with their group commas and points) "9", of letters "a". */
private const val DIGIT = '9'
private const val LETTER = 'a'
private const val MAX_SERIAL_DIGITS = 6
