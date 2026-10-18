// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/units/ReadingUnitBuilderTest.kt
// Purpose: Sentence units, their offsets into the page text, length caps, languages and line boxes.

package io.github.skm0078.audora.pdf.units

import io.github.skm0078.audora.core.config.ReadingUnitConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.UnitKind
import io.github.skm0078.audora.pdf.extract.PageText
import io.github.skm0078.audora.pdf.extract.PageTextAssembler
import io.github.skm0078.audora.pdf.extract.RawPageFixture
import io.github.skm0078.audora.pdf.extract.SyntheticChar
import io.github.skm0078.audora.pdf.extract.normalizeForComparison
import io.github.skm0078.audora.pdf.layout.PageSketch
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
    fun `charStart and charEnd point at the sentence in the reading text`() {
        val page = pageOf("  First one.   Second one!  ")
        val reading = builder.readingText(page)
        builder.build(page).forEach { unit ->
            assertEquals(unit.text, normalizeForComparison(reading.text.substring(unit.charStart, unit.charEnd)))
        }
        assertEquals(0, builder.build(page).first().charStart)
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
    fun `Indic units are NFC, split at a typed pipe and keep a doctor's name whole`() {
        // Bengali "ো" typed in two parts (U+09C7 U+09BE), "|" typed for the danda, and "ডা." before a name.
        val page = pageOf("ডা. রায় \u0995\u09C7\u09BEথায় যাবেন| তিনি বাড়ি যাবেন।")
        val units = builder.build(page)
        assertEquals(listOf("ডা. রায় \u0995\u09CBথায় যাবেন।", "তিনি বাড়ি যাবেন।"), units.map { it.text })
        assertTrue(units.all { it.lang == Lang.BN })
        val reading = builder.readingText(page)
        units.forEach { unit -> assertEquals(unit.text, reading.text.substring(unit.charStart, unit.charEnd)) }
    }

    @Test
    fun `bounds are one box per line`() {
        val twoLines =
            "ab".map {
                val left = (it.code - 'a'.code) * 10f
                SyntheticChar(it.code, box = floatArrayOf(left, 120f, left + 8f, 110f))
            } +
                SyntheticChar(' '.code, generated = true) +
                "cd".map {
                    SyntheticChar(
                        it.code,
                        box = floatArrayOf(
                            (it.code - 'c'.code) * 10f,
                            106f,
                            (it.code - 'c'.code) * 10f + 8f,
                            96f,
                        ),
                    )
                }
        val page = PageTextAssembler().assemble(RawPageFixture.page(twoLines))
        val bounds = requireNotNull(builder.build(page).single().bounds)
        assertEquals(2, bounds.size)
        assertEquals(120f, bounds[0].top, 0f)
        assertEquals(96f, bounds[1].bottom, 0f)
    }

    @Test
    fun `a heading without a full stop is its own unit and pauses take the configured lengths`() {
        val sketch = PageSketch()
        sketch.line("Introduction", left = 50f, top = 720f, size = 14f)
        sketch.lines(listOf("Body text follows the heading. It", "carries on for a second line."), 50f, 700f)
        val config = ReadingUnitConfig(sentencePauseMs = 10, paragraphPauseMs = 400, headingPauseMs = 600)
        val units = ReadingUnitBuilder(config).build(sketch.build())
        assertEquals(
            listOf("Introduction", "Body text follows the heading.", "It carries on for a second line."),
            units.map { it.text },
        )
        assertEquals(listOf(600, 10, 400), units.map { it.pauseAfterMs })
    }

    @Test
    fun `each list item is its own unit with the list pause, and the list ends where the text moves back left`() {
        val sketch = PageSketch()
        sketch.line("Pack these things:", left = 50f, top = 720f)
        sketch.line("\u2022 Apples", left = 50f, top = 708f)
        sketch.line("\u2022 Pears and", left = 50f, top = 696f)
        sketch.line("plums", left = 62f, top = 684f)
        sketch.line("Then we leave.", left = 50f, top = 672f)
        val config = ReadingUnitConfig(sentencePauseMs = 10, paragraphPauseMs = 400, listItemPauseMs = 300)
        val units = ReadingUnitBuilder(config).build(sketch.build())
        assertEquals(listOf("Pack these things:", "Apples", "Pears and plums", "Then we leave."), units.map { it.text })
        assertEquals(
            listOf(UnitKind.BODY, UnitKind.LIST_ITEM, UnitKind.LIST_ITEM, UnitKind.BODY),
            units.map { it.kind },
        )
        assertEquals(listOf(400, 300, 300, 400), units.map { it.pauseAfterMs })
    }

    @Test
    fun `numbered items split after a clause or inside a list, never in running prose`() {
        val sketch = PageSketch()
        sketch.line("Do this:", left = 50f, top = 720f)
        sketch.line("1. Open the box", left = 50f, top = 708f)
        sketch.line("2. Take it out", left = 50f, top = 696f)
        // A gap, then prose whose wrapped line happens to start with "3." after a line that ends mid-clause.
        sketch.line("Read the steps in section", left = 50f, top = 660f)
        sketch.line("3. We then close it.", left = 50f, top = 648f)
        val units = ReadingUnitBuilder().build(sketch.build())
        assertEquals(
            listOf(
                "Do this:",
                "1. Open the box",
                "2. Take it out",
                "Read the steps in section 3.",
                "We then close it.",
            ),
            units.map { it.text },
        )
        assertEquals(
            listOf(UnitKind.BODY, UnitKind.LIST_ITEM, UnitKind.LIST_ITEM, UnitKind.BODY, UnitKind.BODY),
            units.map { it.kind },
        )
    }

    @Test
    fun `letter items follow their letter across a list nested flush, a paragraph opening with c dot is prose`() {
        val sketch = PageSketch()
        val lines = listOf("Choose one:", "a. Rice", "(i) white", "(ii) brown", "b. Wheat", "c. Maize")
        lines.forEachIndexed { i, line -> sketch.line(line, left = 50f, top = 720f - i * 12f) }
        val units = ReadingUnitBuilder().build(sketch.build())
        assertEquals(lines, units.map { it.text })
        assertEquals(listOf(UnitKind.BODY) + List(lines.size - 1) { UnitKind.LIST_ITEM }, units.map { it.kind })
        // A paragraph whose first line opens with a circa date, no list before it.
        val circa = PageSketch()
        circa.line("The old town grew slowly.", left = 50f, top = 720f)
        circa.line("c. 1900 the mill opened.", left = 50f, top = 680f)
        assertEquals(
            listOf(UnitKind.BODY, UnitKind.BODY),
            ReadingUnitBuilder().build(circa.build()).map { it.kind },
        )
    }

    @Test
    fun `headings carry their kind and default pauses follow the spec`() {
        val sketch = PageSketch()
        sketch.line("Introduction", left = 50f, top = 720f, size = 14f)
        sketch.line("Body text follows the heading.", left = 50f, top = 700f)
        val units = ReadingUnitBuilder().build(sketch.build())
        assertEquals(listOf(UnitKind.HEADING, UnitKind.BODY), units.map { it.kind })
        assertEquals(listOf(600, 400), units.map { it.pauseAfterMs })
    }

    @Test
    fun `words in a bold font are the unit's bold terms, as spans of its text`() {
        val marked = "Water moves by osmosis. The [Krebs cycle] makes ATP in the [mitochondria]."
        val chars = ArrayList<SyntheticChar>()
        var bold = false
        for (c in marked) {
            when (c) {
                '[' -> bold = true
                ']' -> bold = false
                else -> chars.add(RawPageFixture.chars(c.toString()).single().copy(fontId = if (bold) 2 else 0))
            }
        }
        val fonts = listOf("Body", "Heading", "ABCDEF+Times-Bold")
        val page = PageTextAssembler().assemble(RawPageFixture.page(chars, fontNames = fonts))
        val units = builder.build(page)
        assertEquals(
            listOf(emptyList(), listOf("Krebs cycle", "mitochondria")),
            units.map { unit -> unit.boldTerms.map { unit.text.substring(it.start, it.end) } },
        )
    }
}
