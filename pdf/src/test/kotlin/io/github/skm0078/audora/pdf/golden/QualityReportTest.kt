// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/golden/QualityReportTest.kt
// Purpose: The scores file round trip, the baseline gate, and a deterministic QUALITY.md.

package io.github.skm0078.audora.pdf.golden

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityReportTest {
    private fun result(id: String, similarity: Double, category: String = "en-book") = GoldenResult(
        document = GoldenDocument(id, category, "src/$id.pdf", "all", id, ExpectedStatus.EXACT),
        pages = 3,
        score = TextScore(similarity, 0.9, 0.8),
    )

    @Test
    fun `scores file round trips at four decimals`() {
        val tsv = QualityReport.scoresTsv(listOf(result("a", 0.912345), result("b", 0.5)))
        assertEquals(mapOf("a" to 0.9123, "b" to 0.5), QualityReport.parseScores(tsv))
        assertTrue(tsv.lines()[1].startsWith("document\tcategory\tstatus\tpages\tsimilarity"))
    }

    @Test
    fun `empty baseline skips the gate`() {
        assertEquals(emptyMap<String, Double>(), QualityReport.parseScores(""))
        val comparison = QualityReport.compare(emptyMap(), listOf(result("a", 0.1)))
        assertTrue(comparison.passed)
        assertEquals(listOf("a"), comparison.added)
    }

    @Test
    fun `a lower corpus score fails, an equal or higher one passes`() {
        val baseline = mapOf("a" to 0.9, "b" to 0.8)
        assertFalse(QualityReport.compare(baseline, listOf(result("a", 0.9), result("b", 0.79))).passed)
        assertTrue(QualityReport.compare(baseline, listOf(result("a", 0.9), result("b", 0.8))).passed)
        // One document may trade down if the corpus as a whole gains.
        val traded = QualityReport.compare(baseline, listOf(result("a", 0.95), result("b", 0.78)))
        assertTrue(traded.passed)
        assertEquals(listOf("a", "b"), traded.changes.map { it.first })
    }

    @Test
    fun `floating-point noise below the written precision never fails the gate`() {
        val baseline = mapOf("a" to 0.9123)
        assertTrue(QualityReport.compare(baseline, listOf(result("a", 0.912_349_9))).passed)
    }

    @Test
    fun `added and removed documents are reported but not gated`() {
        val baseline = mapOf("a" to 0.9, "gone" to 0.1)
        val comparison = QualityReport.compare(baseline, listOf(result("a", 0.9), result("new", 0.05)))
        assertTrue(comparison.passed)
        assertEquals(1, comparison.shared)
        assertEquals(listOf("new"), comparison.added)
        assertEquals(listOf("gone"), comparison.removed)
    }

    @Test
    fun `quality report is deterministic and shows the corpus score`() {
        val results = listOf(result("a", 0.9, "en-2col"), result("b", 0.7, "en-2col"), result("c", 1.0))
        val first = QualityReport.markdown(results, pipelineVersion = 1)
        assertEquals(first, QualityReport.markdown(results, pipelineVersion = 1))
        assertTrue(first.contains("**Corpus score: 0.8667**"))
        assertTrue(first.contains("| en-2col | 2 | 0.8000 |"))
        assertTrue(first.contains("| c | en-book | 3 | exact | 1.0000 | 0.9000 | 0.8000 |"))
    }
}
