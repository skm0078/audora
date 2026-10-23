// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/quality/SpeakabilityLabelsTest.kt
// Purpose: RI-2a's done-when: the default gate on the labelled lines in pdf/golden/speakability/, and its speed.

package io.github.skm0078.audora.pdf.quality

import io.github.skm0078.audora.core.config.SpeakabilityConfig
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every labelled line (pdf/golden/speakability/README.md) is scored by the default [SpeakabilityScorer] with the
 * bundled word lists, with the OCR confidence the pipeline gave it. The gate must catch at least [MIN_CAUGHT] of the
 * junk lines and wrongly flag at most [MAX_FALSE_FLAGS] of the real ones, at under [MAX_MS_PER_LINE] ms a line.
 */
class SpeakabilityLabelsTest {
    private data class Line(val source: String, val ocrConfidence: Float?, val junk: Boolean, val text: String)

    private val lines: List<Line> by lazy {
        val file = File(System.getProperty(DIR_PROPERTY) ?: DEFAULT_DIR).resolve(LABELS)
        val rows = file.readLines(Charsets.UTF_8)
        require(rows.first().split('\t') == COLUMNS) { "$file: header must be $COLUMNS" }
        rows.drop(1).filter { it.isNotEmpty() }.mapIndexed { i, row ->
            val f = row.split('\t', limit = COLUMNS.size)
            require(f.size == COLUMNS.size && f[LABEL] in LABELS_ALLOWED) { "$file line ${i + 2} is malformed" }
            Line(f[SOURCE], f[OCR].takeIf { it.isNotEmpty() }?.toFloat(), f[LABEL] == JUNK, f[TEXT])
        }
    }
    private val scorer = SpeakabilityScorer()
    private val skipBelow = SpeakabilityConfig().skipBelow

    @Test
    fun `the default gate catches junk and spares real sentences`() {
        val flagged = lines.filter { scorer.score(it.text, it.ocrConfidence) < skipBelow }
        val junk = lines.count { it.junk }
        val real = lines.size - junk
        val caught = flagged.count { it.junk }.toFloat() / junk
        val falseFlags = flagged.count { !it.junk }.toFloat() / real
        val report = "caught %.3f of %d junk lines, flagged %.3f of %d real".format(caught, junk, falseFlags, real)
        println("Speakability: $report")
        assertTrue("$report; need >= $MIN_CAUGHT caught", caught >= MIN_CAUGHT)
        val wrong = flagged.filterNot { it.junk }.take(SHOWN)
            .joinToString { "${it.source}: ${it.text.take(SHOWN_CHARS)}" }
        assertTrue("$report; need <= $MAX_FALSE_FLAGS flagged. First: $wrong", falseFlags <= MAX_FALSE_FLAGS)
    }

    @Test
    fun `scoring a line takes well under the per-sentence budget`() {
        lines.forEach { scorer.score(it.text, it.ocrConfidence) } // loads the word lists and warms up
        val start = System.nanoTime()
        repeat(ROUNDS) { lines.forEach { scorer.score(it.text, it.ocrConfidence) } }
        val msPerLine = (System.nanoTime() - start) / NANOS_PER_MS / (ROUNDS * lines.size)
        assertTrue("%.4f ms a line".format(msPerLine), msPerLine < MAX_MS_PER_LINE)
    }

    private companion object {
        const val DIR_PROPERTY = "audora.golden.dir"
        const val DEFAULT_DIR = "golden"
        const val LABELS = "speakability/labels.tsv"
        val COLUMNS = listOf("source", "route", "ocr", "label", "text")
        const val SOURCE = 0
        const val OCR = 2
        const val LABEL = 3
        const val TEXT = 4
        const val JUNK = "junk"
        val LABELS_ALLOWED = setOf(JUNK, "real")
        const val MIN_CAUGHT = 0.9f
        const val MAX_FALSE_FLAGS = 0.02f
        const val MAX_MS_PER_LINE = 5.0
        const val ROUNDS = 3
        const val NANOS_PER_MS = 1e6
        const val SHOWN = 5
        const val SHOWN_CHARS = 60
    }
}
