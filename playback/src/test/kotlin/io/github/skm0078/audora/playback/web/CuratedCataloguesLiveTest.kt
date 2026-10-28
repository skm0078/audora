// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/web/CuratedCataloguesLiveTest.kt
// Purpose: The weekly live check of every curated catalogue (.github/workflows/catalogue-check.yml). Skipped in the
//          ordinary test run: it needs the network, which a unit test must never.

package io.github.skm0078.audora.playback.web

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CuratedCataloguesLiveTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `every curated catalogue opens, leads to a book ListenLock reads, and searches`() = runBlocking {
        assumeTrue("Live catalogue check: run with -Pcatalogue.live=true", System.getProperty(LIVE) == "true")
        val check = FeedCheck(temp.newFile())
        val curated = CuratedCatalogue.entries.map { check.check(it.title, it.address) }
        // Addresses to try before curating one; reported, never failing the check.
        val tried = System.getProperty(TRY).orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)
            .map { check.check("(trying)", it) }
        report(curated + tried)

        val broken = curated.filter { it.problem != null }
        assertTrue("Broken catalogues: " + broken.joinToString { "${it.name}: ${it.problem}" }, broken.isEmpty())
    }

    /** A Markdown table: into the run's summary on GitHub Actions, and always into the log. */
    private fun report(reports: List<FeedReport>) {
        val table = buildString {
            appendLine("### Curated catalogues")
            appendLine("| Catalogue | Address | First page | A book it lists | Download | Search | Result |")
            appendLine("|---|---|---|---|---|---|---|")
            reports.forEach { r ->
                val cells = listOf(
                    r.name,
                    r.address,
                    "${r.entries} entries",
                    r.book ?: "-",
                    r.download ?: "-",
                    r.search ?: "-",
                    r.problem?.let { "FAIL: $it" } ?: "pass",
                )
                appendLine(cells.joinToString(" | ", "| ", " |") { it.replace('|', '/') })
            }
        }
        println(table)
        System.getenv(SUMMARY)?.takeIf(String::isNotEmpty)?.let { File(it).appendText(table) }
    }

    private companion object {
        const val LIVE = "audora.catalogue.live"
        const val TRY = "audora.catalogue.try"
        const val SUMMARY = "GITHUB_STEP_SUMMARY"
    }
}
