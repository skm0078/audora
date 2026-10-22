// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/cleanup/CleanupGoldenTest.kt
// Purpose: Cleanup on real golden pages, and proof that positions saved before it (pipeline 2) still resume.

package io.github.skm0078.audora.pdf.cleanup

import io.github.skm0078.audora.core.config.ReadingUnitConfig
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.UnitKind
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.resume.AnchorMatch
import io.github.skm0078.audora.core.resume.ReAnchoring
import io.github.skm0078.audora.core.resume.ResumeAnchor
import io.github.skm0078.audora.core.resume.SentenceFingerprint
import io.github.skm0078.audora.pdf.PIPELINE_VERSION
import io.github.skm0078.audora.pdf.document.PdfBook
import io.github.skm0078.audora.pdf.document.PdfDocumentHandle
import io.github.skm0078.audora.pdf.document.PdfEngine
import io.github.skm0078.audora.pdf.document.PdfOpenError
import io.github.skm0078.audora.pdf.document.PdfPageError
import io.github.skm0078.audora.pdf.extract.PageTextAssembler
import io.github.skm0078.audora.pdf.extract.RawPage
import io.github.skm0078.audora.pdf.golden.GoldenCorpus
import io.github.skm0078.audora.pdf.layout.PageLayoutAnalyzer
import io.github.skm0078.audora.pdf.layout.ReadingTextBuilder
import io.github.skm0078.audora.pdf.units.ReadingUnitBuilder
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanupGoldenTest {
    private val corpus = GoldenCorpus(File(System.getProperty("audora.golden.dir") ?: "golden"))
    private val builder = ReadingUnitBuilder()
    private val fingerprint = SentenceFingerprint()
    private val reAnchoring = ReAnchoring(fingerprint)

    private fun rawPages(id: String): List<RawPage> = corpus.pages(corpus.documents.single { it.id == id })

    /** Units per page as the player gets them: through PdfBook, so neighbours take part. */
    private fun units(id: String): List<List<ReadingUnit>> {
        val pages = rawPages(id)
        val book = (PdfBook.open(DumpEngine(pages)) as Outcome.Success).value
        return book.use { (0 until book.pageCount).map { (book.readingUnits(it, builder) as Outcome.Success).value } }
    }

    /** Pipeline 2 (P2-02): layout only, no cleanup. */
    private fun unitsBeforeCleanup(id: String): List<List<ReadingUnit>> = rawPages(id).map { raw ->
        val page = PageTextAssembler().assemble(raw)
        builder.build(ReadingTextBuilder().build(page, PageLayoutAnalyzer().analyze(page)))
    }

    /** The text of each page outside tables, which are kept as table units (RI-4a) and read by setting. */
    private fun spoken(id: String) =
        units(id).map { page -> page.filterNot { it.kind.isTable }.joinToString(" ") { it.text } }

    @Test
    fun `running headers and page numbers go on every page`() {
        for (id in listOf("en-udhr-noto", "en-kjv-2col-long", "hi-udhr-noto", "bn-udhr-noto")) {
            for ((page, units) in units(id).withIndex()) {
                assertTrue("$id p$page", units.none { "Audora S2 test corpus" in it.text })
                assertTrue("$id p$page", units.none { it.text.all(Char::isDigit) })
            }
        }
        // "CHAPTER 5" over the chapter title is a heading and stays; the running headers carry a full stop.
        for (text in spoken("en-textbook-forallx-yyc-logic")) {
            assertFalse(
                text,
                "CHAPTER 4." in text || "CHAPTER 5." in text,
            )
        }
        for (text in spoken("en-math-jmathbiol-integrodifference-ccby")) assertFalse(text, "Page " in text)
    }

    @Test
    fun `tables leave the running text, their captions and the text around them stay`() {
        val connectives = spoken("en-textbook-forallx-yyc-logic")[1]
        assertFalse(connectives, "biconditional" in connectives || "rough meaning" in connectives)
        assertTrue(connectives, "This table summarizes them" in connectives)
        assertTrue(connectives, "These are not the only connectives of English of interest." in connectives)

        val paper = spoken("en-2col-physimaging-radonc-ccby")[1]
        assertTrue(paper, "Table 1 Characteristics of evaluated patients." in paper)
        assertFalse(paper, "Glioblastoma Glioblastoma" in paper || "2 Gy" in paper)
        assertFalse(paper, "[4" in paper)

        val table = units("en-2col-physimaging-radonc-ccby")[1].filter { it.kind.isTable }
        assertEquals(UnitKind.TABLE_SUMMARY, table.first().kind)
        assertTrue(table.first().text, table.first().text.startsWith("Pat. Age Gender"))
        assertEquals(listOf(10, 7), listOf(table.first().table?.rowCount, table.first().table?.columnCount))
        assertEquals(11, table.size)
    }

    @Test
    fun `positions saved by pipeline 2 resume at the same sentence`() {
        val results = corpus.documents.flatMap { document ->
            val before = unitsBeforeCleanup(document.id)
            val after = units(document.id)
            before.indices.flatMap { page -> before[page].map { resume(document.id, page, it, after[page]) } }
        }
        assertTrue("${results.size} saved sentences checked", results.size > MIN_CHECKED)
        assertEquals(
            "Sentences still spoken whose saved position no longer resumes",
            emptyList<String>(),
            results.filterNotNull(),
        )
    }

    /** Saves [unit] as pipeline 2 would have, resumes it on the [current] units; a description if it was lost. */
    private fun resume(id: String, page: Int, unit: ReadingUnit, current: List<ReadingUnit>): String? {
        val anchor = ResumeAnchor(
            bookId = BookId("golden"),
            page = page,
            charStart = unit.charStart,
            fingerprint = fingerprint.head(unit.text),
            tailFingerprint = fingerprint.tail(unit.text),
            pipelineVersion = 2,
            savedAtMillis = 0L,
        )
        val point = reAnchoring.locate(anchor, PIPELINE_VERSION, current)
        assertTrue(current.isEmpty() || current.any { it.charStart == point.charStart })
        // Only text that cleanup removed may lose its anchor (a header, a page number, a table cell); the offset
        // fallback then starts at the sentence before it, as ADR-015 designs. Not judged: a lone word (a removed
        // one cannot be told from one still spoken elsewhere), and pieces of a sentence too long for one unit,
        // whose cut points move when a bullet or citation goes.
        val unmatched = point.match == AnchorMatch.OFFSET_UNVERIFIED || point.match == AnchorMatch.PAGE_START
        val judged = words(unit.text).size >= 2 && unit.text.length < maxUnitChars - CUT_SLACK
        // A table cell now sits in a row unit with its neighbours (RI-4a); it resumes like removed text did.
        val running = current.filterNot { it.kind.isTable }
        val stillSpoken = containsRun(words(running.joinToString(" ") { it.text }), words(unit.text))
        return if (unmatched && judged && stillSpoken) "$id p$page u${unit.index}: ${unit.text.take(60)}" else null
    }

    /** Lower-case runs of letters and digits. */
    private fun words(text: String): List<String> =
        text.lowercase().split(Regex("[^\\p{L}\\p{M}\\p{Nd}]+")).filter { it.isNotEmpty() }

    /** [run] appears in [words] as consecutive words. */
    private fun containsRun(words: List<String>, run: List<String>): Boolean = run.isNotEmpty() &&
        words.indices.any { i -> i + run.size <= words.size && run.indices.all { words[i + it] == run[it] } }

    private companion object {
        val maxUnitChars = ReadingUnitConfig().maxUnitChars

        /** A long sentence is cut at the last space before the limit, so a piece is at most a word shorter. */
        const val CUT_SLACK = 40

        /** The corpus has about two thousand sentences; far fewer means the test is not looking at it. */
        const val MIN_CHECKED = 1000
    }

    private class DumpEngine(private val pages: List<RawPage>) : PdfEngine {
        override fun open(password: String?): Outcome<PdfDocumentHandle, PdfOpenError> = Outcome.Success(
            object : PdfDocumentHandle {
                override val pageCount = pages.size

                override fun readPage(pageIndex: Int): Outcome<RawPage, PdfPageError> =
                    Outcome.Success(pages[pageIndex])

                override fun close() = Unit
            },
        )
    }
}
