// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/epub/CopyFixtures.kt
// Purpose: Test units with kinds, pauses, scores and table cells, for the copy builders.

package io.github.skm0078.audora.playback.epub

import io.github.skm0078.audora.core.model.CellSpan
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.TableRowInfo
import io.github.skm0078.audora.core.model.UnitKind

const val PARAGRAPH_END = 600

fun sentence(page: Int, index: Int, text: String, kind: UnitKind = UnitKind.BODY, pause: Int = 0): ReadingUnit =
    ReadingUnit(
        page = page,
        index = index,
        charStart = index * 100,
        charEnd = index * 100 + text.length,
        text = text,
        lang = Lang.EN,
        pauseAfterMs = pause,
        bounds = null,
        kind = kind,
    )

fun note(page: Int, index: Int, text: String, number: Int): ReadingUnit =
    sentence(page, index, text, UnitKind.FOOTNOTE).copy(noteRef = number)

/** A table unit whose cells are [cells] joined by two spaces; [row] null for the header (summary) unit. */
fun tableUnit(page: Int, index: Int, row: Int?, vararg cells: String): ReadingUnit {
    val text = cells.joinToString("  ")
    var start = 0
    val spans = cells.map { cell -> CellSpan(start, start + cell.length).also { start += cell.length + 2 } }
    val info = TableRowInfo(
        tableIndex = 0,
        row = row,
        rowCount = 2,
        columnCount = cells.size,
        headers = emptyList(),
        cells = spans,
    )
    val kind = if (row == null) UnitKind.TABLE_SUMMARY else UnitKind.TABLE_ROW
    return ReadingUnit(page, index, index * 100, index * 100 + text.length, text, Lang.EN, 0, null, kind, table = info)
}
