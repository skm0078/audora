// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/normalize/TableSpeech.kt
// Purpose: The words a table is read with: its size, and each row as "Row 3. Name: Ram. Age: 34." (RI-4b).

package io.github.skm0078.audora.speech.normalize

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.TableRowInfo

/**
 * Written text, not final speech: playback hands it to the voice like any sentence, so the normalizers still read its
 * numbers, dates and units ("34" → "thirty-four"). Nothing here is added to `ReadingUnit.text` (ADR-018).
 *
 * A row pairs every non-empty cell with its column's header ("Age: 34"); a table without a header row reads its
 * cells in order, separated by commas. Rows are numbered from 1, as a person counts them.
 */
object TableSpeech {
    /** "4 rows, 5 columns." counting data rows only (the header row is not a row the listener counts). */
    fun summary(table: TableRowInfo, lang: Lang): String {
        val words = WORDS.getValue(lang)
        return "${words.rows(table.rowCount)}, ${words.columns(table.columnCount)}${words.stop}"
    }

    /** One data row of [table] whose cells are spans of [text]; [summary] for the header unit (row null). */
    fun row(table: TableRowInfo, text: String, lang: Lang): String {
        val row = table.row ?: return summary(table, lang)
        val words = WORDS.getValue(lang)
        val cells = table.cells.map { span -> text.substring(span.start, span.end).trim() }
        val spokenCells = if (table.headers.isEmpty()) {
            listOfNotNull(cells.filter { it.isNotEmpty() }.joinToString(", ").ifEmpty { null })
        } else {
            cells.mapIndexedNotNull { column, cell ->
                val header = table.headers.getOrNull(column)?.trim()?.trimEnd(':', '：')?.trim().orEmpty()
                when {
                    cell.isEmpty() -> null
                    header.isEmpty() -> cell
                    else -> "$header: $cell"
                }
            }
        }
        return (listOf("${words.row} ${row + 1}") + spokenCells).joinToString(words.stop + " ") + words.stop
    }

    /** A language's words for tables; Hindi and Bengali end sentences with a danda. */
    private class TableWords(
        val row: String,
        val stop: String,
        private val rowOne: String,
        private val rowMany: String,
        private val columnOne: String,
        private val columnMany: String,
    ) {
        fun rows(count: Int): String = "$count ${if (count == 1) rowOne else rowMany}"

        fun columns(count: Int): String = "$count ${if (count == 1) columnOne else columnMany}"
    }

    private val WORDS = mapOf(
        Lang.EN to TableWords("Row", ".", "row", "rows", "column", "columns"),
        Lang.HI to TableWords("पंक्ति", "।", "पंक्ति", "पंक्तियाँ", "स्तंभ", "स्तंभ"),
        Lang.BN to TableWords("সারি", "।", "সারি", "সারি", "কলাম", "কলাম"),
    )
}
