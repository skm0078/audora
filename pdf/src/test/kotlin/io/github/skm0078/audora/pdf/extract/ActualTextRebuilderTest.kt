// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/extract/ActualTextRebuilderTest.kt
// Purpose: The /ActualText rebuild on real PDFium output (S2 fixtures) and on synthetic edge cases.

package io.github.skm0078.audora.pdf.extract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActualTextRebuilderTest {
    private val assembler = PageTextAssembler()

    @Test
    fun `Hindi Chromium page reads exactly once the spans are rebuilt`() {
        assertRebuildFixesFixture("hi-udhr-noto-p1", minCerBefore = 0.05)
    }

    @Test
    fun `Bengali Chromium page with reordered reph reads exactly once rebuilt`() {
        assertRebuildFixesFixture("bn-udhr-noto-p0", minCerBefore = 0.10)
    }

    @Test
    fun `English Chromium page stays exact`() {
        val page = assembler.assemble(RawPageFixture.load("en-udhr-noto-p0"))
        assertTrue(
            "rebuild ${page.stats.actualTextRebuild}",
            page.stats.actualTextRebuild != ActualTextRebuild.ALIGNMENT_FAILED,
        )
        val cer = cer("en-udhr-noto-p0", page.text)
        assertTrue("CER=$cer", cer <= MAX_CER)
    }

    @Test
    fun `fake space after a span is dropped unless a space glyph follows`() {
        // PDFium: "मा" + generated " " + "नव" + generated " " ; a space glyph follows "नव" only.
        val page = RawPageFixture.page(
            chars = RawPageFixture.chars("मा नव ", generated = setOf(2, 5)),
            objects = listOf(
                RawTextObject("मा ", actualText = "मा", spanKey = 1, width = 8f),
                RawTextObject("न", actualText = null, spanKey = 0, width = 7f),
                RawTextObject("व ", actualText = null, spanKey = 0, width = 7f),
                RawTextObject("", actualText = null, spanKey = 0, width = 0f),
            ),
        )
        val text = assembler.assemble(page)
        assertEquals("मानव ", text.text)
        assertEquals(ActualTextRebuild.APPLIED, text.stats.actualTextRebuild)
        assertEquals(1, text.stats.actualTextSpans)
    }

    @Test
    fun `space glyph after a span keeps one space`() {
        val page = RawPageFixture.page(
            chars = RawPageFixture.chars("कि मा", generated = setOf(2)),
            objects = listOf(
                RawTextObject("कि ", actualText = "कि", spanKey = 1, width = 8f),
                RawTextObject("", actualText = null, spanKey = 0, width = 0f),
                RawTextObject("मा", actualText = "मा", spanKey = 2, width = 8f),
            ),
        )
        assertEquals("कि मा", assembler.assemble(page).text)
    }

    @Test
    fun `span text replaces glyph characters and objects of one span merge`() {
        // Two glyph objects of one span; PDFium mapped the second glyph to a wrong character.
        val page = RawPageFixture.page(
            chars = RawPageFixture.chars("fXy"),
            objects = listOf(
                RawTextObject("f", actualText = "fi", spanKey = 7, width = 5f),
                RawTextObject("X", actualText = "fi", spanKey = 7, width = 5f),
                RawTextObject("y", actualText = null, spanKey = 0, width = 5f),
            ),
        )
        assertEquals("fiy", assembler.assemble(page).text)
    }

    @Test
    fun `characters of another object inside a span's range are kept in place`() {
        // Bengali reph: PDFium puts the second glyph of span "র্ব" after "জ".
        val page = RawPageFixture.page(
            chars = RawPageFixture.chars("রজব"),
            objects = listOf(
                RawTextObject("র", actualText = "র্ব", spanKey = 3, width = 5f),
                RawTextObject("ব", actualText = "র্ব", spanKey = 3, width = 5f),
                RawTextObject("জ", actualText = null, spanKey = 0, width = 5f),
            ),
        )
        assertEquals("র্বজ", assembler.assemble(page).text)
    }

    @Test
    fun `empty ActualText removes the span`() {
        val page = RawPageFixture.page(
            chars = RawPageFixture.chars("ab-c"),
            objects = listOf(
                RawTextObject("ab", actualText = null, spanKey = 0, width = 5f),
                RawTextObject("-", actualText = "", spanKey = 4, width = 5f),
                RawTextObject("c", actualText = null, spanKey = 0, width = 5f),
            ),
        )
        assertEquals("abc", assembler.assemble(page).text)
    }

    @Test
    fun `generated line break after a span survives`() {
        val page = RawPageFixture.page(
            chars = RawPageFixture.chars("मा\r\nनव", generated = setOf(2, 3)),
            objects = listOf(
                RawTextObject("मा\r\n", actualText = "मा", spanKey = 1, width = 8f),
                RawTextObject("नव", actualText = null, spanKey = 0, width = 8f),
            ),
        )
        assertEquals("मा\r\nनव", assembler.assemble(page).text)
    }

    @Test
    fun `unmatched object leaves the page as PDFium read it`() {
        val page = RawPageFixture.page(
            chars = RawPageFixture.chars("मा नव", generated = setOf(2)),
            objects = listOf(
                RawTextObject("मा ", actualText = "मा", spanKey = 1, width = 8f),
                RawTextObject("zz", actualText = null, spanKey = 0, width = 8f),
            ),
        )
        val text = assembler.assemble(page)
        assertEquals("मा नव", text.text)
        assertEquals(ActualTextRebuild.ALIGNMENT_FAILED, text.stats.actualTextRebuild)
    }

    @Test
    fun `page without ActualText is not touched`() {
        val page = RawPageFixture.page(
            chars = RawPageFixture.chars("a b", generated = setOf(1)),
            objects = listOf(RawTextObject("a b", actualText = null, spanKey = 0, width = 8f)),
        )
        val text = assembler.assemble(page)
        assertEquals("a b", text.text)
        assertEquals(ActualTextRebuild.NOT_NEEDED, text.stats.actualTextRebuild)
    }

    private fun assertRebuildFixesFixture(name: String, minCerBefore: Double) {
        val raw = RawPageFixture.load(name)
        val rebuilt = assembler.assemble(raw)
        assertEquals(ActualTextRebuild.APPLIED, rebuilt.stats.actualTextRebuild)
        assertTrue("expected /ActualText spans", rebuilt.stats.actualTextSpans > 0)
        val withoutRebuild =
            String(IntArray(raw.chars.size) { safeCodePoint(raw.chars.codePoint(it)) }, 0, raw.chars.size)
        val before = cer(name, withoutRebuild)
        val after = cer(name, rebuilt.text)
        assertTrue("PDFium alone should be visibly wrong, CER=$before", before >= minCerBefore)
        assertTrue("rebuilt CER=$after", after <= MAX_CER)
    }

    private fun cer(name: String, actual: String): Double = characterErrorRate(
        normalizeForComparison(RawPageFixture.expectedText(name)),
        normalizeForComparison(withoutCorpusFooter(actual)),
    )

    private companion object {
        const val MAX_CER = 0.01
    }
}
