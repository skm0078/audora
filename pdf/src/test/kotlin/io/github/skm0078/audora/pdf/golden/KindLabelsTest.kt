// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/golden/KindLabelsTest.kt
// Purpose: RI-10 on the golden corpus: each labelled excerpt is classified as pdf/golden/kinds.tsv says.

package io.github.skm0078.audora.pdf.golden

import io.github.skm0078.audora.core.config.SpeakabilityConfig
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.document.DocumentKind
import io.github.skm0078.audora.pdf.document.DocumentKindClassifier
import io.github.skm0078.audora.pdf.document.PageSignals
import io.github.skm0078.audora.pdf.document.PageSource
import io.github.skm0078.audora.pdf.document.PdfBook
import io.github.skm0078.audora.pdf.units.ReadingUnitBuilder
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * pdf/golden/kinds.tsv labels every document of the test-pdfs corpus with its kind and page count
 * (pdf/golden/README.md). The full measure (57 documents, a dozen pages each, scans OCR'd) runs locally; CI has
 * the golden corpus's excerpts and no OCR, so this checks the excerpts that can be told without it: their pages
 * classified with the book's real page count. The excerpts that cannot are listed in [UNTOLD] with the reason.
 */
class KindLabelsTest {
    @Test
    fun `each golden excerpt is classified as labelled`() {
        val root = File(System.getProperty(DIR_PROPERTY) ?: DEFAULT_DIR)
        val labels = root.resolve(LABELS).readLines(Charsets.UTF_8).drop(1).filter {
            it.isNotEmpty()
        }.associate { row ->
            val f = row.split('\t')
            require(f.size == COLUMNS) { "$LABELS: malformed row $row" }
            f[0] to (f[1].toInt() to DocumentKind.valueOf(f[2]))
        }
        val corpus = GoldenCorpus(root, EXPECTED)
        val checked = corpus.documents.filter { it.id !in UNTOLD }.associateWith { labels[labelKey(it)] }
        val unlabelled = checked.filterValues { it == null }.keys.map { it.id }
        assertEquals("golden documents missing from $LABELS", emptyList<String>(), unlabelled)
        val expected = checked.entries.associate { (document, label) -> document.id to label!!.second }
        val found = checked.entries.associate { (document, label) ->
            document.id to classify(corpus, document, label!!.first)
        }
        assertEquals(expected, found)
    }

    /** The test-pdfs file an excerpt was cut from; a document generated in the repo goes by its own id. */
    private fun labelKey(document: GoldenDocument): String = if (document.source.startsWith(
            REPO_SOURCE,
        )
    ) {
        document.id
    } else {
        document.source.substringAfterLast('/').removeSuffix(".pdf")
    }

    private fun classify(corpus: GoldenCorpus, document: GoldenDocument, pageCount: Int): DocumentKind {
        val engine = GoldenPipeline.DumpEngine(corpus.pages(document))
        val book = (PdfBook.open(engine, logContext = document.id, ocr = null) as Outcome.Success).value
        val builder = ReadingUnitBuilder()
        val readable = SpeakabilityConfig().skipBelow
        return book.use {
            val pages = (0 until book.pageCount).map { page ->
                val reading = (book.readPage(page, builder) as Outcome.Success).value
                val raw = if (reading.source ==
                    PageSource.TextLayer
                ) {
                    (book.pageText(page) as Outcome.Success).value.text
                } else {
                    null
                }
                PageSignals.of(reading.units, raw, readable)
            }
            DocumentKindClassifier().classify(pages, pageCount)
        }
    }

    private companion object {
        const val DIR_PROPERTY = "audora.golden.dir"
        const val DEFAULT_DIR = "golden"
        const val LABELS = "kinds.tsv"
        const val EXPECTED = "expected"
        const val COLUMNS = 3
        const val REPO_SOURCE = "repo:"

        /** Excerpts CI cannot classify, and why. */
        val UNTOLD = mapOf(
            "bn-us-hud-fairlending-guide-pd" to "its text layer has a broken font map: the app OCRs it, CI has no OCR",
        )
    }
}
