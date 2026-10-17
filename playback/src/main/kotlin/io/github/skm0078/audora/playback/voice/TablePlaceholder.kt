// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/voice/TablePlaceholder.kt
// Purpose: Stand-in table reading until RI-4b's Tables setting: rows are skipped, a table is announced by its size.

package io.github.skm0078.audora.playback.voice

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.UnitKind

/**
 * PLACEHOLDER that RI-4b replaces with the "Tables" setting (Summary / Row by row / Skip) and
 * `speech/normalize/TableSpeech.kt`. `:pdf` keeps tables as units since RI-4a (ADR-035); before that their lines were
 * dropped, so skipping the rows keeps playback as it was, and the summary unit (the header row) is read as the
 * table's size: "4 rows, 5 columns."
 */
// TODO(RI-4b): replace with the Tables setting and TableSpeech; keep the playlist and renderer call sites.
object TablePlaceholder {
    /** The units playback reads: all but table rows. */
    fun spokenUnits(units: List<ReadingUnit>): List<ReadingUnit> = units.filterNot { it.kind == UnitKind.TABLE_ROW }

    /** What the voice is given for [unit]: its text, or a table's size for the table's summary unit. */
    fun spokenText(unit: ReadingUnit): String {
        val table = unit.table
        if (unit.kind != UnitKind.TABLE_SUMMARY || table == null) return unit.text
        val rows = table.rowCount
        val columns = table.columnCount
        return when (unit.lang) {
            Lang.EN -> "${english(rows, "row")}, ${english(columns, "column")}."
            Lang.HI -> "$rows ${if (rows == 1) "पंक्ति" else "पंक्तियाँ"}, $columns स्तंभ।"
            Lang.BN -> "$rows সারি, $columns কলাম।"
        }
    }

    private fun english(count: Int, noun: String): String = if (count == 1) "1 $noun" else "$count ${noun}s"
}
