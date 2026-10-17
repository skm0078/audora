// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/reading/QuoteCarryTest.kt
// Purpose: An open quote carries to the next sentences of its paragraph, and no further (plan item 7).

package io.github.skm0078.audora.playback.reading

import io.github.skm0078.audora.core.config.DialogueConfig
import io.github.skm0078.audora.core.model.UnitKind
import io.github.skm0078.audora.playback.unit
import org.junit.Assert.assertEquals
import org.junit.Test

class QuoteCarryTest {
    private val paragraphPauseMs = 400

    private fun sentence(index: Int, text: String, pause: Int = 0, page: Int = 0, kind: UnitKind = UnitKind.BODY) =
        unit(page, index).copy(text = text, pauseAfterMs = pause, kind = kind)

    @Test
    fun `a long speech stays quoted to its closing mark, within the paragraph`() {
        val carry = QuoteCarry()
        val units = listOf(
            sentence(0, "She stood and said, “Listen to me."),
            sentence(1, "I will not go back there."),
            sentence(2, "Not ever again.” She shut the door."),
            sentence(3, "The rain went on."),
        )

        carry.page(units)

        assertEquals(listOf(false, true, true, false), units.map(carry::startsInQuote))
    }

    @Test
    fun `a paragraph end, a heading or a new page ends a quote left open`() {
        val carry = QuoteCarry()
        val first = listOf(
            sentence(0, "He began, “Once upon a time.", pause = paragraphPauseMs),
            sentence(1, "A new paragraph, no quote here."),
            sentence(2, "Then “a stray mark."),
            sentence(3, "Chapter Two", kind = UnitKind.HEADING),
            sentence(4, "Narration again, “and an open quote."),
        )
        val next = listOf(sentence(0, "Top of the next page.", page = 1))

        carry.page(first)
        carry.page(next)

        assertEquals(listOf(false, false, false, false, false), first.map(carry::startsInQuote))
        assertEquals(false, carry.startsInQuote(next.single()))
    }

    @Test
    fun `old pages are forgotten and a new book starts clean`() {
        val carry = QuoteCarry(config = DialogueConfig(rememberedPages = 1))
        val page0 = listOf(sentence(0, "She said, “Wait."), sentence(1, "Wait for me."))
        val page1 = listOf(sentence(0, "He said, “Run.", page = 1), sentence(1, "Run now.", page = 1))

        carry.page(page0)
        carry.page(page1)
        assertEquals(false, carry.startsInQuote(page0[1]))
        assertEquals(true, carry.startsInQuote(page1[1]))

        carry.clear()
        assertEquals(false, carry.startsInQuote(page1[1]))
    }
}
