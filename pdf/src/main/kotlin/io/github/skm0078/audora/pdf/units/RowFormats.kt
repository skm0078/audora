// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/units/RowFormats.kt
// Purpose: How a table row's cells are written (numbers, words, years, serial numbers), to tell data from a header.

package io.github.skm0078.audora.pdf.units

import io.github.skm0078.audora.core.model.ReadingUnit

/** The texts of a table unit's cells, in column order. */
internal fun cellsOf(unit: ReadingUnit): List<String> =
    requireNotNull(unit.table).cells.map { unit.text.substring(it.start, it.end) }

/** What the years in a table's first row say about it ([yearCue]). */
internal enum class YearCue {
    /** A header naming years ("2018-19", or "2020" over figures of fewer digits). */
    HEADER,

    /** Plain years as long as their columns' figures ("2020" over "5352"): a header or data alike. */
    UNDECIDED,

    /** No years to go by: a number that is no year, years where the column holds years, or a year shorter than its
     *  column's figures (a salary of 2000 among salaries of five figures). */
    NONE,
}

/**
 * What [first]'s years say, against the data [rows] under it. Only a row whose every number is a year counts, and
 * not in a column whose values are all years (a table with a row per year): two or more years, a span of years
 * ("2018-19", "१९९८–२००३") or a plain year with more digits than its column's figures ("2020" over "120") name
 * years. A single plain year as long as its column's figures is [YearCue.UNDECIDED]; one shorter says nothing.
 */
internal fun yearCue(first: ReadingUnit, rows: List<ReadingUnit>): YearCue {
    val numbers = cellsOf(first).withIndex().filter { (_, cell) -> cell.any(Char::isDigit) }
    val years = numbers.mapNotNull { (column, cell) ->
        // A column whose every value is a year (or that has none) may hold a year as data.
        val holdsYears = columnValues(rows, column).all { YEAR.matches(asciiDigits(it)) }
        YEAR.matchEntire(asciiDigits(cell.trim()))?.takeUnless { holdsYears }?.let { column to it }
    }
    val cues = years.map { (column, year) ->
        val longest = columnValues(rows, column).maxOfOrNull { value -> value.count(Char::isDigit) } ?: 0
        val digits = year.groupValues[1].length
        when {
            year.groupValues[2].isNotEmpty() || digits > longest -> YearCue.HEADER
            digits == longest -> YearCue.UNDECIDED
            else -> YearCue.NONE
        }
    }
    return when {
        years.isEmpty() || years.size < numbers.size -> YearCue.NONE
        years.size >= MIN_HEADER_YEARS || YearCue.HEADER in cues -> YearCue.HEADER
        YearCue.UNDECIDED in cues -> YearCue.UNDECIDED
        else -> YearCue.NONE
    }
}

/** The non-empty values of [column] in [rows]. */
private fun columnValues(rows: List<ReadingUnit>, column: Int): List<String> =
    rows.mapNotNull { row -> cellsOf(row).getOrNull(column)?.trim()?.takeIf { it.isNotEmpty() } }

/** [text] with every script's digits written as ASCII digits ("१९९८" to "1998"). */
internal fun asciiDigits(text: String): String =
    text.map { c -> c.digitToIntOrNull()?.let { '0' + it } ?: c }.joinToString("")

/**
 * [first] is written like its [rows] in every column whose rows mostly hold numbers written one way, or is empty
 * there ("-", "Nil", "N.A."), and in at least one such column it is written so: a header names its number columns
 * in words ("Name | Age | City" over "Ram | 34 | Pune"), so only a row of data passes. Text columns say nothing: a
 * word header is written like the words under it. Numbers that name the columns do not pass either: codes written
 * with a leading zero over figures without ("Name | 041 | 042" over marks), or columns counted ([countsColumns]).
 * [holdsYear] is the caller's to add: a row of years may be a header or data.
 */
internal fun numberColumnsMatch(first: ReadingUnit, rows: List<ReadingUnit>): Boolean {
    val cells = cellsOf(first).map { asciiDigits(it.trim()) }
    val firstFormats = cells.map(::formatOf)
    val majority = majorityFormats(first, rows)
    val numeric = majority.indices.filter { majority[it]?.contains(DIGIT) == true }
    val matching = numeric.filter { firstFormats.getOrNull(it) == majority[it] }
    val empty = numeric.count { firstFormats.getOrNull(it) == null }
    val values = matching.map { cells[it] }
    val codes = values.all { ZERO_PADDED.matches(it) } &&
        matching.none { column -> columnValues(rows, column).any { ZERO_PADDED.matches(asciiDigits(it)) } }
    return matching.isNotEmpty() && matching.size + empty == numeric.size && !codes && !countsColumns(first, rows)
}

/** One of [first]'s cells is a year or a span of years ("2011", "2018-19"), any script's digits. */
internal fun holdsYear(first: ReadingUnit): Boolean = cellsOf(first).any { YEAR.matches(asciiDigits(it.trim())) }

/** Per column of [first]'s table, how more than half of [rows] write it ([formatOf]), or null. */
private fun majorityFormats(first: ReadingUnit, rows: List<ReadingUnit>): List<String?> {
    val rowFormats = rows.map { cellsOf(it).map(::formatOf) }
    return (0 until requireNotNull(first.table).columnCount).map { c ->
        rowFormats.mapNotNull { it.getOrNull(c) }.groupingBy { it }.eachCount().maxByOrNull { it.value }
            ?.takeIf { it.value * 2 > rowFormats.size }?.key
    }
}

/** The number a row's first cell holds alone ("12", "12.", "१२)"), any script's digits; null when it holds more. */
internal fun serialOf(unit: ReadingUnit): Long? {
    val first = cellsOf(unit).firstOrNull()?.trim()?.trimEnd('.', ')')
        ?.takeIf { it.isNotEmpty() && it.length <= MAX_SERIAL_DIGITS && it.all(Char::isDigit) }
    return first?.let(::asciiDigits)?.toLongOrNull()
}

/** A vowel sign or virama: "है" ends on a sign, not a letter, and "नाम" is not "नमी". */
internal fun isCombiningMark(c: Char): Boolean = Character.getType(c).let {
    it == Character.NON_SPACING_MARK.toInt() || it == Character.COMBINING_SPACING_MARK.toInt()
}

/** How a cell is written ("28,400" and "5739": "9"; "Ram Lal": "a"; "2018-19": "9-9"); null when empty or nil. */
private fun formatOf(cell: String): String? {
    val text = cell.trim()
    if (text.none { it.isLetterOrDigit() } || NIL_WORDS.matches(text)) return null
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

/** A number written with a leading zero, as a code is ("041"). */
private val ZERO_PADDED = Regex("""0\d+""")

/** A cell that says it holds no figure: "Nil", "N.A.", "NA", "N/A", "निरंक", "শূন্য"; read as an empty cell. */
private val NIL_WORDS = Regex("""(?i:nil|n\.?[ ]?a\.?|n/a)|निरंक|শূন্য""")

/** A row whose numbers are two or more years names years ("District | 2011 | 2021"), whatever their columns hold. */
private const val MIN_HEADER_YEARS = 2
