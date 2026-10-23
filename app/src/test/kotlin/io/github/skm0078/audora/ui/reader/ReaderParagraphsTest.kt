// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/ReaderParagraphsTest.kt
// Purpose: Reflowed text (plan item 15): where paragraphs break, which sentence a tap lands on, and the size steps.

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.UnitKind
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderParagraphsTest {
    private fun unit(index: Int, kind: UnitKind = UnitKind.BODY, pause: Int = 0) =
        ReadingUnit(0, index, index * 10, index * 10 + 5, "S$index.", Lang.EN, pause, null, kind)

    @Test
    fun `paragraphs break at a paragraph pause, at anything but body text, and where the kind changes`() {
        val units = listOf(
            unit(0, UnitKind.HEADING, pause = 600),
            unit(1),
            unit(2, pause = 400),
            unit(3),
            unit(4),
            unit(5, UnitKind.LIST_ITEM, pause = 300),
            unit(6, UnitKind.LIST_ITEM),
            unit(7),
        )
        assertEquals(listOf(0..0, 1..2, 3..4, 5..5, 6..6, 7..7), paragraphs(units))
        assertEquals(emptyList<IntRange>(), paragraphs(emptyList()))
    }

    @Test
    fun `a continued table's empty summary is neither drawn nor a paragraph`() {
        val units = continuedTableOf(page = 0)
        assertEquals(units[1].charStart, units[2].charStart)
        assertEquals(listOf(0, 2, 3), drawnUnits(units))
        assertEquals(listOf(0..0, 2..2, 3..3), paragraphs(units))
    }

    @Test
    fun `a tap lands on the sentence under it, or the one before the gap`() {
        val spans = sentenceSpans(listOf("One.", "Two two.", "3"))
        assertEquals(listOf(0..3, 5..12, 14..14), spans)
        val cases = mapOf(0 to 0, 3 to 0, 4 to 0, 5 to 1, 13 to 1, 14 to 2, 99 to 2)
        cases.forEach { (offset, sentence) -> assertEquals("offset $offset", sentence, sentenceAt(spans, offset)) }
    }

    @Test
    fun `text size snaps to five percent steps within range`() {
        val cases = mapOf(1f to 1f, 1.02f to 1f, 1.03f to 1.05f, 0.5f to 0.85f, 3f to 1.6f)
        cases.forEach { (scale, snapped) -> assertEquals("at $scale", snapped, snapScale(scale), 1e-4f) }
    }
}
