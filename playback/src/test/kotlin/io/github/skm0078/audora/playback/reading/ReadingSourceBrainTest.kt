// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/reading/ReadingSourceBrainTest.kt
// Purpose: With no story brain the renderer reads speakers and feelings exactly as before; with one, its calls win
//          where it made them and the rules fill the rest (ADR story-brain).

package io.github.skm0078.audora.playback.reading

import io.github.skm0078.audora.core.config.StoryBrainConfig
import io.github.skm0078.audora.core.model.Feeling
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.playback.brain.BrainSource
import io.github.skm0078.audora.playback.brain.StoryBrainReader
import io.github.skm0078.audora.playback.unit
import io.github.skm0078.audora.speech.brain.BrainModel
import io.github.skm0078.audora.speech.normalize.PronunciationFixer
import io.github.skm0078.audora.speech.sherpa.SpeakerGender
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingSourceBrainTest {
    private val paragraph = 900
    private val units: List<ReadingUnit> = listOf(
        unit(0, 0).copy(text = "“Who is there?”", pauseAfterMs = paragraph),
        unit(0, 1).copy(text = "“Only me,” said Tom.", pauseAfterMs = paragraph),
        unit(0, 2).copy(text = "She sat down.", pauseAfterMs = paragraph),
    )

    /** Calls the first two lines Mira's, the first scared. */
    private class TwoLineModel : BrainModel {
        override fun answer(prompt: String, grammar: String, maxTokens: Int) =
            "1|Mira|scared\n2|Mira|neutral\ncast: Mira=F\n"

        override fun cancel() = Unit

        override fun close() = Unit
    }

    private fun fixer() = PronunciationFixer(emptyList())

    @Test
    fun `with no brain, speakers and feelings are the rules' alone`() {
        val plain = ReadingSource()
        plain.page(units, continuesPreviousPage = false)
        val book = plain.book(::fixer)
        units.forEach { unit ->
            assertEquals(plain.quotes.speakerOf(unit), book.speakerOf(unit))
            assertNull(book.judgedFeeling(unit))
        }
        assertEquals("Tom", book.speakerOf(units[1])?.name)
    }

    @Test
    fun `a tagged speaker with a known gender stays the rules', the brain fills the rest`() {
        val executor: ExecutorService = Executors.newSingleThreadExecutor()
        val brain = StoryBrainReader(
            config = StoryBrainConfig(pagesAhead = 0),
            source = BrainSource(load = { TwoLineModel() }, installed = { true }),
            store = null,
            wanted = { true },
            executor = executor,
        )
        val source = ReadingSource(brain = brain)
        brain.open("book") { null }
        source.page(units, continuesPreviousPage = false)
        // The brain works on its own thread: wait for it.
        executor.submit {}.get(WAIT_SECONDS, TimeUnit.SECONDS)
        val book = source.book(::fixer)
        // No tag: the brain's call.
        assertEquals("Mira", book.speakerOf(units[0])?.name)
        assertEquals(Feeling.SCARED, book.judgedFeeling(units[0]))
        // "said Tom": the rules know Tom and his gender, so the brain's "Mira" does not replace him.
        assertEquals("Tom", book.speakerOf(units[1])?.name)
        assertEquals(Feeling.NEUTRAL, book.judgedFeeling(units[1]))
        assertNull(book.judgedFeeling(units[2]))
        brain.close()
    }

    /** Calls the line নীলিমা's and, against her name's ending, a man's. */
    private class NamedModel : BrainModel {
        override fun answer(prompt: String, grammar: String, maxTokens: Int) = "1|নীলিমা|neutral\ncast: নীলিমা=M\n"

        override fun cancel() = Unit

        override fun close() = Unit
    }

    @Test
    fun `a gender the rules only guessed from a name's ending yields to the brain's`() {
        val executor: ExecutorService = Executors.newSingleThreadExecutor()
        val brain = StoryBrainReader(
            config = StoryBrainConfig(pagesAhead = 0),
            source = BrainSource(load = { NamedModel() }, installed = { true }),
            store = null,
            wanted = { true },
            executor = executor,
        )
        val line = unit(0, 0, Lang.BN).copy(text = "“কাল যাব,” নীলিমা বলল।", pauseAfterMs = paragraph)
        val rules = ReadingSource().apply { page(listOf(line), continuesPreviousPage = false) }
        val source = ReadingSource(brain = brain)
        brain.open("book") { null }
        source.page(listOf(line), continuesPreviousPage = false)
        executor.submit {}.get(WAIT_SECONDS, TimeUnit.SECONDS)

        assertEquals(SpeakerGender.FEMALE, rules.book(::fixer).speakerOf(line)?.gender)
        assertEquals(SpeakerGender.MALE, source.book(::fixer).speakerOf(line)?.gender)
        brain.close()
    }

    private companion object {
        const val WAIT_SECONDS = 10L
    }
}
