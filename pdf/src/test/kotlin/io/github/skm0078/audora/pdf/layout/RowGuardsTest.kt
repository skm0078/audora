// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/layout/RowGuardsTest.kt
// Purpose: Rows kept apart and kept whole: a tall math sign never pulls the next line in, preformatted text is not
// cut into columns.

package io.github.skm0078.audora.pdf.layout

import io.github.skm0078.audora.pdf.layout.PageSketch.Companion.BODY
import io.github.skm0078.audora.pdf.layout.PageSketch.Companion.MONO
import io.github.skm0078.audora.pdf.layout.PageSketch.Companion.REGULAR
import org.junit.Assert.assertEquals
import org.junit.Test

class RowGuardsTest {
    private val analyzer = PageLayoutAnalyzer()
    private val textBuilder = ReadingTextBuilder()

    private fun lineCount(sketch: PageSketch): Int =
        analyzer.analyze(sketch.build()).blocks.sumOf { block -> block.paragraphs.sumOf { it.lines.size } }

    private fun words(sketch: PageSketch): String {
        val page = sketch.build()
        return textBuilder.build(page, analyzer.analyze(page)).text.split(Regex("\\s+")).joinToString(" ").trim()
    }

    private class Stack(val name: String, val sketch: PageSketch, val lines: Int)

    @Test
    fun `a tall sign joins a formula's limit but never two lines of words`() {
        // A math font's "≳" or "∫": a box far taller than its line, reaching down into the line below.
        val tallSign = Triple("≳", TALL_SIZE, TALL_RISE)
        val cases = listOf(
            Stack(
                "words around a tall sign, words below",
                PageSketch()
                    .mixed(
                        listOf(
                            Triple("This bounds the radii to 2GM ", BODY, 0f),
                            tallSign,
                            Triple(" 210 km.", BODY, 0f),
                        ),
                        50f,
                        TOP,
                    )
                    .line("To reach an orbital frequency the objects", 50f, TOP - PITCH),
                lines = 2,
            ),
            Stack(
                "a formula's tall sign and its lower limit",
                PageSketch()
                    .mixed(listOf(Triple("f = ", BODY, 0f), tallSign, Triple(" g(x) dx", BODY, 0f)), 50f, TOP)
                    .line("0", 70f, TOP - PITCH, size = LIMIT_SIZE),
                lines = 1,
            ),
            Stack(
                "two plain lines",
                PageSketch().line("First line of plain words here", 50f, TOP).line(
                    "second line below it",
                    50f,
                    TOP - PITCH,
                ),
                lines = 2,
            ),
        )
        for (case in cases) assertEquals(case.name, case.lines, lineCount(case.sketch))
    }

    @Test
    fun `an option list in a monospace font is read row by row, in a text font column by column`() {
        val options = listOf("-n, --dry-run", "-v, --verbose", "-i, --interactive", "-p, --patch", "-f, --force")
        val meanings = listOf("dry run", "be verbose", "interactive picking", "select hunks", "add ignored files")
        val byRows = options.zip(meanings).joinToString(" ") { (option, meaning) -> "$option $meaning" }
        val byColumns = (options + meanings).joinToString(" ")
        for ((font, expected) in listOf(MONO to byRows, REGULAR to byColumns)) {
            val sketch = PageSketch()
            options.indices.forEach { row ->
                sketch.line(options[row], 50f, TOP - row * PITCH, font = font)
                sketch.line(meanings[row], MEANING_LEFT, TOP - row * PITCH, font = font)
            }
            assertEquals("font $font", expected, words(sketch))
        }
    }

    private companion object {
        const val TOP = 700f
        const val PITCH = 12f
        const val TALL_SIZE = 25f
        const val TALL_RISE = -15f
        const val LIMIT_SIZE = 7f
        const val MEANING_LEFT = 200f
    }
}
