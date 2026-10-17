// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/golden/TableLabelsTest.kt
// Purpose: RI-4a's done-when: the hand-labelled tables are found with their shape and 90% of their cells.

package io.github.skm0078.audora.pdf.golden

import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.document.PdfBook
import io.github.skm0078.audora.pdf.units.ReadingUnitBuilder
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** pdf/golden/tables/README.md: labels typed from the rendered pages, cells compared without whitespace. */
class TableLabelsTest {
    private data class Key(val corpus: String, val document: String, val page: Int, val table: Int)

    @Test
    fun `the labelled tables are found with their rows, columns and cells`() {
        val root = File(System.getProperty(DIR_PROPERTY) ?: DEFAULT_DIR)
        val labels = root.resolve(LABELS).readLines(Charsets.UTF_8).drop(1).filter { it.isNotEmpty() }
            .map { it.split('\t') }
            .groupBy({ f -> Key(f[0], f[1], f[2].toInt(), f[3].toInt()) }, { f -> f[4] to f.drop(FIRST_CELL) })
        val units = HashMap<Pair<String, String>, List<List<ReadingUnit>>>()
        val report = labels.map { (key, rows) ->
            val pages = units.getOrPut(key.corpus to key.document) { units(root, key.corpus, key.document) }
            key to score(rows, pages[key.page].filter { it.kind.isTable && it.table?.tableIndex == key.table })
        }
        val summary = report.joinToString("\n") { (key, score) -> "$key: $score" }
        assertTrue(summary, report.all { (_, score) -> score.shapeMatches && score.accuracy >= MIN_CELL_ACCURACY })
    }

    private data class Score(val shapeMatches: Boolean, val matched: Int, val cells: Int) {
        val accuracy: Double get() = if (cells == 0) 0.0 else matched.toDouble() / cells

        override fun toString() = "shape ${if (shapeMatches) "ok" else "WRONG"}, cells $matched/$cells"
    }

    /** The labelled [rows] (header first) against the table's units (summary first). */
    private fun score(rows: List<Pair<String, List<String>>>, table: List<ReadingUnit>): Score {
        val info = table.firstOrNull()?.table
        val dataRows = rows.size - 1
        val columns = rows.first().second.size
        val shape = info != null && info.row == null && info.rowCount == dataRows && info.columnCount == columns
        val read = table.map { unit -> unit.table?.cells.orEmpty().map { unit.text.substring(it.start, it.end) } }
        val matched = rows.withIndex().sumOf { (i, row) ->
            val got = read.getOrNull(i).orEmpty()
            row.second.withIndex().count { (c, cell) -> got.getOrNull(c)?.let(::squeeze) == squeeze(cell) }
        }
        return Score(shape, matched, rows.sumOf { it.second.size })
    }

    private fun squeeze(text: String) = text.filterNot(Char::isWhitespace)

    private fun units(root: File, corpus: String, document: String): List<List<ReadingUnit>> {
        val (dir, expected) = CORPORA.getValue(corpus)
        val goldenCorpus = GoldenCorpus(root.resolve(dir), expected)
        val engine = GoldenPipeline.DumpEngine(goldenCorpus.pages(goldenCorpus.documents.single { it.id == document }))
        val book = (PdfBook.open(engine, logContext = document, ocr = null) as Outcome.Success).value
        val builder = ReadingUnitBuilder()
        return book.use { (0 until book.pageCount).map { (book.readingUnits(it, builder) as Outcome.Success).value } }
    }

    private companion object {
        const val DIR_PROPERTY = "audora.golden.dir"
        const val DEFAULT_DIR = "golden"
        const val LABELS = "tables/labels.tsv"
        const val FIRST_CELL = 5
        const val MIN_CELL_ACCURACY = 0.9
        val CORPORA = mapOf("golden" to ("" to "expected"), "listening" to ("listening" to "spoken"))
    }
}
