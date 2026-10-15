// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/layout/ReadingOrderFixtureTest.kt
// Purpose: Reading order and units on real pages (two-column paper and Bible, textbook, Hindi and Bengali).

package io.github.skm0078.audora.pdf.layout

import io.github.skm0078.audora.pdf.extract.PageText
import io.github.skm0078.audora.pdf.extract.PageTextAssembler
import io.github.skm0078.audora.pdf.extract.RawPageFixture
import io.github.skm0078.audora.pdf.extract.normalizeForComparison
import io.github.skm0078.audora.pdf.units.ReadingUnitBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingOrderFixtureTest {
    private val builder = ReadingUnitBuilder()

    private fun page(name: String, dir: String = "layout"): PageText =
        PageTextAssembler().assemble(RawPageFixture.load(name, dir))

    private fun expected(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/layout/$name.expected.txt")).bufferedReader().readText()

    @Test
    fun `two-column Bible page reads left column then right column`() {
        val text = normalizeForComparison(builder.readingText(page("kjv-p0")).text)
        // The S2 corpus header and the page number are P2-03's to remove.
        val body = text.removePrefix("Audora S2 test corpus - en-kjv-2col-long ").removeSuffix(" 1")
        assertEquals(normalizeForComparison(expected("kjv-p0")), body)
    }

    @Test
    fun `sentences crossing the gutter of a two-column paper are one unit each`() {
        val units = builder.build(page("physimaging-p0")).map { it.text }
        assertTrue(
            "Damage to the white matter in the brain is often observed within the first year, while radia­tion " +
                "effects in the grey matter often do not occur until after six to 12 months [1]." in units,
        )
        // The full-width abstract is read before the columns, the left column before the right one.
        val abstract = units.indexOfFirst { it.startsWith("Conclusion: In the long-term follow-up") }
        val left = units.indexOfFirst { it.startsWith("The most common types of brain tumours") }
        val right = units.indexOfFirst { it.startsWith("The demyelination is a consequence") }
        assertTrue("order $abstract < $left < $right", abstract in 0 until left && left < right)
    }

    @Test
    fun `headings are their own units and found by size or bold name`() {
        val paper = builder.readingText(page("physimaging-p0"))
        val headings = paper.paragraphs.filter { it.kind == ParagraphKind.HEADING }
            .map { paper.text.substring(it.start, it.end) }
        assertTrue(headings.toString(), "1. Introduction" in headings)
        val book = builder.readingText(page("forallx-p20"))
        val bookHeadings = book.paragraphs.filter { it.kind == ParagraphKind.HEADING }
            .map { normalizeForComparison(book.text.substring(it.start, it.end)) }
        assertEquals(listOf("2.3 Formal validity"), bookHeadings)
    }

    @Test
    fun `single-column pages keep their text and order`() {
        for ((name, dir) in SINGLE_COLUMN) {
            val page = page(name, dir)
            val reading = normalizeForComparison(builder.readingText(page).text)
            // Only the S2 header (top of the page) and the page number (bottom) move: PDFium emits them together.
            assertEquals(name, withoutCorpusMarks(normalizeForComparison(page.text)), withoutCorpusMarks(reading))
        }
    }

    @Test
    fun `layout neither drops nor duplicates text`() {
        for ((name, dir) in ALL) {
            val page = page(name, dir)
            val reading = builder.readingText(page)
            val sources = reading.text.indices.map(reading::sourceOffset).filter { it != ReadingText.INSERTED }
            assertEquals(name, sources.size, sources.toSet().size)
            val glyphs = page.text.indices.filter { !page.text[it].isWhitespace() }.toSet()
            assertTrue(name, sources.toSet().containsAll(glyphs))
        }
    }

    @Test
    fun `units are ascending, disjoint and match the reading text`() {
        for ((name, dir) in ALL) {
            val reading = builder.readingText(page(name, dir))
            val units = builder.build(reading)
            assertTrue(name, units.isNotEmpty())
            units.zipWithNext().forEach { (a, b) -> assertTrue("$name ${a.index}", a.charEnd <= b.charStart) }
            units.forEach { unit ->
                assertEquals(
                    name,
                    unit.text,
                    normalizeForComparison(reading.text.substring(unit.charStart, unit.charEnd)),
                )
            }
        }
    }

    private fun withoutCorpusMarks(s: String): String =
        s.replace(Regex("Audora S2 test corpus - \\S+"), "").replace(Regex("\\s\\d+\\s*$"), "").trim()
            .split(Regex("\\s+")).joinToString(" ")

    private companion object {
        val SINGLE_COLUMN = listOf(
            "forallx-p20" to "layout",
            "en-udhr-noto-p0" to "rawpages",
            "hi-udhr-noto-p1" to "rawpages",
            "bn-udhr-noto-p0" to "rawpages",
        )
        val ALL =
            SINGLE_COLUMN + listOf("kjv-p0" to "layout", "physimaging-p0" to "layout", "physimaging-p1" to "layout")
    }
}
