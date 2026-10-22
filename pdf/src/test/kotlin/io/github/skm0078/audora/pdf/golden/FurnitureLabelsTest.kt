// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/golden/FurnitureLabelsTest.kt
// Purpose: RI-7a's done-when: exactly the labelled captions and display equations of both corpora are tagged.

package io.github.skm0078.audora.pdf.golden

import io.github.skm0078.audora.core.model.UnitKind
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.document.PdfBook
import io.github.skm0078.audora.pdf.units.ReadingUnitBuilder
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * pdf/golden/furniture/labels.tsv lists every unit of the golden and listening corpora a careful reader calls a
 * caption or a display equation (pdf/golden/furniture/README.md). The pipeline must tag each of them, and nothing
 * else. Scanned documents are left out: they have no captions or equations, and need recorded OCR.
 */
class FurnitureLabelsTest {
    private data class Label(
        val corpus: String,
        val document: String,
        val page: Int,
        val kind: UnitKind,
        val text: String,
    )

    @Test
    fun `the labelled captions and equations are tagged and nothing else is`() {
        val root = File(System.getProperty(DIR_PROPERTY) ?: DEFAULT_DIR)
        val labels = root.resolve(LABELS).readLines(Charsets.UTF_8).drop(1).filter { it.isNotEmpty() }.map { row ->
            val f = row.split('\t', limit = COLUMNS)
            require(f.size == COLUMNS) { "$LABELS: malformed row $row" }
            Label(f[0], f[1], f[2].toInt(), UnitKind.valueOf(f[3]), f[4])
        }.sortedWith(ORDER)
        val tagged = CORPORA.flatMap { (name, expected) -> tagged(name, GoldenCorpus(root.resolve(name), expected)) }
            .sortedWith(ORDER)
        val missed = (labels - tagged.toSet()).joinToString("\n") { "missed: $it" }
        val extra = (tagged - labels.toSet()).joinToString("\n") { "not labelled: $it" }
        assertEquals("$missed\n$extra\n".trim() + "\n", labels, tagged)
    }

    private fun tagged(name: String, corpus: GoldenCorpus): List<Label> =
        corpus.documents.filterNot { it.category.startsWith(SCAN) }.flatMap { document ->
            val engine = GoldenPipeline.DumpEngine(corpus.pages(document))
            val book = (PdfBook.open(engine, logContext = document.id, ocr = null) as Outcome.Success).value
            val builder = ReadingUnitBuilder()
            book.use {
                (0 until book.pageCount).flatMap { page ->
                    (book.readingUnits(page, builder) as Outcome.Success).value
                        .filter { it.kind in FURNITURE }
                        .map { Label(name.ifEmpty { GOLDEN }, document.id, page, it.kind, it.text) }
                }
            }
        }

    private companion object {
        const val DIR_PROPERTY = "audora.golden.dir"
        const val DEFAULT_DIR = "golden"
        const val LABELS = "furniture/labels.tsv"
        const val COLUMNS = 5
        const val SCAN = "scan"
        const val GOLDEN = "golden"
        val CORPORA = listOf("" to "expected", "listening" to "spoken")
        val FURNITURE = setOf(UnitKind.CAPTION, UnitKind.EQUATION)
        val ORDER = compareBy<Label>({ it.corpus }, { it.document }, { it.page }, { it.kind }, { it.text })
    }
}
