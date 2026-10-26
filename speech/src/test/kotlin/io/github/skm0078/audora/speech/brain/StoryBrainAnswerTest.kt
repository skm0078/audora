// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/brain/StoryBrainAnswerTest.kt
// Purpose: The story brain's answer read into speakers with genders and feelings (ADR 2026-10-25-story-brain); a broken row is left out.

package io.github.skm0078.audora.speech.brain

import io.github.skm0078.audora.core.model.Feeling
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.speech.sherpa.SpeakerGender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoryBrainAnswerTest {
    private val request = StoryBrainPrompt.build(
        Lang.EN,
        listOf(
            BrainLine("“Where were you?” Tom asked.", spoken = true, paragraph = 0),
            BrainLine("“At the station.”", spoken = true, paragraph = 1),
            BrainLine("She sat down.", spoken = false, paragraph = 1),
            BrainLine("“Get out!”", spoken = true, paragraph = 2),
        ),
    )!!

    @Test
    fun `each row gives a speaker with the cast's gender and a feeling`() {
        val parsed = StoryBrainAnswer.parse(
            "1|Tom|angry\n2|Mira|neutral\n4|the stranger|joy\ncast: Tom=M, Mira=F\n",
            request,
        )
        assertEquals(setOf(1, 2, 4), parsed.notes.keys)
        assertEquals("Tom", parsed.notes.getValue(1).speaker.name)
        assertEquals(SpeakerGender.MALE, parsed.notes.getValue(1).speaker.gender)
        assertEquals(Feeling.ANGRY, parsed.notes.getValue(1).feeling)
        // Neutral is no feeling at all; joy is the app's lively.
        assertNull(parsed.notes.getValue(2).feeling)
        assertEquals(SpeakerGender.FEMALE, parsed.notes.getValue(2).speaker.gender)
        assertEquals(Feeling.LIVELY, parsed.notes.getValue(4).feeling)
        assertEquals(SpeakerGender.UNKNOWN, parsed.notes.getValue(4).speaker.gender)
    }

    @Test
    fun `the name lists beat the model on a role, and padding in the cast line is not carried on`() {
        val answer = "1|Grandma|sad\n2|Tom|neutral\n4|Tom|loud\ncast: Grandma=M, Tom=M, Tom=F, the garden=F\n"
        val parsed = StoryBrainAnswer.parse(answer, request)
        assertEquals(SpeakerGender.FEMALE, parsed.notes.getValue(1).speaker.gender)
        // The first call on a name stands.
        assertEquals(SpeakerGender.MALE, parsed.notes.getValue(4).speaker.gender)
        assertEquals(setOf("Grandma", "Tom"), parsed.cast.keys)
    }

    @Test
    fun `a gender comes from the cast met earlier, by the whole name or a word of it`() {
        val known = mapOf("Mr Hale" to SpeakerGender.MALE, "the old woman" to SpeakerGender.FEMALE)
        val parsed = StoryBrainAnswer.parse("1|Hale|loud\n2|old woman|soft\n4|Hale|sad\ncast: Hale=M\n", request, known)
        assertEquals(SpeakerGender.MALE, parsed.notes.getValue(1).speaker.gender)
        assertEquals(SpeakerGender.FEMALE, parsed.notes.getValue(2).speaker.gender)
        assertEquals(SpeakerGender.MALE, parsed.cast["Hale"])
        assertEquals(SpeakerGender.MALE, parsed.cast["Mr Hale"])
    }

    @Test
    fun `rows that do not read, or name a line not asked about, are left out`() {
        val parsed = StoryBrainAnswer.parse("1|Tom|furious\n3|Mira|sad\n2||neutral\n4|Tom", request)
        assertEquals(emptyMap<Int, BrainNote>(), parsed.notes)
    }

    @Test
    fun `an answer cut at its cap keeps the rows it finished`() {
        val parsed = StoryBrainAnswer.parse("1|Tom|scared\n2|Mi", request)
        assertEquals(setOf(1), parsed.notes.keys)
        assertEquals(Feeling.SCARED, parsed.notes.getValue(1).feeling)
    }
}
