// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/layout/PageLayoutAnalyzerTest.kt
// Purpose: Reading order (columns, titles, single lines), paragraphs, headings and the extraction-order fallback.

package io.github.skm0078.audora.pdf.layout

import io.github.skm0078.audora.core.config.LayoutConfig
import io.github.skm0078.audora.pdf.layout.PageSketch.Companion.BODY
import io.github.skm0078.audora.pdf.layout.PageSketch.Companion.BOLD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageLayoutAnalyzerTest {
    private val analyzer = PageLayoutAnalyzer()
    private val textBuilder = ReadingTextBuilder()

    private fun read(sketch: PageSketch): ReadingText {
        val page = sketch.build()
        return textBuilder.build(page, analyzer.analyze(page))
    }

    private fun paragraphs(reading: ReadingText): List<String> =
        reading.paragraphs.map { reading.text.substring(it.start, it.end).replace('\n', ' ') }

    @Test
    fun `two columns drawn row by row are read column by column`() {
        val sketch = PageSketch()
        for (row in 0 until 3) {
            val top = 700f - row * 12f
            sketch.line("left column line ${row + 1} words", left = 50f, top = top)
            sketch.line("right column line ${row + 1} words", left = 300f, top = top)
        }
        val reading = read(sketch)
        assertEquals(
            (1..3).joinToString(" ") { "left column line $it words" } + " " +
                (1..3).joinToString(" ") { "right column line $it words" },
            reading.text.split(Regex("\\s+")).joinToString(" "),
        )
    }

    @Test
    fun `PDFium joining two columns into one line is undone`() {
        // One content-stream line per row across both columns, as PDFium reports some generators.
        val sketch = PageSketch()
        for (row in 0 until 3) {
            val top = 700f - row * 12f
            sketch.line(
                "L${row + 1} alpha beta gamma delta" + " ".repeat(60) + "R${row + 1} alpha beta gamma",
                50f,
                top,
            )
        }
        val words = read(sketch).text.split(Regex("\\s+")).filter { it.startsWith("L") || it.startsWith("R") }
        assertEquals(listOf("L1", "L2", "L3", "R1", "R2", "R3"), words)
    }

    @Test
    fun `a title spanning both columns comes first and is a heading`() {
        val sketch = PageSketch()
        sketch.line("A Title Across The Whole Page Width Here", left = 50f, top = 760f, size = 18f)
        for (row in 0 until 3) {
            val top = 700f - row * 12f
            sketch.line("left words in row ${row + 1} here", left = 50f, top = top)
            sketch.line("right words in row ${row + 1} here", left = 300f, top = top)
        }
        val reading = read(sketch)
        assertEquals(ParagraphKind.HEADING, reading.paragraphs.first().kind)
        assertTrue(paragraphs(reading).first().startsWith("A Title Across"))
        assertTrue(reading.text.indexOf("left words in row 3") < reading.text.indexOf("right words in row 1"))
    }

    @Test
    fun `a sentence running into the next column is one paragraph`() {
        val sketch = PageSketch()
        sketch.lines(listOf("First sentence ends here. The quick", "brown fox runs to the end of the"), 50f, 700f)
        sketch.lines(listOf("left column and then it jumps.", "A new sentence starts here."), 300f, 700f)
        val page = sketch.build()
        val layout = analyzer.analyze(page)
        assertTrue(layout.blocks[1].paragraphs.first().continuesPrevious)
        assertEquals(1, textBuilder.build(page, layout).paragraphs.size)
    }

    @Test
    fun `a column ending a sentence does not run into the next one`() {
        val sketch = PageSketch()
        sketch.lines(listOf("First sentence ends here and the", "column ends with a full stop."), 50f, 700f)
        sketch.lines(listOf("Second column starts afresh with", "a new paragraph of its own."), 300f, 700f)
        assertEquals(2, read(sketch).paragraphs.size)
    }

    @Test
    fun `larger text is a heading and the body below it is its own paragraph`() {
        val sketch = PageSketch()
        sketch.line("Introduction", left = 50f, top = 720f, size = 14f)
        sketch.lines(listOf("Body text follows the heading here", "and carries on for a second line."), 50f, 700f)
        val reading = read(sketch)
        assertEquals(listOf(ParagraphKind.HEADING, ParagraphKind.BODY), reading.paragraphs.map { it.kind })
        assertEquals("Introduction", paragraphs(reading).first())
    }

    @Test
    fun `a bold font name makes a short line a heading, font weight is not used`() {
        val sketch = PageSketch()
        sketch.line("Methods", left = 50f, top = 712f, font = BOLD)
        sketch.lines(listOf("Body text follows the heading here", "and carries on for a second line."), 50f, 700f)
        assertEquals(ParagraphKind.HEADING, read(sketch).paragraphs.first().kind)
    }

    @Test
    fun `a wider gap than the line spacing starts a paragraph`() {
        val sketch = PageSketch()
        val below = sketch.lines(listOf("One line of the first paragraph", "and its second line ends it."), 50f, 700f)
        sketch.lines(
            listOf("The second paragraph starts after", "an empty line and goes on."),
            50f,
            below - BODY * 1.2f,
        )
        assertEquals(2, read(sketch).paragraphs.size)
    }

    @Test
    fun `a first-line indent starts a paragraph`() {
        val sketch = PageSketch()
        sketch.lines(listOf("One line of the first paragraph", "and its second line ends it."), 50f, 700f)
        sketch.lines(listOf("Indented start of paragraph two", "and its second line."), 50f, 676f)
        val indented = PageSketch().apply {
            lines(listOf("One line of the first paragraph", "and its second line ends it."), 50f, 700f)
            line("Indented start of paragraph two", 62f, 676f)
            line("and its second line.", 50f, 664f)
        }
        assertEquals(1, read(sketch).paragraphs.size)
        assertEquals(2, read(indented).paragraphs.size)
    }

    @Test
    fun `a wide justified space on a single line is not a gutter`() {
        val sketch = PageSketch()
        sketch.line("Lonely start" + " ".repeat(40) + "lonely end", left = 50f, top = 700f)
        val layout = analyzer.analyze(sketch.build())
        assertEquals(1, layout.blocks.size)
        assertEquals(2, layout.blocks.single().paragraphs.single().lines.single().spans.size)
    }

    @Test
    fun `a gap only one line has is not a gutter`() {
        // A wide gap in a paragraph's last-but-one line, above a short last line: the line must stay whole.
        val sketch = PageSketch()
        val gapped = "first line of a paragraph that has" + " ".repeat(8) + "a wide gap at its end"
        sketch.line(gapped, left = 50f, top = 700f)
        sketch.line("short last line.", left = 50f, top = 688f)
        val reading = read(sketch)
        assertEquals(
            "first line of a paragraph that has a wide gap at its end short last line.",
            reading.text.split(Regex("\\s+")).joinToString(" "),
        )
    }

    @Test
    fun `a pre-base vowel sign drawn left of its consonant does not split the word`() {
        // "किताब": the vowel sign ि is stored after क but drawn left of it.
        val page = PageSketch().glyphs(listOf('क' to 55f, 'ि' to 50f, 'त' to 60f, 'ा' to 65f, 'ब' to 70f), 700f).build()
        val config = LayoutConfig()
        assertEquals(1, GlyphRunSplitter(config).split(page, GlyphStyles(page, config)).runs.size)
    }

    @Test
    fun `pages without glyph boxes keep the extraction order as one paragraph`() {
        val sketch = PageSketch(boxes = false)
        sketch.line("second visually", left = 50f, top = 600f)
        sketch.line("first visually", left = 50f, top = 700f)
        val page = sketch.build()
        val layout = analyzer.analyze(page)
        assertEquals(LayoutMode.EXTRACTION_ORDER, layout.mode)
        assertEquals("second visually\nfirst visually", textBuilder.build(page, layout).text)
    }

    @Test
    fun `a line-end hyphen joins the next line without a break`() {
        val sketch = PageSketch()
        sketch.lines(listOf("a hypo\u0002", "thetical case"), 50f, 700f)
        assertEquals("a hypo\u0002thetical case", read(sketch).text)
    }

    @Test
    fun `reading text maps back to the glyph boxes`() {
        val page = PageSketch().line("ab", 50f, 700f).build()
        val reading = textBuilder.build(page, analyzer.analyze(page))
        assertEquals(page.boxAt(1), reading.boxAt(1))
        assertEquals(1, reading.sourceOffset(1))
    }

    @Test
    fun `an empty page has no blocks`() {
        val layout = analyzer.analyze(PageSketch().build())
        assertTrue(layout.blocks.isEmpty())
        assertFalse(textBuilder.build(PageSketch().build(), layout).text.isNotEmpty())
    }
}
