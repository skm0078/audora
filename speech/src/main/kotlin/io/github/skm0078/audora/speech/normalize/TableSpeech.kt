// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/normalize/TableSpeech.kt
// Purpose: The words a table is read with: its size, and each row as "Row 3. Name: Ram. Age: 34." (RI-4b); a
// table carried over from the page before says it continues and numbers its rows on.

package io.github.skm0078.audora.speech.normalize

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.TableRowInfo

/**
 * Written text, not final speech: playback hands it to the voice like any sentence, so the normalizers still read its
 * numbers, dates and units ("34" → "thirty-four"). Nothing here is added to `ReadingUnit.text` (ADR-018).
 *
 * A row pairs every non-empty cell with its column's header ("Age: 34"); a table without a header row reads its
 * cells in order, separated by commas. Rows are numbered from 1, as a person counts them. A cell holding only a rule
 * ("–", "-----", the way tables write nil) is read as the language's word for nil: the voices say nothing for a dash,
 * which left "2019:" hanging. A cell of dots only ("..", statistics' "not available") is skipped like an empty one.
 */
object TableSpeech {
    /**
     * "4 rows, 5 columns." counting data rows only (the header row is not a row the listener counts); a table carried
     * over from the page before is "Table continues, 7 more rows." instead, its columns already announced.
     */
    fun summary(table: TableRowInfo, lang: Lang): String {
        val words = WORDS.getValue(lang)
        return if (table.continued) {
            "${words.continues}, ${words.moreRows(table.rowCount)}${words.stop}"
        } else {
            "${words.rows(table.rowCount)}, ${words.columns(table.columnCount)}${words.stop}"
        }
    }

    /** One data row of [table] whose cells are spans of [text]; [summary] for the header unit (row null). */
    fun row(table: TableRowInfo, text: String, lang: Lang): String {
        val row = table.row ?: return summary(table, lang)
        val words = WORDS.getValue(lang)
        val cells = table.cells.map { span ->
            text.substring(span.start, span.end).trim().let { cell ->
                when {
                    RULE_ONLY.matches(cell) -> words.nil
                    DOTS_ONLY.matches(cell) -> ""
                    else -> cell
                }
            }
        }
        val spokenCells = if (table.headers.isEmpty()) {
            listOfNotNull(cells.filter { it.isNotEmpty() }.joinToString(", ").ifEmpty { null })
        } else {
            cells.mapIndexedNotNull { column, cell ->
                val header = table.headers.getOrNull(column)?.trim()?.trimEnd(':', '：')?.trim().orEmpty()
                when {
                    cell.isEmpty() -> null
                    header.isEmpty() || RULE_ONLY.matches(header) -> cell
                    else -> "$header: $cell"
                }
            }
        }
        return (listOf("${words.row} ${table.rowsBefore + row + 1}") + spokenCells).joinToString(words.stop + " ") +
            words.stop
    }

    /** A language's words for tables; Hindi and Bengali end sentences with a danda. */
    @Suppress("LongParameterList") // One word list per language, each word a parameter.
    private class TableWords(
        val row: String,
        val stop: String,
        private val rowOne: String,
        private val rowMany: String,
        private val columnOne: String,
        private val columnMany: String,
        val continues: String,
        val nil: String,
        private val more: (String) -> String,
    ) {
        fun rows(count: Int): String = "$count ${if (count == 1) rowOne else rowMany}"

        fun columns(count: Int): String = "$count ${if (count == 1) columnOne else columnMany}"

        fun moreRows(count: Int): String = more(rows(count))
    }

    /** Dashes and minus signs only, maybe with a note mark ("–*", "–¹"): a cell ruled through instead of filled. */
    private val RULE_ONLY = Regex("""[\p{Pd}−]+[*†‡§¹²³⁴-⁹]*""")

    /** Dots only (".." for "not available", too short for a leader): left empty, so skipped with its header. */
    private val DOTS_ONLY = Regex("""[.…·]+""")

    private val WORDS = mapOf(
        Lang.EN to TableWords("Row", ".", "row", "rows", "column", "columns", "Table continues", "nil") { rows ->
            rows.replaceFirst(" ", " more ")
        },
        Lang.HI to TableWords("पंक्ति", "।", "पंक्ति", "पंक्तियाँ", "स्तंभ", "स्तंभ", "तालिका जारी है", "कुछ नहीं") {
            "और $it"
        },
        Lang.BN to TableWords("সারি", "।", "সারি", "সারি", "কলাম", "কলাম", "সারণি চলছে", "নেই") { "আরও $it" },
    )
}
