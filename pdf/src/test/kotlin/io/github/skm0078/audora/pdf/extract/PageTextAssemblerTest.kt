// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/extract/PageTextAssemblerTest.kt
// Purpose: PageText offsets, boxes, font runs and stats.

package io.github.skm0078.audora.pdf.extract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PageTextAssemblerTest {
    private val assembler = PageTextAssembler()

    @Test
    fun `boxes follow UTF-16 units including supplementary characters`() {
        val page = assembler.assemble(RawPageFixture.page(RawPageFixture.chars("a\uD83D\uDE00b")))
        assertEquals("a\uD83D\uDE00b", page.text)
        assertEquals(page.boxAt(1), page.boxAt(2))
        assertEquals(20f, requireNotNull(page.boxAt(3)).left, 0f) // the third PDFium char starts at 20 pt
    }

    @Test
    fun `generated characters have no box`() {
        val page = assembler.assemble(RawPageFixture.page(RawPageFixture.chars("a b", generated = setOf(1))))
        assertNotNull(page.boxAt(0))
        assertNull(page.boxAt(1))
    }

    @Test
    fun `invalid code points become the replacement character`() {
        val chars = listOf(SyntheticChar('a'.code), SyntheticChar(0), SyntheticChar(0xD800))
        assertEquals("a\uFFFD\uFFFD", assembler.assemble(RawPageFixture.page(chars)).text)
    }

    @Test
    fun `font runs split on font and size changes and ignore generated spaces`() {
        val chars = listOf(
            SyntheticChar('H'.code, fontId = 1, fontSize = 18f),
            SyntheticChar('i'.code, fontId = 1, fontSize = 18f),
            SyntheticChar(' '.code, generated = true),
            SyntheticChar('y'.code, fontId = 0, fontSize = 12f),
            SyntheticChar('o'.code, fontId = 0, fontSize = 12f),
        )
        val runs = assembler.assemble(RawPageFixture.page(chars)).fontRuns
        assertEquals(
            listOf(FontRun(0, 3, "Heading", 18f), FontRun(3, 5, "Body", 12f)),
            runs,
        )
    }

    @Test
    fun `stats count generated, mis-mapped and invisible characters`() {
        val chars = listOf(
            SyntheticChar('a'.code, mapError = true),
            SyntheticChar(' '.code, generated = true),
            SyntheticChar('b'.code, renderMode = RawChars.RENDER_MODE_INVISIBLE),
            SyntheticChar('c'.code, renderMode = -1),
        )
        val stats = assembler.assemble(RawPageFixture.page(chars)).stats
        assertEquals(PageTextStats(4, 1, 1, 1, 0, ActualTextRebuild.NOT_NEEDED), stats)
    }

    @Test
    fun `unknown render mode is reported as unknown`() {
        val raw = RawPageFixture.page(listOf(SyntheticChar('c'.code, renderMode = -1)))
        assertEquals(RawChars.RENDER_MODE_UNKNOWN, raw.chars.renderMode(0))
    }
}
