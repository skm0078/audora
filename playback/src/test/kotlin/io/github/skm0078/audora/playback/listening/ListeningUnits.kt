// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/listening/ListeningUnits.kt
// Purpose: Reads the reading units :pdf's ListeningUnitsTest wrote for each listening document (TSV, one per line).

package io.github.skm0078.audora.playback.listening

import io.github.skm0078.audora.core.model.CellSpan
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.TableRowInfo
import io.github.skm0078.audora.core.model.UnitKind

/**
 * The reading half of the contract with pdf/src/test/.../golden/listening/ReadingUnitTsv.kt (pdf/golden/listening/
 * README.md); change both together. Line boxes are not carried: nothing heard depends on them.
 */
object ListeningUnits {
    private val COLUMNS = listOf(
        "page", "index", "charStart", "charEnd", "kind", "lang", "pauseAfterMs", "speakScore", "noteRef",
        "tableIndex", "row", "rowCount", "columnCount", "headers", "cells", "text",
    )
    private const val HEADER_SEPARATOR = '\u001F'

    fun parse(tsv: String): List<ReadingUnit> {
        val lines = tsv.lines().filter { it.isNotEmpty() }
        require(lines.firstOrNull()?.split('\t') == COLUMNS) { "units TSV header must be $COLUMNS" }
        return lines.drop(1).mapIndexed { i, line ->
            val f = line.split('\t')
            require(f.size == COLUMNS.size) {
                "units TSV line ${i + 2}: expected ${COLUMNS.size} columns, got ${f.size}"
            }
            Row(f).unit()
        }
    }

    /** One line's fields by column name; empty fields are null. */
    private class Row(private val fields: List<String>) {
        fun optional(name: String): String? = fields[COLUMNS.indexOf(name)].ifEmpty { null }

        fun required(name: String): String = requireNotNull(optional(name)) { "units TSV: $name is empty" }

        fun int(name: String): Int = required(name).toInt()
    }

    private fun Row.unit(): ReadingUnit {
        val table = optional("rowCount")?.let { rowCount ->
            TableRowInfo(
                tableIndex = int("tableIndex"),
                row = optional("row")?.toInt(),
                rowCount = rowCount.toInt(),
                columnCount = int("columnCount"),
                headers = optional("headers")?.split(HEADER_SEPARATOR)?.map(::unescape).orEmpty(),
                cells = optional("cells")?.split(',')?.map { span ->
                    val (start, end) = span.split('-').map(String::toInt)
                    CellSpan(start, end)
                }.orEmpty(),
            )
        }
        return ReadingUnit(
            page = int("page"),
            index = int("index"),
            charStart = int("charStart"),
            charEnd = int("charEnd"),
            text = unescape(optional("text").orEmpty()),
            lang = Lang.valueOf(required("lang")),
            pauseAfterMs = int("pauseAfterMs"),
            bounds = null,
            kind = UnitKind.valueOf(required("kind")),
            speakScore = optional("speakScore")?.toFloat(),
            table = table,
            noteRef = optional("noteRef")?.toInt(),
        )
    }

    fun unescape(text: String): String = buildString(text.length) {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && i + 1 < text.length) {
                append(
                    when (val next = text[i + 1]) {
                        't' -> '\t'
                        'n' -> '\n'
                        'r' -> '\r'
                        'u' -> HEADER_SEPARATOR
                        else -> next
                    },
                )
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }
}
