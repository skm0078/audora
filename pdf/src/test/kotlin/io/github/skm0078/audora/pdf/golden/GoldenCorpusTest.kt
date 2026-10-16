// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/golden/GoldenCorpusTest.kt
// Purpose: Scores the :pdf pipeline on the golden corpus, writes the report, and fails if the score dropped.

package io.github.skm0078.audora.pdf.golden

import io.github.skm0078.audora.pdf.PIPELINE_VERSION
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs with every `./gradlew test`. pdf/build.gradle.kts passes the corpus folder, the baseline scores (the committed
 * pdf/golden/scores.tsv, or the base branch's copy in CI) and an output folder. `./gradlew :pdf:goldenTest` copies
 * that output into docs/QUALITY.md and pdf/golden/scores.tsv. The spoken text of every document is written next to
 * the report (spoken/<id>.txt), so a score change can be read as a diff.
 */
class GoldenCorpusTest {
    @Test
    fun `golden corpus score is not lower than the baseline`() {
        val root = File(System.getProperty(DIR_PROPERTY) ?: DEFAULT_DIR)
        val corpus = GoldenCorpus(root)
        val out = File(System.getProperty(OUT_PROPERTY) ?: DEFAULT_OUT)
        val spoken = out.resolve("spoken").apply { mkdirs() }

        val results = corpus.documents.map { document ->
            val pages = corpus.pages(document)
            val text = GoldenPipeline.spokenText(pages)
            spoken.resolve("${document.id}.txt").writeText(text + "\n", Charsets.UTF_8)
            GoldenResult(document, pages.size, TextSimilarity.score(corpus.expectedText(document), text))
        }
        out.resolve("QUALITY.md").writeText(QualityReport.markdown(results, PIPELINE_VERSION), Charsets.UTF_8)
        out.resolve("scores.tsv").writeText(QualityReport.scoresTsv(results), Charsets.UTF_8)

        val baselineFile = System.getProperty(BASELINE_PROPERTY)?.let(::File) ?: root.resolve("scores.tsv")
        val baseline = if (baselineFile.isFile) QualityReport.parseScores(baselineFile.readText()) else emptyMap()
        val comparison = QualityReport.compare(baseline, results)
        val summary = comparison.render()
        out.resolve("changes.txt").writeText(summary, Charsets.UTF_8)
        println("Golden corpus (baseline $baselineFile):\n$summary")

        assertTrue(
            "The golden corpus score dropped (Build Spec §5). Spoken text: ${spoken.path}\n$summary",
            comparison.passed,
        )
    }

    private companion object {
        const val DIR_PROPERTY = "audora.golden.dir"
        const val OUT_PROPERTY = "audora.golden.out"
        const val BASELINE_PROPERTY = "audora.golden.baseline"

        // Gradle runs unit tests in the module folder; these let the test run from an IDE too.
        const val DEFAULT_DIR = "golden"
        const val DEFAULT_OUT = "build/golden/ide"
    }
}
