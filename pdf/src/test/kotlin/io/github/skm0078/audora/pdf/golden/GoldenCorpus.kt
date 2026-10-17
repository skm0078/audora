// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/golden/GoldenCorpus.kt
// Purpose: Loads the golden corpus in pdf/golden/: the document list, the PDFium page dumps and the expected text.

package io.github.skm0078.audora.pdf.golden

import io.github.skm0078.audora.pdf.extract.RawPage
import io.github.skm0078.audora.pdf.extract.RawPageFixture
import java.io.File
import java.util.zip.GZIPInputStream

/** How far the expected text can be trusted (pdf/golden/README.md, "Expected text"). */
enum class ExpectedStatus(val label: String) {
    /** Ground truth from the generator that made the PDF. */
    EXACT("exact"),

    /** Drafted from another extractor and corrected by eye; waits for a human hand-check. */
    DRAFT("draft"),

    /** Hand-checked against the rendered pages by a person. */
    CHECKED("checked"),
    ;

    companion object {
        fun of(label: String): ExpectedStatus =
            entries.firstOrNull { it.label == label } ?: error("unknown expected-text status '$label'")
    }
}

/** One row of corpus.tsv. [textId] names the expected-text file, shared when two PDFs hold the same words. */
data class GoldenDocument(
    val id: String,
    val category: String,
    val source: String,
    val pages: String,
    val textId: String,
    val status: ExpectedStatus,
)

/**
 * A corpus folder (pdf/golden/README.md): the golden corpus keeps its expected texts in `expected/`; the listening
 * corpus (pdf/golden/listening/, RI-8a) has the same layout with spoken scripts in `spoken/`.
 */
class GoldenCorpus(private val root: File, private val expectedDir: String = "expected") {
    val documents: List<GoldenDocument> by lazy { readIndex() }

    /** Every page of the document's dump, in order. */
    fun pages(document: GoldenDocument): List<RawPage> = readDump(root.resolve("rawpages/${document.id}.rawpages.gz"))

    fun expectedText(document: GoldenDocument): String {
        val file = root.resolve("$expectedDir/${document.textId}.txt")
        require(file.isFile) { "missing $file" }
        return file.readText(Charsets.UTF_8)
    }

    private fun readIndex(): List<GoldenDocument> {
        val lines = root.resolve(INDEX).readLines(Charsets.UTF_8).filter { it.isNotBlank() }
        require(lines.firstOrNull()?.split('\t') == COLUMNS) { "$INDEX: the header must be $COLUMNS" }
        return lines.drop(1).mapIndexed { i, line ->
            val f = line.split('\t')
            require(f.size == COLUMNS.size) { "$INDEX line ${i + 2}: expected ${COLUMNS.size} tab-separated fields" }
            GoldenDocument(f[0], f[1], f[2], f[3], f[4], ExpectedStatus.of(f[5]))
        }.also { documents ->
            val duplicates = documents.groupBy { it.id }.filterValues { it.size > 1 }.keys
            require(duplicates.isEmpty()) { "$INDEX: duplicate ids $duplicates" }
        }
    }

    companion object {
        const val INDEX = "corpus.tsv"
        private const val PAGE_RECORD = "page "

        /** Every page of a dump written by scripts/pdf/golden_corpus.py, in order. */
        fun readDump(dump: File): List<RawPage> {
            require(dump.isFile) { "missing $dump: run python3 scripts/pdf/golden_corpus.py dump" }
            val pages = ArrayList<ArrayList<String>>()
            GZIPInputStream(dump.inputStream()).bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line ->
                    if (line.startsWith(PAGE_RECORD)) pages.add(ArrayList())
                    val page = pages.lastOrNull() ?: error("$dump does not start with a page record")
                    page.add(line)
                }
            }
            return pages.map { RawPageFixture.parse(it.asSequence()) }
        }
        private val COLUMNS = listOf("id", "category", "source", "pages", "text", "status")
    }
}
