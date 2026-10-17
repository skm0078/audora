// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/golden/listening/ReadingUnitTsv.kt
// Purpose: Writes reading units as the TSV that :playback's listening test reads (pdf/golden/listening/README.md).

package io.github.skm0078.audora.pdf.golden.listening

import io.github.skm0078.audora.core.model.ReadingUnit

/**
 * One unit per line, columns in [COLUMNS] order, empty for null. The format is a contract with
 * playback/src/test/.../listening/ListeningUnits.kt, which reads it back; change both together.
 */
object ReadingUnitTsv {
    val COLUMNS = listOf(
        "page", "index", "charStart", "charEnd", "kind", "lang", "pauseAfterMs", "speakScore", "noteRef",
        "tableIndex", "row", "rowCount", "columnCount", "headers", "cells", "text",
    )
    const val HEADER_SEPARATOR = '\u001F'

    fun write(units: List<ReadingUnit>): String = buildString {
        appendLine(COLUMNS.joinToString("\t"))
        units.forEach { appendLine(line(it)) }
    }

    private fun line(unit: ReadingUnit): String {
        val table = unit.table
        return listOf(
            unit.page.toString(),
            unit.index.toString(),
            unit.charStart.toString(),
            unit.charEnd.toString(),
            unit.kind.name,
            unit.lang.name,
            unit.pauseAfterMs.toString(),
            unit.speakScore?.toString().orEmpty(),
            unit.noteRef?.toString().orEmpty(),
            table?.tableIndex?.toString().orEmpty(),
            table?.row?.toString().orEmpty(),
            table?.rowCount?.toString().orEmpty(),
            table?.columnCount?.toString().orEmpty(),
            table?.headers?.joinToString(HEADER_SEPARATOR.toString()) { escape(it) }.orEmpty(),
            table?.cells?.joinToString(",") { "${it.start}-${it.end}" }.orEmpty(),
            escape(unit.text),
        ).joinToString("\t")
    }

    /** Backslash escapes for the characters that would break a line or a column. */
    fun escape(text: String): String = buildString(text.length) {
        text.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                '\t' -> append("\\t")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                HEADER_SEPARATOR -> append("\\u")
                else -> append(c)
            }
        }
    }
}
