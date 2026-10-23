// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/golden/IntentLabelsTest.kt
// Purpose: ADR-056 on the golden corpus: each excerpt's pages, read in order, tell the intent a listener would expect.

package io.github.skm0078.audora.pdf.golden

import io.github.skm0078.audora.core.config.SpeakabilityConfig
import io.github.skm0078.audora.core.model.ContentIntent
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.document.IntentClassifier
import io.github.skm0078.audora.pdf.document.IntentSignals
import io.github.skm0078.audora.pdf.document.PdfBook
import io.github.skm0078.audora.pdf.units.ReadingUnitBuilder
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The excerpts are read the way playback feeds `BookIntent`: every page's units in order, then the decision list. A
 * charter (UDHR) may read as a report or plain; a novel must read as a story, scripture as scripture, a paper or a
 * textbook as study. CI has no OCR, so scanned excerpts are not listed.
 */
class IntentLabelsTest {
    @Test
    fun `each golden excerpt is told the intent a listener expects`() {
        val root = File(System.getProperty(DIR_PROPERTY) ?: DEFAULT_DIR)
        val corpus = GoldenCorpus(root, EXPECTED_DIR)
        val documents = corpus.documents.associateBy { it.id }
        val found = EXPECTED.keys.associateWith { id ->
            val document = requireNotNull(documents[id]) { "golden document $id is gone" }
            intentOf(corpus, document)
        }
        val wrong = found.filter { (id, intent) -> intent !in EXPECTED.getValue(id) }
        assertEquals("excerpts told another intent", emptyMap<String, ContentIntent?>(), wrong)
    }

    private fun intentOf(corpus: GoldenCorpus, document: GoldenDocument): ContentIntent? {
        val engine = GoldenPipeline.DumpEngine(corpus.pages(document))
        val book = (PdfBook.open(engine, logContext = document.id, ocr = null) as Outcome.Success).value
        val readable = SpeakabilityConfig().skipBelow
        return book.use {
            val signals = (0 until book.pageCount).fold(IntentSignals()) { sum, page ->
                val reading = (book.readPage(page, ReadingUnitBuilder()) as Outcome.Success).value
                sum + IntentSignals.of(reading.units, readable)
            }
            IntentClassifier().classify(signals)
        }
    }

    private companion object {
        const val DIR_PROPERTY = "audora.golden.dir"
        const val DEFAULT_DIR = "golden"
        const val EXPECTED_DIR = "expected"
        val PLAIN_OR_REPORT = setOf<ContentIntent?>(ContentIntent.GENERAL, ContentIntent.REPORT)
        val EXPECTED: Map<String, Set<ContentIntent?>> = mapOf(
            "en-mobydick-1col-long" to setOf(ContentIntent.STORY),
            "en-novel-moby-dick-gitenberg" to setOf(ContentIntent.STORY),
            "en-kjv-2col-long" to setOf(ContentIntent.SCRIPTURE),
            "en-textbook-forallx-yyc-logic" to setOf(ContentIntent.STUDY),
            "en-2col-physimaging-radonc-ccby" to setOf(ContentIntent.STUDY),
            "en-math-jmathbiol-integrodifference-ccby" to setOf(ContentIntent.STUDY),
            "en-udhr-noto" to PLAIN_OR_REPORT,
            "hi-udhr-noto" to PLAIN_OR_REPORT,
            "bn-udhr-noto" to PLAIN_OR_REPORT,
            "hi-us-hud-factsheet-pd" to setOf(ContentIntent.STUDY, ContentIntent.GENERAL),
            // Two pages of a leaflet, much of it below the readable score: may be too little to tell (null).
            "bn-us-fda-factsheet-vrinda-pd" to setOf(ContentIntent.STUDY, ContentIntent.GENERAL, null),
        )
    }
}
