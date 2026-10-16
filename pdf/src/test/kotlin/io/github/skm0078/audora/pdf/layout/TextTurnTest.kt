// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/layout/TextTurnTest.kt
// Purpose: Pages set sideways or upside down are laid out turned upright and read line by line (ADR-032).

package io.github.skm0078.audora.pdf.layout

import io.github.skm0078.audora.core.model.TextBox
import io.github.skm0078.audora.pdf.extract.PageText
import io.github.skm0078.audora.pdf.extract.PageTextAssembler
import io.github.skm0078.audora.pdf.extract.RawPageFixture
import io.github.skm0078.audora.pdf.extract.SyntheticChar
import io.github.skm0078.audora.pdf.units.ReadingUnitBuilder
import org.junit.Assert.assertEquals
import org.junit.Test

class TextTurnTest {
    private val first = "The quick brown fox jumps over the lazy dog."
    private val second = "Then it sleeps until the sun goes down again."

    /**
     * [lines] in extraction order, each placed by [place] from its reading position (line, char): what a page with
     * that text direction looks like to PDFium.
     */
    private fun page(lines: List<Pair<Int, String>>, place: (line: Int, char: Int) -> TextBox): PageText {
        val chars = ArrayList<SyntheticChar>()
        lines.forEachIndexed { i, (line, text) ->
            if (i > 0) "\r\n".forEach { chars.add(SyntheticChar(it.code, generated = true)) }
            text.forEachIndexed { c, ch ->
                val box = place(line, c)
                chars.add(SyntheticChar(ch.code, box = floatArrayOf(box.left, box.top, box.right, box.bottom)))
            }
        }
        return PageTextAssembler().assemble(RawPageFixture.page(chars))
    }

    private fun spoken(page: PageText): List<String> = ReadingUnitBuilder().build(page).map { it.text }

    @Test
    fun `text running up the page is turned clockwise and read line by line`() {
        // Reading line 0 sits left of line 1; each line climbs 10 pt per glyph.
        val page = page(listOf(1 to second, 0 to first)) { line, c ->
            val x = 100f + line * GAP
            TextBox(left = x, top = 108f + c * STEP, right = x + GLYPH, bottom = 100f + c * STEP)
        }
        assertEquals(TextTurn.CLOCKWISE, PageLayoutAnalyzer().analyze(page).turn)
        assertEquals(listOf(first, second), spoken(page))
    }

    @Test
    fun `text running down the page is turned counter-clockwise`() {
        // Reading line 0 sits right of line 1; each line descends.
        val page = page(listOf(0 to first, 1 to second)) { line, c ->
            val x = 300f - line * GAP
            TextBox(left = x, top = 600f - c * STEP, right = x + GLYPH, bottom = 592f - c * STEP)
        }
        val layout = PageLayoutAnalyzer().analyze(page)
        assertEquals(TextTurn.COUNTER_CLOCKWISE, layout.turn)
        assertEquals(LayoutMode.GEOMETRIC, layout.mode)
        assertEquals(listOf(first, second), spoken(page))
    }

    @Test
    fun `upside-down text is turned half way`() {
        val page = page(listOf(1 to second, 0 to first)) { line, c ->
            val y = 100f + line * GAP
            TextBox(left = 600f - c * STEP - GLYPH, top = y + GLYPH, right = 600f - c * STEP, bottom = y)
        }
        assertEquals(TextTurn.HALF, PageLayoutAnalyzer().analyze(page).turn)
        assertEquals(listOf(first, second), spoken(page))
    }

    @Test
    fun `highlight boxes stay in page space on a turned page`() {
        val page = page(listOf(0 to first)) { _, c ->
            TextBox(left = 100f, top = 108f + c * STEP, right = 100f + GLYPH, bottom = 100f + c * STEP)
        }
        val bounds = ReadingUnitBuilder().build(page).single().bounds.orEmpty()
        // One column of glyphs on the page: x stays in 100..108, y climbs from 100.
        assertEquals(100f, bounds.minOf { it.left })
        assertEquals(100f + GLYPH, bounds.maxOf { it.right })
        assertEquals(100f, bounds.minOf { it.bottom })
    }

    @Test
    fun `a few stacked glyphs are not enough to turn a page`() {
        val page = page(listOf(0 to "ABCDE")) { _, c ->
            TextBox(left = 100f, top = 108f + c * STEP, right = 100f + GLYPH, bottom = 100f + c * STEP)
        }
        assertEquals(TextTurn.NONE, PageLayoutAnalyzer().analyze(page).turn)
    }

    private companion object {
        const val STEP = 10f
        const val GLYPH = 8f
        const val GAP = 20f
    }
}
