// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/normalize/TableSpeech.kt
// Purpose: The words a table is read with, phrased the way a person explains a table (RI-4b, Q-4): what it holds,
// then each row led by its name ("2021: sales 40 crore, and profit 8 crore.").

package io.github.skm0078.audora.speech.normalize

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.TableRowInfo

/**
 * Written text, not final speech: playback hands it to the voice like any sentence, so the normalizers still read its
 * numbers, dates and units ("34" → "thirty-four"). Nothing here is added to `ReadingUnit.text` (ADR-018).
 *
 * The header unit says how big the table is and what its columns are ("A table of 4 rows, with columns for Name, Age,
 * and City."), so the Summary mode tells the listener what the table is about, not only its size. Up to
 * [MAX_NAMED_COLUMNS] columns all named are listed; a wider table, or one with unnamed columns, names its first
 * [NAMED_IN_WIDE] distinct names ("including …"). Names are said without their bracketed tail ("(kg)", "(n = 104)").
 *
 * A row is one sentence the way a person reads it out: its first cell names the row ("Ram: age 34, and city Delhi.")
 * and every other non-empty cell goes with its column's header, joined by commas and an "and" before the last. The
 * old "Row 3. Name: Ram. Age: 34." put a full stop, a fall in pitch and a long pause after every cell, which is what
 * made tables sound robotic. A row with more than [MAX_PAIRS_PER_SENTENCE] cells is split into sentences of that many,
 * so a wide table still has places to breathe. A first cell that is only a number ("2019", "3.5") keeps its header in
 * front ("Year 2019: …"), since a bare number does not say what it is.
 *
 * A header's unit in brackets goes after the value, as said aloud: "Weight (kg)" and "34" read "Weight 34 kg", "Growth
 * (%)" "Growth 12%", "Price (₹ crore)" "Price ₹40 crore". A cell holding only a rule ("–", "-----", the way tables
 * write nil) is the language's word for nil: the voices say nothing for a dash, which left "2019:" hanging. A cell of
 * dots only ("..", statistics' "not available") is skipped like an empty one. A table without a header row reads its
 * cells in order, separated by commas. A row with nothing to say falls back to its number, so it is never silent.
 */
object TableSpeech {
    /** A row with more filled cells than this reads as more than one sentence. */
    const val MAX_PAIRS_PER_SENTENCE = 4

    /** A summary names every column up to this many; a wider table names its first few ([NAMED_IN_WIDE]). */
    const val MAX_NAMED_COLUMNS = 4

    /** How many columns the summary of a wide table names. */
    const val NAMED_IN_WIDE = 3

    /**
     * "A table of 4 rows, with columns for Name, Age and City." counting data rows only (the header row is not a row
     * the listener counts); a table carried over from the page before is "The table continues, with 7 more rows."
     * instead, its columns already announced.
     */
    fun summary(table: TableRowInfo, lang: Lang): String {
        val words = WORDS.getValue(lang)
        if (table.continued) return words.continues(table.rowCount) + words.stop
        val named = table.headers.map { header -> BRACKET_TAIL.replace(cleanHeader(header), "") }
            .filter { it.isNotEmpty() }
        val distinct = named.distinct()
        val sentence = when {
            named.isEmpty() -> words.sized(table.rowCount, table.columnCount)

            named.size == table.columnCount && named.size <= MAX_NAMED_COLUMNS ->
                words.withColumns(table.rowCount, named.size, words.list(named))

            else -> words.including(table.rowCount, table.columnCount, words.list(distinct.take(NAMED_IN_WIDE)))
        }
        return sentence + words.stop
    }

    /** One data row of [table] whose cells are spans of [text]; [summary] for the header unit (row null). */
    fun row(table: TableRowInfo, text: String, lang: Lang): String {
        val row = table.row ?: return summary(table, lang)
        val words = WORDS.getValue(lang)
        val cells = table.cells.map { span -> spokenCell(text.substring(span.start, span.end).trim(), words) }
        val sentences = if (table.headers.isEmpty()) {
            listOfNotNull(cells.filter { it.isNotEmpty() }.joinToString(", ").ifEmpty { null })
        } else {
            labelled(cells, table.headers.map(::cleanHeader), words)
        }
        val spoken = sentences.ifEmpty { listOf("${words.row} ${table.rowsBefore + row + 1}") }
        return spoken.joinToString(" ") { it + words.stop }
    }

    /** The row's name, then its other cells with their headers, in sentences of at most [MAX_PAIRS_PER_SENTENCE]. */
    private fun labelled(cells: List<String>, headers: List<String>, words: TableWords): List<String> {
        val first = cells.firstOrNull().orEmpty()
        val lead = when {
            first.isEmpty() -> null
            NUMBER.matches(first) -> paired(headers.firstOrNull().orEmpty(), first)
            else -> first
        }
        val pairs = cells.drop(1).mapIndexedNotNull { i, cell ->
            cell.takeIf { it.isNotEmpty() }?.let { paired(headers.getOrNull(i + 1).orEmpty(), it) }
        }
        if (pairs.isEmpty()) return listOfNotNull(lead)
        val chunks = pairs.chunked(MAX_PAIRS_PER_SENTENCE)
        return chunks.mapIndexed { i, chunk ->
            val joined = if (i == chunks.lastIndex) words.list(chunk) else chunk.joinToString(", ")
            if (i == 0 && lead != null) "$lead: $joined" else joined
        }
    }

    /** "Weight 34 kg": the header's name, the value, and the header's unit where a person says it. */
    private fun paired(header: String, value: String): String {
        val (name, unit) = splitUnit(header)
        val withUnit = if (unit.isEmpty() || !NUMBER.matches(value)) value else withUnit(value, unit)
        return if (name.isEmpty()) withUnit else "$name $withUnit"
    }

    private fun withUnit(value: String, unit: String): String {
        val symbol = unit.takeWhile { it in CURRENCY }
        val rest = unit.drop(symbol.length).trim()
        return when {
            unit == "%" -> "$value%"
            symbol.isNotEmpty() -> "$symbol$value${if (rest.isEmpty()) "" else " $rest"}"
            else -> "$value $unit"
        }
    }

    /** "Weight (kg)" → ("Weight", "kg"); "Sales (in crore)" → ("Sales", "crore"); a header with no unit as it is. */
    private fun splitUnit(header: String): Pair<String, String> {
        val match = UNIT.matchEntire(header)
        val unit = match?.groupValues?.get(2)?.trim()?.removePrefix("in ")?.trim().orEmpty()
        val isUnit = unit.isNotEmpty() && unit.none(Char::isDigit) && unit.count { it == ' ' } < MAX_UNIT_WORDS
        return if (match != null && isUnit) match.groupValues[1].trim() to unit else header to ""
    }

    /** A header without its trailing colon; one ruled through or of dots only names nothing. */
    private fun cleanHeader(header: String): String {
        val clean = header.trim().trimEnd(':', '：').trim()
        return if (RULE_ONLY.matches(clean) || DOTS_ONLY.matches(clean)) "" else clean
    }

    private fun spokenCell(cell: String, words: TableWords): String = when {
        RULE_ONLY.matches(cell) -> words.nil
        DOTS_ONLY.matches(cell) -> ""
        else -> cell
    }

    /** A language's words for tables; Hindi and Bengali end sentences with a danda. */
    @Suppress("LongParameterList") // One word list per language, each phrase a parameter.
    private class TableWords(
        val row: String,
        val stop: String,
        val nil: String,
        private val and: String,
        private val serialComma: Boolean,
        val sized: (rows: Int, columns: Int) -> String,
        val withColumns: (rows: Int, columns: Int, names: String) -> String,
        val including: (rows: Int, columns: Int, names: String) -> String,
        val continues: (rows: Int) -> String,
    ) {
        /** "A, B and C" ("A, B, and C" in English). */
        fun list(items: List<String>): String = when (items.size) {
            0 -> ""
            1 -> items.single()
            2 -> "${items[0]} $and ${items[1]}"
            else -> items.dropLast(1).joinToString(", ") + (if (serialComma) ", " else " ") + "$and ${items.last()}"
        }
    }

    /** Dashes and minus signs only, maybe with a note mark ("–*", "–¹"): a cell ruled through instead of filled. */
    private val RULE_ONLY = Regex("""[\p{Pd}−]+[*†‡§¹²³⁴-⁹]*""")

    /** Dots only (".." for "not available", too short for a leader): left empty, so skipped with its header. */
    private val DOTS_ONLY = Regex("""[.…·]+""")

    /** A plain number, maybe signed, with digit grouping or a decimal point: "2019", "-3.5", "1,234", "৩৪". */
    private val NUMBER = Regex("""[+\-−]?\p{Nd}[\p{Nd},.]*""")

    /** Whatever a header ends with in brackets ("(kg)", "(n = 104)"): a summary names columns without it. */
    private val BRACKET_TAIL = Regex("""\s*[(\[][^()\[\]]*[)\]]\s*$""")

    /** A header ending in a short bracketed unit: "Weight (kg)", "Price (₹)", "Sales (in crore)". */
    private val UNIT = Regex("""(.*?)\s*[(\[]\s*([^()\[\]]{1,15}?)\s*[)\]]""")

    private const val CURRENCY = "₹$€£¥"

    /** A bracket of this many words or more is a note ("(see below)"), not a unit; one with a digit is a year. */
    private const val MAX_UNIT_WORDS = 2

    private val WORDS = mapOf(
        Lang.EN to TableWords(
            row = "Row",
            stop = ".",
            nil = "nil",
            and = "and",
            serialComma = true,
            sized = { rows, columns ->
                "A table of ${counted(rows, "row", "rows")} and ${counted(columns, "column", "columns")}"
            },
            withColumns = { rows, columns, names ->
                "A table of ${counted(
                    rows,
                    "row",
                    "rows",
                )}, with ${if (columns == 1) "a column" else "columns"} for $names"
            },
            including = { rows, columns, names ->
                "A table of ${counted(
                    rows,
                    "row",
                    "rows",
                )} and ${counted(columns, "column", "columns")}, including $names"
            },
            continues = { rows -> "The table continues, with $rows more ${if (rows == 1) "row" else "rows"}" },
        ),
        Lang.HI to TableWords(
            row = "पंक्ति",
            stop = "।",
            nil = "कुछ नहीं",
            and = "और",
            serialComma = false,
            sized = { rows, columns ->
                "${counted(rows, "पंक्ति", "पंक्तियों")} और ${counted(columns, "स्तंभ", "स्तंभों")} की तालिका"
            },
            withColumns = { rows, _, names ->
                "${counted(rows, "पंक्ति", "पंक्तियों")} की तालिका, जिसमें $names के स्तंभ हैं"
            },
            including = { rows, columns, names ->
                "${counted(
                    rows,
                    "पंक्ति",
                    "पंक्तियों",
                )} और ${counted(columns, "स्तंभ", "स्तंभों")} की तालिका, जिनमें $names हैं"
            },
            continues = { rows -> "तालिका जारी है, और $rows ${if (rows == 1) "पंक्ति" else "पंक्तियाँ"}" },
        ),
        Lang.BN to TableWords(
            row = "সারি",
            stop = "।",
            nil = "নেই",
            and = "আর",
            serialComma = false,
            sized = { rows, columns -> "$rows সারি আর $columns কলামের একটি সারণি" },
            withColumns = { rows, _, names -> "$rows সারির একটি সারণি, কলামগুলো হল $names" },
            including = { rows, columns, names ->
                "$rows সারি আর $columns কলামের একটি সারণি, তার মধ্যে আছে $names"
            },
            continues = { rows -> "সারণি চলছে, আরও $rows সারি" },
        ),
    )

    /** "4 rows", "1 row"; Hindi passes its oblique plural for before की ("4 पंक्तियों"). */
    private fun counted(count: Int, one: String, many: String) = "$count ${if (count == 1) one else many}"
}
