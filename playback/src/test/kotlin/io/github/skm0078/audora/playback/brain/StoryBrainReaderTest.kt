// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/brain/StoryBrainReaderTest.kt
// Purpose: The story brain reader asks about spoken lines only, reads ahead, keeps and reuses its notes, and with no
//          model answers nothing, so reading is exactly as before (ADR 2026-10-25-story-brain).

package io.github.skm0078.audora.playback.brain

import io.github.skm0078.audora.core.config.StoryBrainConfig
import io.github.skm0078.audora.core.model.Feeling
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.UnitKind
import io.github.skm0078.audora.speech.brain.BrainModel
import io.github.skm0078.audora.speech.sherpa.SpeakerGender
import java.io.File
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StoryBrainReaderTest {
    @get:Rule
    val temp = TemporaryFolder()

    /** Runs each step at once, on the caller's thread. */
    private class DirectExecutor : AbstractExecutorService() {
        private var shut = false

        override fun execute(command: Runnable) = command.run()

        override fun shutdown() {
            shut = true
        }

        override fun shutdownNow(): List<Runnable> = emptyList<Runnable>().also { shut = true }

        override fun isShutdown() = shut

        override fun isTerminated() = shut

        override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
    }

    /** Answers every spoken line with [speaker] and [feeling]; records each prompt. */
    private class FakeModel(var speaker: String = "Tom", var feeling: String = "angry") : BrainModel {
        val prompts = ArrayList<String>()
        var closed = false

        override fun answer(prompt: String, grammar: String, maxTokens: Int): String {
            prompts += prompt
            val numbers = NUMBER.findAll(grammar.lineSequence().first()).map { it.groupValues[1] }
            return numbers.joinToString("") { "$it|$speaker|$feeling\n" } + "cast: Tom=M, Mira=F\n"
        }

        override fun cancel() = Unit

        override fun close() {
            closed = true
        }

        companion object {
            val NUMBER = Regex(""""(\d+)\|"""")
        }
    }

    private val fake = FakeModel()
    private var installed = true
    private var loads = 0
    private var wanted = true
    private val store by lazy { BrainNotesStore(File(temp.root, "notes"), "test/1") }

    private fun reader(config: StoryBrainConfig = StoryBrainConfig(pagesAhead = 1)) = StoryBrainReader(
        config = config,
        source = BrainSource(load = { fake.also { loads++ } }, installed = { installed }),
        store = store,
        wanted = { wanted },
        executor = DirectExecutor(),
    )

    private fun sentence(page: Int, index: Int, text: String, pause: Int = 0, kind: UnitKind = UnitKind.BODY) =
        ReadingUnit(page, index, 0, text.length, text, Lang.EN, pause, null, kind)

    private fun page(page: Int) = listOf(
        sentence(page, 0, "Mira came home late.", pause = PARAGRAPH),
        sentence(page, 1, "“Where were you?” Tom asked.", pause = PARAGRAPH),
        sentence(page, 2, "“At the station.", pause = 0),
        sentence(page, 3, "The train broke down.”", pause = PARAGRAPH),
        sentence(page, 4, "She sat down.", pause = PARAGRAPH),
    )

    private val book = (0..5).associateWith { page(it) }

    @Test
    fun `spoken lines get the brain's speaker and feeling, narration gets nothing`() {
        val reader = reader()
        reader.open("book", book::get)
        reader.page(book.getValue(0), scanned = false)
        val units = book.getValue(0)
        assertNull(reader.noteOf(units[0]))
        assertNull(reader.noteOf(units[4]))
        assertEquals("Tom", reader.speakerOf(units[1])?.name)
        assertEquals(SpeakerGender.MALE, reader.speakerOf(units[1])?.gender)
        assertEquals(Feeling.ANGRY, reader.feelingOf(units[1]))
        // Inside a quote its paragraph opened: still spoken.
        assertEquals(Feeling.ANGRY, reader.feelingOf(units[3]))
        assertTrue(fake.prompts.first().contains("3* “At the station.\n4* The train broke down.”"))
        assertTrue(fake.prompts.first().contains("1. Mira came home late."))
    }

    @Test
    fun `a name the line calls out to is not its speaker, but the feeling stands`() {
        val reader = reader()
        reader.open("book") { null }
        val units = listOf(sentence(0, 0, "“I'm sorry, Tom.”", pause = PARAGRAPH), sentence(0, 1, "She left."))
        reader.page(units, scanned = false)
        assertNull(reader.speakerOf(units[0]))
        assertEquals(Feeling.ANGRY, reader.feelingOf(units[0]))
    }

    @Test
    fun `a neutral answer is told apart from no answer`() {
        fake.feeling = "neutral"
        val reader = reader()
        reader.open("book", book::get)
        reader.page(book.getValue(0), scanned = false)
        assertEquals(Feeling.NEUTRAL, reader.feelingOf(book.getValue(0)[1]))
        assertNull(reader.feelingOf(book.getValue(0)[0]))
    }

    @Test
    fun `it reads ahead of the page playing, and each page once`() {
        val reader = reader(StoryBrainConfig(pagesAhead = 2))
        reader.open("book", book::get)
        reader.page(book.getValue(0), scanned = false)
        assertEquals(3, fake.prompts.size)
        assertEquals(Feeling.ANGRY, reader.feelingOf(book.getValue(2)[1]))
        reader.page(book.getValue(1), scanned = false)
        assertEquals(4, fake.prompts.size)
        // The page before is given as context to the next one.
        assertTrue(fake.prompts.last().contains("The story just before"))
        // The cast carries over: the speakers only, not every name in the cast line.
        assertTrue(fake.prompts.last(), fake.prompts.last().contains("Characters met so far: Tom=M\n"))
    }

    @Test
    fun `a scanned page is not read ahead of`() {
        val reader = reader()
        reader.open("book", book::get)
        reader.page(book.getValue(0), scanned = true)
        assertEquals(1, fake.prompts.size)
    }

    @Test
    fun `notes are kept on disk, so a book opened again is not asked about again`() {
        reader().apply {
            open("book", book::get)
            page(book.getValue(0), scanned = false)
        }
        val asked = fake.prompts.size
        val again = reader()
        again.open("book", book::get)
        again.page(book.getValue(0), scanned = false)
        assertEquals(asked, fake.prompts.size)
        assertEquals("Tom", again.speakerOf(book.getValue(0)[1])?.name)
    }

    @Test
    fun `a note is never applied to a sentence read differently`() {
        val reader = reader()
        reader.open("book", book::get)
        reader.page(book.getValue(0), scanned = false)
        val changed = book.getValue(0)[1].copy(text = "“Where have you been?” Tom asked.")
        assertNull(reader.noteOf(changed))
    }

    @Test
    fun `with no model, or not wanted, or a narration page, nothing is asked and nothing is noted`() {
        installed = false
        val reader = reader()
        reader.open("book", book::get)
        reader.page(book.getValue(0), scanned = false)
        assertEquals(0, loads)
        assertNull(reader.noteOf(book.getValue(0)[1]))

        installed = true
        wanted = false
        reader.page(book.getValue(1), scanned = false)
        assertEquals(0, loads)

        wanted = true
        val narration = listOf(sentence(4, 0, "It rained.", pause = PARAGRAPH), sentence(4, 1, "Nobody came."))
        reader().apply {
            open("other", { null })
            page(narration, scanned = false)
        }
        assertTrue(fake.prompts.isEmpty())
    }

    @Test
    fun `a model deleted while loaded is let go, and closing the reader frees it`() {
        val reader = reader(StoryBrainConfig(pagesAhead = 0))
        reader.open("book", book::get)
        reader.page(book.getValue(0), scanned = false)
        assertEquals(1, loads)
        installed = false
        reader.page(book.getValue(1), scanned = false)
        assertTrue(fake.closed)
        fake.closed = false
        installed = true
        reader.open("book", book::get)
        reader.page(book.getValue(2), scanned = false)
        assertEquals(2, loads)
        reader.close()
        assertTrue(fake.closed)
    }

    private companion object {
        const val PARAGRAPH = 900
    }
}
