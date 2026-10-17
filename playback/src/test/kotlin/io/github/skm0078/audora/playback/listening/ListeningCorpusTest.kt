// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/listening/ListeningCorpusTest.kt
// Purpose: Scores what the voice is given for the listening corpus, writes the report, fails if the score dropped.

package io.github.skm0078.audora.playback.listening

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The second half of the listening score (RI-8a, ADR-035). `:pdf`'s ListeningUnitsTest writes each document's units
 * (playback/build.gradle.kts runs that task first and passes its folder); this test turns them into what the voice is
 * given ([HeardText]) and scores that against the document's script in pdf/golden/listening/spoken/. The heard text
 * of every document is written next to the report (heard/<id>.txt), so a score change can be read as a diff.
 * `./gradlew :playback:listeningTest` copies the report into docs/LISTENING.md and pdf/golden/listening/scores.tsv.
 */
class ListeningCorpusTest {
    @Test
    fun `listening score is not lower than the baseline`() {
        val root = File(System.getProperty(DIR_PROPERTY) ?: DEFAULT_DIR)
        val units = File(System.getProperty(UNITS_PROPERTY) ?: DEFAULT_UNITS)
        val out = File(System.getProperty(OUT_PROPERTY) ?: DEFAULT_OUT)
        val heardDir = out.resolve("heard").apply { mkdirs() }

        val results = ListeningDocument.readIndex(root).map { document ->
            val tsv = units.resolve("${document.id}.tsv")
            require(tsv.isFile) { "missing $tsv: run :pdf's unit tests first (playback/build.gradle.kts wires it)" }
            val parsed = ListeningUnits.parse(tsv.readText(Charsets.UTF_8))
            val heard = HeardText().of(parsed).joinToString("\n")
            heardDir.resolve("${document.id}.txt").writeText(heard + "\n", Charsets.UTF_8)
            val script = root.resolve("spoken/${document.scriptId}.txt").readText(Charsets.UTF_8)
            ListeningResult(document, parsed.size, ListeningScore.of(script, heard))
        }
        out.resolve("LISTENING.md").writeText(ListeningReport.markdown(results), Charsets.UTF_8)
        out.resolve("scores.tsv").writeText(ListeningReport.scoresTsv(results), Charsets.UTF_8)

        val baselineFile = System.getProperty(BASELINE_PROPERTY)?.let(::File) ?: root.resolve("scores.tsv")
        val baseline = if (baselineFile.isFile) ListeningReport.parseScores(baselineFile.readText()) else emptyMap()
        val (passed, summary) = ListeningReport.compare(baseline, results)
        println("Listening corpus (baseline $baselineFile):\n$summary")
        assertTrue("The listening score dropped (ADR-035). Heard text: ${heardDir.path}\n$summary", passed)
    }

    private companion object {
        const val DIR_PROPERTY = "audora.listening.dir"
        const val UNITS_PROPERTY = "audora.listening.units"
        const val OUT_PROPERTY = "audora.listening.out"
        const val BASELINE_PROPERTY = "audora.listening.baseline"

        // Gradle runs unit tests in the module folder; these let the test run from an IDE after :pdf's tests.
        const val DEFAULT_DIR = "../pdf/golden/listening"
        const val DEFAULT_UNITS = "../pdf/build/golden/testDebugUnitTest/listening"
        const val DEFAULT_OUT = "build/listening/ide"
    }
}
