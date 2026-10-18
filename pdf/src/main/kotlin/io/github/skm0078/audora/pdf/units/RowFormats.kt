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
        val holdsYears = columnValues(rows, column).all { YEAR.matches(ascii(it)) }
        YEAR.matchEntire(ascii(cell.trim()))?.takeUnless { holdsYears }?.let { column to it }
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
private fun ascii(text: String): String = text.map { c -> c.digitToIntOrNull()?.let { '0' + it } ?: c }.joinToString("")

/**
 * In at least [share] of the columns whose [rows] are mostly written one way, [first] is written that way too, and
 * some such column holds numbers, else a header and a row cannot be told apart.
 */
internal fun matchesFormats(first: ReadingUnit, rows: List<ReadingUnit>, share: Float): Boolean {
    val firstFormats = cellsOf(first).map(::formatOf)
    val majority = majorityFormats(first, rows)
    val judged = majority.indices.filter { majority[it] != null }
    val same = judged.count { firstFormats.getOrNull(it) == majority[it] }
    return judged.any { requireNotNull(majority[it]).contains(DIGIT) } && same >= judged.size * share
}

/**
 * [first] is written like its [rows] in every column whose rows mostly hold numbers written one way, or is empty
 * there ("-"), in at least one such column it is written so, and it holds no year: a header names its number
 * columns in words ("Name | Age | City" over "Ram | 34 | Pune") or by year ("District | 2011 | 2021"), so only a
 * row of data passes. Text columns say nothing: a word header is written like the words under it.
 */
internal fun matchesNumberColumns(first: ReadingUnit, rows: List<ReadingUnit>): Boolean {
    val firstFormats = cellsOf(first).map(::formatOf)
    val majority = majorityFormats(first, rows)
    val numeric = majority.indices.filter { majority[it]?.contains(DIGIT) == true }
    val holdsYear = cellsOf(first).any { YEAR.matches(ascii(it.trim())) }
    val matching = numeric.count { firstFormats.getOrNull(it) == majority[it] }
    val empty = numeric.count { firstFormats.getOrNull(it) == null }
    return matching > 0 && matching + empty == numeric.size && !holdsYear
}

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
    return first?.let(::ascii)?.toLongOrNull()
}

/** A vowel sign or virama: "है" ends on a sign, not a letter, and "नाम" is not "नमी". */
internal fun isCombiningMark(c: Char): Boolean = Character.getType(c).let {
    it == Character.NON_SPACING_MARK.toInt() || it == Character.COMBINING_SPACING_MARK.toInt()
}

/** How a cell is written ("28,400" and "5739": "9"; "Ram Lal": "a"; "2018-19": "9-9"); null when empty. */
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

/** A row whose numbers are two or more years names years ("District | 2011 | 2021"), whatever their columns hold. */
private const val MIN_HEADER_YEARS = 2
