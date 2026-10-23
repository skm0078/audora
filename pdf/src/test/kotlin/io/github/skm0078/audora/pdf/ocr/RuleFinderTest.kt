// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/ocr/RuleFinderTest.kt
// Purpose: The lines ruled on a page image are found; text, filled bars and short strokes are not (RI-4a part 2).

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.core.config.RuleConfig
import io.github.skm0078.audora.pdf.document.PageImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleFinderTest {
    private val finder = RuleFinder(RuleConfig())

    /** A white page; [ink] paints rectangles (inclusive pixel bounds) at a grey level. */
    private class Canvas(val width: Int = 400, val height: Int = 300) {
        val gray = ByteArray(width * height) { WHITE.toByte() }

        fun ink(left: Int, top: Int, right: Int, bottom: Int, level: Int = 0): Canvas {
            for (y in top..bottom) for (x in left..right) gray[y * width + x] = level.toByte()
            return this
        }

        fun image() = PageImage(0, width, height, DPI, gray)
    }

    @Test
    fun `a ruled grid gives its horizontal and vertical rules`() {
        val canvas = Canvas()
        for (y in listOf(50, 150, 250)) canvas.ink(20, y, 380, y + 1)
        for (x in listOf(20, 200, 380)) canvas.ink(x, 50, x + 1, 251)
        val rules = finder.find(canvas.image(), EM_PX)
        val (horizontal, vertical) = rules.partition { it.horizontal }
        assertEquals(listOf(50, 150, 250), horizontal.map { it.top }.sorted())
        assertEquals(listOf(20, 200, 380), vertical.map { it.left }.sorted())
        for (rule in horizontal) assertEquals(listOf(20, 381), listOf(rule.left, rule.right))
        for (rule in vertical) assertTrue("$rule", rule.top <= 50 && rule.bottom >= 251)
    }

    @Test
    fun `text, a filled bar and short strokes are not rules`() {
        val canvas = Canvas()
        // Words: dark runs a few em long with word spaces between them, on rows of text.
        for (row in 0 until 3) {
            for (word in 0 until 5) canvas.ink(20 + word * 70, 40 + row * 30, 75 + word * 70, 55 + row * 30)
        }
        canvas.ink(20, 200, 320, 240) // a filled bar
        canvas.ink(20, 270, 100, 271) // a rule too short (4 em; a rule is 8)
        canvas.ink(350, 150, 351, 180) // a stem too short (1.5 em; a rule is 2)
        assertEquals(emptyList<PixelRule>(), finder.find(canvas.image(), EM_PX))
    }

    @Test
    fun `a faint grey rule is found, and a double rule gives two`() {
        val faint = Canvas().ink(20, 60, 380, 61, level = FAINT)
        assertEquals(listOf(60), finder.find(faint.image(), EM_PX).map { it.top })
        // A table's bottom right above a footer's top: thicker together than a rule may be, so split again.
        val double = Canvas().ink(20, 200, 380, 203).ink(20, 212, 380, 215)
        val rules = finder.find(double.image(), EM_PX)
        assertEquals(listOf(200, 212), rules.map { it.top }.sorted())
        assertTrue(rules.all { it.bottom - it.top <= MAX_DOUBLE_LINE })
    }

    @Test
    fun `a broken rule is bridged, and finding rules can be turned off`() {
        val canvas = Canvas().ink(20, 100, 180, 101).ink(183, 100, 380, 101)
        assertEquals(listOf(20 to 380), finder.find(canvas.image(), EM_PX).map { it.left to it.right })
        assertEquals(emptyList<PixelRule>(), RuleFinder(RuleConfig(enabled = false)).find(canvas.image(), EM_PX))
        assertEquals(emptyList<PixelRule>(), finder.find(canvas.image(), 0f))
    }

    private companion object {
        const val WHITE = 255
        const val FAINT = 170
        const val DPI = 300
        const val EM_PX = 20f
        const val MAX_DOUBLE_LINE = 3
    }
}
