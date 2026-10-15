// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/units/ReadingUnitBuilderTest.kt
// Purpose: Sentence units, their offsets into the page text, length caps, languages and line boxes.

package io.github.skm0078.audora.pdf.units

import io.github.skm0078.audora.core.config.ReadingUnitConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.pdf.extract.PageText
import io.github.skm0078.audora.pdf.extract.PageTextAssembler
import io.github.skm0078.audora.pdf.extract.RawPageFixture
import io.github.skm0078.audora.pdf.extract.SyntheticChar
import io.github.skm0078.audora.pdf.extract.normalizeForComparison
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingUnitBuilderTest {
    private val builder = ReadingUnitBuilder()

    private fun pageOf(text: String, pageIndex: Int = 4): PageText =
        PageTextAssembler().assemble(RawPageFixture.page(RawPageFixture.chars(text), pageIndex = pageIndex))

    @Test
    fun `line breaks inside a sentence do not end it`() {
        val page = pageOf("The cat sat\r\non the mat. Then it slept.")
        val units = builder.build(page)
        assertEquals(listOf("The cat sat on the mat.", "Then it slept."), units.map { it.text })
        assertEquals(listOf(0, 1), units.map { it.index })
        assertTrue(units.all { it.page == 4 && it.lang == Lang.EN })
    }

    @Test
    fun `charStart and charEnd point at the sentence in the page text`() {
        val page = pageOf("  First one.   Second one!  ")
        builder.build(page).forEach { unit ->
            assertEquals(unit.text, normalizeForComparison(page.text.substring(unit.charStart, unit.charEnd)))
        }
        assertEquals(2, builder.build(page).first().charStart)
    }

    @Test
    fun `units without letters or digits are skipped`() {
        // A fixed splitter isolates the rule from BreakIterator's grouping of punctuation.
        val text = "Yes. — * 42."
        val splitter = SentenceSplitter { _, _ -> listOf(0 until 4, 4 until 9, 9 until text.length) }
        val units = ReadingUnitBuilder(splitter = splitter).build(pageOf(text))
        assertEquals(listOf("Yes.", "42."), units.map { it.text })
    }

    @Test
    fun `blank page has no units`() {
        assertEquals(emptyList<Any>(), builder.build(pageOf(" \r\n ")))
    }

    @Test
    fun `long sentences are cut at a space below the limit`() {
        val words = List(40) { "word$it" }.joinToString(" ")
        val units = ReadingUnitBuilder(ReadingUnitConfig(maxUnitChars = 50)).build(pageOf(words))
        assertTrue(units.size > 1)
        assertTrue(units.all { it.charEnd - it.charStart <= 50 })
        assertEquals(words, units.joinToString(" ") { it.text })
    }

    @Test
    fun `text without spaces is cut hard at the limit`() {
        val units = ReadingUnitBuilder(ReadingUnitConfig(maxUnitChars = 10)).build(pageOf("x".repeat(25)))
        assertEquals(listOf(10, 10, 5), units.map { it.text.length })
    }

    @Test
    fun `Hindi sentences end at the danda and get the Hindi language`() {
        val units = builder.build(pageOf("सभी मनुष्य स्वतंत्र हैं। उन्हें बुद्धि मिली है।"))
        assertEquals(2, units.size)
        assertTrue(units.all { it.lang == Lang.HI })
    }

    @Test
    fun `bounds are one box per line`() {
        val twoLines =
            "ab".map { SyntheticChar(it.code, box = floatArrayOf(it.code * 10f, 120f, it.code * 10f + 8f, 110f)) } +
                SyntheticChar(' '.code, generated = true) +
                "cd".map {
                    SyntheticChar(
                        it.code,
                        box = floatArrayOf(
                            (it.code - 'c'.code) * 10f,
                            100f,
                            (it.code - 'c'.code) * 10f + 8f,
                            90f,
                        ),
                    )
                }
        val page = PageTextAssembler().assemble(RawPageFixture.page(twoLines))
        val bounds = requireNotNull(builder.build(page).single().bounds)
        assertEquals(2, bounds.size)
        assertEquals(120f, bounds[0].top, 0f)
        assertEquals(90f, bounds[1].bottom, 0f)
    }
}
