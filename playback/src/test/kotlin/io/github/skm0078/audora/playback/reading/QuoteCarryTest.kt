// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/reading/QuoteCarryTest.kt
// Purpose: An open quote carries to the next sentences of its paragraph, and no further (plan item 7); who speaks each
// quoted sentence follows tags, paragraphs, turns and pronouns (plan item 63).

package io.github.skm0078.audora.playback.reading

import io.github.skm0078.audora.core.config.DialogueConfig
import io.github.skm0078.audora.core.model.Feeling
import io.github.skm0078.audora.core.model.UnitKind
import io.github.skm0078.audora.playback.unit
import io.github.skm0078.audora.speech.sherpa.SpeakerGender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuoteCarryTest {
    private val paragraphPauseMs = 400

    private fun sentence(index: Int, text: String, pause: Int = 0, page: Int = 0, kind: UnitKind = UnitKind.BODY) =
        unit(page, index).copy(text = text, pauseAfterMs = pause, kind = kind)

    @Test
    fun `a sad line's mood reaches the next sentence of its paragraph, not across a paragraph break`() {
        val carry = QuoteCarry()
        val units = listOf(
            sentence(0, "She wept."),
            sentence(1, "It had been so long.", pause = paragraphPauseMs),
            sentence(2, "She sobbed again.", pause = paragraphPauseMs),
            sentence(3, "Morning came."),
        )

        carry.page(units)

        assertEquals(listOf(null, Feeling.SAD, null, null), units.map(carry::moodOf))
    }

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

    @Test
    fun `a tagged speech carries its speaker through the paragraph, and lines alternate after`() {
        val carry = QuoteCarry()
        val units = listOf(
            sentence(0, "“Where were you?” Ann asked.", pause = paragraphPauseMs),
            sentence(1, "“Out,” said Tom.", pause = paragraphPauseMs),
            sentence(2, "“Out where?", pause = 0),
            sentence(3, "Tell me.”", pause = paragraphPauseMs),
            sentence(4, "“Just out.”", pause = paragraphPauseMs),
            sentence(5, "The clock struck nine.", pause = paragraphPauseMs),
            sentence(6, "“Fine.”", pause = paragraphPauseMs),
        )

        carry.page(units)

        val names = units.map { carry.speakerOf(it)?.name }
        assertEquals(listOf("Ann", "Tom", "Ann", "Ann", "Tom", null, null), names)
    }

    @Test
    fun `a pronoun points to the speaker of its gender and teaches it`() {
        val carry = QuoteCarry()
        val units = listOf(
            sentence(0, "“Come in,” said Mrs Gupta.", pause = paragraphPauseMs),
            sentence(1, "“Thank you,” said Ravi.", pause = paragraphPauseMs),
            sentence(2, "“Sit down,” she said.", pause = paragraphPauseMs),
            sentence(3, "“I will stand,” he said.", pause = paragraphPauseMs),
            sentence(4, "The room went quiet.", pause = paragraphPauseMs),
            sentence(5, "“Who is there?” he asked.", pause = paragraphPauseMs),
        )

        carry.page(units)

        assertEquals("Mrs Gupta", carry.speakerOf(units[2])?.name)
        assertEquals("Ravi", carry.speakerOf(units[3])?.name)
        assertEquals(SpeakerGender.MALE, carry.speakerOf(units[3])?.gender)
        assertEquals("Ravi", carry.speakerOf(units[5])?.name)
    }

    @Test
    fun `a pronoun with no one to point to stays a generic speaker, and narration has none`() {
        val carry = QuoteCarry()
        val units = listOf(
            sentence(0, "“Halt,” she said.", pause = paragraphPauseMs),
            sentence(1, "Nobody spoke.", pause = paragraphPauseMs),
        )

        carry.page(units)

        assertEquals(null, carry.speakerOf(units[0])?.name)
        assertEquals(SpeakerGender.FEMALE, carry.speakerOf(units[0])?.gender)
        assertNull(carry.speakerOf(units[1]))
    }

    @Test
    fun `a conversation carries over to the next page`() {
        val carry = QuoteCarry()
        val first = listOf(
            sentence(0, "“Ready?” asked Mira.", pause = paragraphPauseMs),
            sentence(1, "“Yes,” said Arjun.", pause = paragraphPauseMs),
        )
        val next = listOf(sentence(0, "“Then go.”", page = 1, pause = paragraphPauseMs))

        carry.page(first)
        carry.page(next)

        assertEquals("Mira", carry.speakerOf(next.single())?.name)
    }

    @Test
    fun `a pronoun takes a name tagged earlier in its paragraph, and never an ungendered name from before`() {
        val carry = QuoteCarry()
        val units = listOf(
            sentence(0, "“Where were you?” asked Tom.", pause = paragraphPauseMs),
            sentence(1, "Mary looked away.", pause = 0),
            sentence(2, "“Out,” she said.", pause = paragraphPauseMs),
            sentence(3, "“Out,” said Asha.", pause = 0),
            sentence(4, "“With friends,” she added.", pause = paragraphPauseMs),
        )

        carry.page(units)

        assertEquals(null, carry.speakerOf(units[2])?.name)
        assertEquals(SpeakerGender.FEMALE, carry.speakerOf(units[2])?.gender)
        assertEquals("Asha", carry.speakerOf(units[4])?.name)
        assertEquals(SpeakerGender.FEMALE, carry.speakerOf(units[4])?.gender)
    }

    @Test
    fun `a speech over several paragraphs keeps its speaker, and a heading forgets the conversation`() {
        val carry = QuoteCarry()
        val units = listOf(
            sentence(0, "“Ready?” asked Mira.", pause = paragraphPauseMs),
            sentence(1, "“Yes,” said Arjun.", pause = paragraphPauseMs),
            sentence(2, "“It began long ago, in the hills.", pause = paragraphPauseMs),
            sentence(3, "“And it never ended.”", pause = paragraphPauseMs),
            sentence(4, "Chapter Two", kind = UnitKind.HEADING, pause = paragraphPauseMs),
            sentence(5, "“Who is there?”", pause = paragraphPauseMs),
        )

        carry.page(units)

        assertEquals("Mira", carry.speakerOf(units[2])?.name)
        assertEquals("Mira", carry.speakerOf(units[3])?.name)
        assertNull(carry.speakerOf(units[5]))
    }

    @Test
    fun `a paragraph after an open quote goes on with the speech only when it reopens the quote`() {
        val carry = QuoteCarry()
        val units = listOf(
            sentence(0, "“Ready?” asked Mira.", pause = paragraphPauseMs),
            sentence(1, "“Yes,” said Arjun.", pause = paragraphPauseMs),
            sentence(2, "“It began long ago, in the hills.", pause = paragraphPauseMs),
            sentence(3, "Arjun laughed. “Never in my life.”", pause = paragraphPauseMs),
        )

        carry.page(units)

        assertEquals("Mira", carry.speakerOf(units[2])?.name)
        assertEquals("Arjun", carry.speakerOf(units[3])?.name) // the next turn, not Mira's speech going on
        assertEquals(false, carry.startsInQuote(units[3]))
    }

    @Test
    fun `a pronoun that can be the last speaker again never teaches the other one its gender`() {
        val carry = QuoteCarry()
        val units = listOf(
            sentence(0, "“Where were you?” asked Tom.", pause = paragraphPauseMs),
            sentence(1, "“Out,” she said.", pause = paragraphPauseMs),
            sentence(2, "“With friends,” she added.", pause = paragraphPauseMs),
            sentence(3, "“Which friends?” Tom asked.", pause = paragraphPauseMs),
        )

        carry.page(units)

        assertEquals(SpeakerGender.FEMALE, carry.speakerOf(units[2])?.gender)
        assertNull(carry.speakerOf(units[2])?.name)
        assertEquals(SpeakerGender.UNKNOWN, carry.speakerOf(units[3])?.gender)
    }

    @Test
    fun `once a third speaker joins, untagged lines keep the dialogue voice`() {
        val carry = QuoteCarry()
        val units = listOf(
            sentence(0, "“Ready?” asked Ann.", pause = paragraphPauseMs),
            sentence(1, "“Ready,” said Tom.", pause = paragraphPauseMs),
            sentence(2, "“Me too,” said Raj.", pause = paragraphPauseMs),
            sentence(3, "“Then go.”", pause = paragraphPauseMs),
            sentence(4, "The door shut.", pause = paragraphPauseMs),
            sentence(5, "“Who was that?” asked Ann.", pause = paragraphPauseMs),
            sentence(6, "“Raj,” said Tom.", pause = paragraphPauseMs),
            sentence(7, "“Oh.”", pause = paragraphPauseMs),
        )

        carry.page(units)

        assertNull(carry.speakerOf(units[3]))
        assertEquals("Ann", carry.speakerOf(units[7])?.name) // a new run: two speakers alternate again
    }

    @Test
    fun `a page that goes on with the sentence the page before broke off keeps its quote open`() {
        val carry = QuoteCarry()
        val first = listOf(sentence(0, "She leaned in and said, “The key is under the"))
        val next = listOf(sentence(0, "third stone by the gate.” Then she left.", page = 1))
        val fresh = listOf(sentence(0, "third stone by the gate.", page = 2))

        carry.page(first)
        carry.page(next, continuesPreviousPage = true)
        carry.page(fresh)

        assertEquals(true, carry.startsInQuote(next.single()))
        assertEquals(false, carry.startsInQuote(fresh.single()))
    }

}
