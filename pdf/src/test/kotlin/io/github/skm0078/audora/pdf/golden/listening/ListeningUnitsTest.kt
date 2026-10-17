// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/golden/listening/ListeningUnitsTest.kt
// Purpose: Runs the :pdf pipeline over the listening corpus and writes its units for :playback's listening score.

package io.github.skm0078.audora.pdf.golden.listening

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.document.PdfBook
import io.github.skm0078.audora.pdf.golden.GoldenCorpus
import io.github.skm0078.audora.pdf.golden.GoldenDocument
import io.github.skm0078.audora.pdf.golden.GoldenPipeline
import io.github.skm0078.audora.pdf.units.ReadingUnitBuilder
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The first half of the listening score (RI-8a, ADR-035): `:pdf` cannot depend on `:speech` or `:playback`, so this
 * test only writes each document's units to `<golden out>/listening/<id>.tsv` ([ReadingUnitTsv]), and
 * `:playback`'s ListeningCorpusTest, which runs after this task, turns them into what the voice is given and scores
 * that. Scanned documents are read through [RecordedOcr].
 */
class ListeningUnitsTest {
    @Test
    fun `every listening document gives units`() {
        val root = File(System.getProperty(DIR_PROPERTY) ?: DEFAULT_DIR).resolve(LISTENING)
        val corpus = GoldenCorpus(root, expectedDir = SPOKEN)
        val out = File(System.getProperty(OUT_PROPERTY) ?: DEFAULT_OUT).resolve(LISTENING).apply { mkdirs() }

        corpus.documents.forEach { document ->
            val units = units(document, corpus, root)
            out.resolve("${document.id}.tsv").writeText(ReadingUnitTsv.write(units), Charsets.UTF_8)
            assertTrue("${document.id} gave no units", units.isNotEmpty())
        }
    }

    private fun units(document: GoldenDocument, corpus: GoldenCorpus, root: File): List<ReadingUnit> {
        val ocr = RecordedOcr(root.resolve(OCR), document.id, langsOf(document))
        val engine = GoldenPipeline.DumpEngine(corpus.pages(document))
        val book = when (val opened = PdfBook.open(engine, logContext = "listening=${document.id}", ocr = { ocr })) {
            is Outcome.Success -> opened.value
            is Outcome.Failure -> error("${document.id} did not open: ${opened.error}")
        }
        val builder = ReadingUnitBuilder()
        return book.use {
            (0 until book.pageCount).flatMap { page ->
                when (val units = book.readingUnits(page, builder)) {
                    is Outcome.Success -> units.value
                    is Outcome.Failure -> error("${document.id} page $page failed: ${units.error}")
                }
            }
        }
    }

    /** OCR languages from the id's language prefix, as scripts/pdf/golden_corpus.py records them. */
    private fun langsOf(document: GoldenDocument): Set<Lang> = when (document.id.substringBefore('-')) {
        "hi" -> setOf(Lang.HI, Lang.EN)
        "bn" -> setOf(Lang.BN, Lang.EN)
        else -> setOf(Lang.EN)
    }

    private companion object {
        const val DIR_PROPERTY = "audora.golden.dir"
        const val OUT_PROPERTY = "audora.golden.out"
        const val LISTENING = "listening"
        const val SPOKEN = "spoken"
        const val OCR = "ocr"
        const val DEFAULT_DIR = "golden"
        const val DEFAULT_OUT = "build/golden/ide"
    }
}
