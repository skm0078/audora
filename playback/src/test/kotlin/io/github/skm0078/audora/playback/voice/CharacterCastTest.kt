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
        var loads = 0
        var saves = 0

        override fun load(book: BookId): Map<String, String> = books[book].orEmpty().also { loads++ }

        override fun save(book: BookId, cast: Map<String, String>) {
            saves++
            books[book] = cast
        }
    }

    private val files = MemoryFiles()
    private val casts = CharacterCasts(files)

    private fun cast(narrator: VoiceId? = NARRATOR, taken: Set<VoiceId> = emptySet()) = CharacterCast(
        narrator = { narrator },
        speakers = { voice -> if (voice == NARRATOR) SPEAKERS else emptyList() },
        taken = { taken },
        casts = casts,
    ).also { it.open(BOOK) }

    @Test
    fun `a narrator that is another speaker of its model casts from that model and never reads a character`() {
        val narrator = CastSpeakers.variant(NARRATOR, 11)
        val cast = cast(narrator = narrator)

        val tom = cast.voiceFor(Lang.EN, Speaker("Tom", SpeakerGender.MALE))

        assertEquals(CastSpeakers.variant(NARRATOR, 12), tom)
    }

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

    @Test
    fun `a character first cast by another reader of the book, the export, is kept by both and saved once`() {
        val playback = cast()
        val export = cast()

        val mira = export.voiceFor(Lang.EN, Speaker("Mira", SpeakerGender.FEMALE))
        val tom = playback.voiceFor(Lang.EN, Speaker("Tom", SpeakerGender.MALE))

        assertEquals(mira, playback.voiceFor(Lang.EN, Speaker("Mira", SpeakerGender.UNKNOWN)))
        val saved = files.books.getValue(BOOK)
        assertEquals(setOf(mira!!.name, tom!!.name), saved.values.toSet())
        assertEquals(2, saved.size)
    }

    @Test
    fun `a new character saves once, a voice already given saves nothing, and two casts of a book load it once`() {
        val playback = cast()
        val export = cast()
        assertEquals(1, files.loads)

        playback.voiceFor(Lang.EN, Speaker("Mira", SpeakerGender.FEMALE))
        export.voiceFor(Lang.EN, Speaker("Tom", SpeakerGender.MALE))
        assertEquals(2, files.saves)

        playback.voiceFor(Lang.EN, Speaker("Tom", SpeakerGender.UNKNOWN))
        export.voiceFor(Lang.EN, Speaker("Mira", SpeakerGender.FEMALE))
        playback.voiceFor(Lang.EN, Speaker("Sam", SpeakerGender.UNKNOWN))
        assertEquals(2, files.saves)
        assertEquals(1, files.loads)
    }

    @Test
    fun `two threads casting at once keep every character`() {
        val playback = cast()
        val export = cast()
        val names = (1..THREAD_CHARACTERS).map { "Character $it" }
        val threads = listOf(playback, export).mapIndexed { i, reader ->
            val mine = names.filterIndexed { n, _ -> n % 2 == i }
            Thread { mine.forEach { reader.voiceFor(Lang.EN, Speaker(it, SpeakerGender.MALE)) } }
        }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)

        assertEquals(THREAD_CHARACTERS, files.books.getValue(BOOK).size)
        assertEquals(THREAD_CHARACTERS, files.saves)
    }

    @Test
    fun `a book opened in a new process reads its file once`() {
        files.books[BOOK] = mapOf(listOf(NARRATOR.name, Lang.EN.name, "mira").joinToString("|") to "kokoro-en#2")
        val fresh = CharacterCasts(files)
        val first = CharacterCast({ NARRATOR }, { SPEAKERS }, casts = fresh).also { it.open(BOOK) }

        assertEquals(
            CastSpeakers.variant(NARRATOR, FEMALE_B),
            first.voiceFor(Lang.EN, Speaker("Mira", SpeakerGender.FEMALE)),
        )
    }

    private companion object {
        const val FEMALE_A = 1
        const val FEMALE_B = 2
        const val THREAD_CHARACTERS = 40
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
