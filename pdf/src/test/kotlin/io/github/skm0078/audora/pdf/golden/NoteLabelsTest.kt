// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/golden/NoteLabelsTest.kt
// Purpose: RI-5a's done-when on the corpora: exactly the labelled note markers fall silent and footnotes are tagged.

package io.github.skm0078.audora.pdf.golden

import io.github.skm0078.audora.core.config.LayoutConfig
import io.github.skm0078.audora.core.model.UnitKind
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.document.PdfBook
import io.github.skm0078.audora.pdf.layout.NoteFinder
import io.github.skm0078.audora.pdf.units.ReadingUnitBuilder
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * pdf/golden/notes/labels.tsv lists every note marker and every footnote unit of the golden and listening corpora
 * (pdf/golden/notes/README.md). The pipeline must silence each marker and tag each footnote, and nothing else.
 * Scanned documents are left out: OCR'd pages carry no font sizes to tell a marker by.
 */
class NoteLabelsTest {
    private data class Label(
        val corpus: String,
        val document: String,
        val page: Int,
        val kind: String,
        val ref: String,
        val text: String,
    )

    @Test
    fun `the labelled markers fall silent and the labelled footnotes are tagged, and nothing else`() {
        val root = File(System.getProperty(DIR_PROPERTY) ?: DEFAULT_DIR)
        val labels = root.resolve(LABELS).readLines(Charsets.UTF_8).drop(1).filter { it.isNotEmpty() }.map { row ->
            val f = row.split('\t', limit = COLUMNS)
            require(f.size == COLUMNS) { "$LABELS: malformed row $row" }
            Label(f[0], f[1], f[2].toInt(), f[3], f[4], f[5])
        }.sortedWith(ORDER)
        val found = CORPORA.flatMap { (name, expected) -> found(name, GoldenCorpus(root.resolve(name), expected)) }
            .sortedWith(ORDER)
        val missed = (labels - found.toSet()).joinToString("\n") { "missed: $it" }
        val extra = (found - labels.toSet()).joinToString("\n") { "not labelled: $it" }
        assertEquals("$missed\n$extra\n".trim() + "\n", labels, found)
    }

    private fun found(name: String, corpus: GoldenCorpus): List<Label> =
        corpus.documents.filterNot { it.category.startsWith(SCAN) }.flatMap { document ->
            val engine = GoldenPipeline.DumpEngine(corpus.pages(document))
            val book = (PdfBook.open(engine, logContext = document.id, ocr = null) as Outcome.Success).value
            val builder = ReadingUnitBuilder()
            val notes = NoteFinder(LayoutConfig())
            val corpusName = name.ifEmpty { GOLDEN }
            book.use {
                (0 until book.pageCount).flatMap { page ->
                    val text = (book.pageText(page) as Outcome.Success).value
                    val markers = notes.tag(text, builder.analyze(text).layout).noteMarkers.map { span ->
                        val before = text.text.substring(maxOf(0, span.start - CONTEXT), span.start)
                        val marker = text.text.substring(span.start, span.end)
                        Label(corpusName, document.id, page, MARKER, marker, before.replace(WHITESPACE, " ").trim())
                    }
                    val footnotes = (book.readingUnits(page, builder) as Outcome.Success).value
                        .filter { it.kind == UnitKind.FOOTNOTE }
                        .map {
                            Label(corpusName, document.id, page, FOOTNOTE, it.noteRef?.toString().orEmpty(), it.text)
                        }
                    markers + footnotes
                }
            }
        }

    private companion object {
        const val DIR_PROPERTY = "audora.golden.dir"
        const val DEFAULT_DIR = "golden"
        const val LABELS = "notes/labels.tsv"
        const val COLUMNS = 6
        const val SCAN = "scan"
        const val GOLDEN = "golden"
        const val MARKER = "MARKER"
        const val FOOTNOTE = "FOOTNOTE"

        /** Characters of the page text before a marker that identify it in a label. */
        const val CONTEXT = 20
        val WHITESPACE = Regex("\\s+")
        val CORPORA = listOf("" to "expected", "listening" to "spoken")
        val ORDER = compareBy<Label>({ it.corpus }, { it.document }, { it.page }, { it.kind }, { it.text }, { it.ref })
    }
}
