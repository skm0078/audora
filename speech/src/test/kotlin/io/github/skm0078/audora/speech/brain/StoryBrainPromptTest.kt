// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/brain/StoryBrainPromptTest.kt
// Purpose: The story brain's question (ADR 2026-10-25-story-brain): numbered lines, spoken ones marked, context, and a grammar that asks
//          for exactly the spoken lines in order.

package io.github.skm0078.audora.speech.brain

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.speech.sherpa.SpeakerGender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryBrainPromptTest {
    private val lines = listOf(
        BrainLine("Mira came home late.", spoken = false, paragraph = 0),
        BrainLine("“Where were you?” Tom asked.", spoken = true, paragraph = 1),
        BrainLine("He had waited up.", spoken = false, paragraph = 1),
        BrainLine("“At the\nstation,” she said.", spoken = true, paragraph = 2),
    )

    @Test
    fun `a passage with no speech asks nothing`() {
        assertNull(StoryBrainPrompt.build(Lang.EN, lines.map { it.copy(spoken = false) }))
    }

    @Test
    fun `lines are numbered with spoken ones marked and paragraphs apart`() {
        val request = StoryBrainPrompt.build(Lang.EN, lines)!!
        val expected = "1. Mira came home late.\n\n2* “Where were you?” Tom asked.\n3. He had waited up.\n\n" +
            "4* “At the station,” she said."
        assertTrue(request.prompt, expected in request.prompt)
        assertTrue("English" in request.prompt)
        assertEquals(listOf(2, 4), request.numbers)
        // The answer starts at once: thinking is left empty.
        assertTrue(request.prompt.endsWith("<think>\n\n</think>\n\n"))
        assertFalse(Regex("""\{(language|context|sentences)}""").containsMatchIn(request.prompt))
    }

    @Test
    fun `the grammar asks for each spoken line by number in order, then the cast`() {
        val grammar = StoryBrainPrompt.build(Lang.HI, lines)!!.grammar
        val root = grammar.lineSequence().first()
        assertEquals("root ::= \"2|\" who \"|\" how \"\\n\" \"4|\" who \"|\" how \"\\n\" cast", root)
        StoryBrainPrompt.FEELINGS.forEach { assertTrue(it, "\"$it\"" in grammar) }
        assertTrue(grammar.contains("cast ::= \"cast: \""))
    }

    @Test
    fun `the cast met before and the sentences just before are given, unknown genders left out`() {
        val cast =
            mapOf("Tom" to SpeakerGender.MALE, "Mira" to SpeakerGender.FEMALE, "the man" to SpeakerGender.UNKNOWN)
        val prompt = StoryBrainPrompt.build(Lang.BN, lines, listOf("It was late."), cast)!!.prompt
        assertTrue(prompt, "Characters met so far: Tom=M, Mira=F\n" in prompt)
        assertFalse("the man" in prompt)
        assertTrue("The story just before (no answers needed):\nIt was late.\n" in prompt)
        assertTrue("Bengali" in prompt)
    }

    @Test
    fun `the answer budget grows with the spoken lines`() {
        val one = StoryBrainPrompt.build(Lang.EN, lines.take(2))!!
        val two = StoryBrainPrompt.build(Lang.EN, lines)!!
        assertTrue(two.maxTokens > one.maxTokens)
    }
}
