// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/voice/CharacterCastTest.kt
// Purpose: Characters get distinct voices of their gender from the narrator's model, kept per book (plan item 63).

package io.github.skm0078.audora.playback.voice

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.speech.route.Speaker
import io.github.skm0078.audora.speech.sherpa.CastSpeaker
import io.github.skm0078.audora.speech.sherpa.CastSpeakers
import io.github.skm0078.audora.speech.sherpa.SpeakerGender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterCastTest {
    private class MemoryFiles : CharacterFiles {
        val books = HashMap<BookId, Map<String, String>>()

        override fun load(book: BookId): Map<String, String> = books[book].orEmpty()

        override fun save(book: BookId, cast: Map<String, String>) {
            books[book] = cast
        }
    }

    private val files = MemoryFiles()

    private fun cast(narrator: VoiceId? = NARRATOR, taken: Set<VoiceId> = emptySet()) = CharacterCast(
        narrator = { narrator },
        speakers = { voice -> if (voice == NARRATOR) SPEAKERS else emptyList() },
        taken = { taken },
        files = files,
    ).also { it.open(BOOK) }

    @Test
    fun `characters of one gender get different voices of that gender, each kept`() {
        val cast = cast()

        val tom = cast.voiceFor(Lang.EN, Speaker("Tom", SpeakerGender.MALE))
        val ravi = cast.voiceFor(Lang.EN, Speaker("Ravi", SpeakerGender.MALE))

        val males = SPEAKERS.filter {
            it.gender == SpeakerGender.MALE
        }.map { CastSpeakers.variant(NARRATOR, it.speakerId) }
        assertTrue(tom in males && ravi in males)
        assertNotEquals(tom, ravi)
        assertEquals(tom, cast.voiceFor(Lang.EN, Speaker("Tom", SpeakerGender.MALE)))
        assertEquals(tom, cast.voiceFor(Lang.EN, Speaker("Tom", SpeakerGender.UNKNOWN)))
    }

    @Test
    fun `an unknown gender waits, and a one-speaker narrator has no cast`() {
        assertNull(cast().voiceFor(Lang.EN, Speaker("Sam", SpeakerGender.UNKNOWN)))
        assertNull(cast(narrator = OTHER).voiceFor(Lang.EN, Speaker("Mira", SpeakerGender.FEMALE)))
    }

    @Test
    fun `a book keeps its cast when opened again, and dialogue voices are never given out`() {
        val dialogue = CastSpeakers.variant(NARRATOR, FEMALE_A)
        val first = cast(taken = setOf(dialogue)).voiceFor(Lang.EN, Speaker("Mira", SpeakerGender.FEMALE))

        assertEquals(CastSpeakers.variant(NARRATOR, FEMALE_B), first)
        assertEquals(first, cast(taken = setOf(dialogue)).voiceFor(Lang.EN, Speaker("Mira", SpeakerGender.FEMALE)))
    }

    @Test
    fun `a character cast on a wrong guess of gender is cast again when the gender is told`() {
        val cast = cast()
        val guessed = cast.voiceFor(Lang.EN, Speaker("Tom", SpeakerGender.FEMALE))
        val told = cast.voiceFor(Lang.EN, Speaker("Tom", SpeakerGender.MALE))

        assertTrue(guessed in listOf(FEMALE_A, FEMALE_B).map { CastSpeakers.variant(NARRATOR, it) })
        assertTrue(told in listOf(11, 12).map { CastSpeakers.variant(NARRATOR, it) })
        assertEquals(told, cast().voiceFor(Lang.EN, Speaker("Tom", SpeakerGender.UNKNOWN)))
    }

    @Test
    fun `a renderer's snapshot from before another book opened gives no voice and keeps nothing`() {
        val cast = cast()
        val before = cast.current()
        cast.open(BookId("book-2"))

        assertNull(before.voiceFor(Lang.EN, Speaker("Tom", SpeakerGender.MALE)))
        assertTrue(files.books[BookId("book-2")].isNullOrEmpty())
        assertTrue(cast.current().voiceFor(Lang.EN, Speaker("Tom", SpeakerGender.MALE)) != null)
    }

    @Test
    fun `the cast is kept per narrator model, so switching away and back finds it`() {
        val first = cast().voiceFor(Lang.EN, Speaker("Mira", SpeakerGender.FEMALE))
        assertNull(cast(narrator = OTHER).voiceFor(Lang.EN, Speaker("Mira", SpeakerGender.FEMALE)))
        assertEquals(first, cast().voiceFor(Lang.EN, Speaker("Mira", SpeakerGender.FEMALE)))
    }

    private companion object {
        const val FEMALE_A = 1
        const val FEMALE_B = 2
        val BOOK = BookId("book-1")
        val NARRATOR = VoiceId(EngineId.SHERPA, "kokoro-en")
        val OTHER = VoiceId(EngineId.SHERPA, "piper-hi")
        val SPEAKERS = listOf(
            CastSpeaker(FEMALE_A, "A", SpeakerGender.FEMALE),
            CastSpeaker(FEMALE_B, "B", SpeakerGender.FEMALE),
            CastSpeaker(11, "C", SpeakerGender.MALE),
            CastSpeaker(12, "D", SpeakerGender.MALE),
        )
    }
}
