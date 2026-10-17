// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/studio/StudioRunTest.kt
// Purpose: Studio mode renders ahead up to its target, reuses what is ready, and follows playback's failure rules.

package io.github.skm0078.audora.playback.studio

import io.github.skm0078.audora.core.config.StudioConfig
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.page
import io.github.skm0078.audora.playback.pcm
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioRunTest {
    private val root: File = Files.createTempDirectory("studio").toFile()
    private val store = StudioStore(root)
    private val key = StudioKey(BookId("book"), StudioSignature(1, 0L, SpeechStyle(), emptyMap(), fixes = ""))

    /** One hour per sentence at the test rate (1 kHz) would be huge; one hour = 3,600,000 samples. */
    private val config = StudioConfig(hoursAhead = 1, blockSamples = 256)
    private val sentenceSamples = 600_000 // 10 minutes at 1 kHz.

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    private class FakeBook(override val pageCount: Int, private val failing: Map<Int, StudioPagePlays> = emptyMap()) :
        StudioBook {
        override fun plays(page: Int): StudioPagePlays = failing[page] ?: StudioPagePlays.Units(page(page, 2))
    }

    private class CountingVoice(private val samples: Int, private val failAll: Boolean = false) : StudioVoice {
        val rendered = ArrayList<ReadingUnit>()

        override fun render(unit: ReadingUnit): Outcome<io.github.skm0078.audora.speech.audio.PcmAudio, String> {
            rendered.add(unit)
            return if (failAll) Outcome.Failure("broken") else Outcome.Success(pcm(samples))
        }
    }

    private fun run(voice: StudioVoice, book: StudioBook, from: Int = 0, gate: StudioGate = StudioGate {}) =
        StudioRun(voice, store, gate, config).run(key, book, from)

    @Test
    fun `renders from the listener's page until the hours ahead are ready`() {
        val voice = CountingVoice(sentenceSamples)
        val result = run(voice, FakeBook(pageCount = 10), from = 2)
        val summary = (result as Outcome.Success).value
        // Two sentences of 10 minutes per page: three pages make an hour.
        assertEquals(3, summary.renderedPages)
        assertEquals(3_600_000L, summary.readyMs)
        assertEquals(false, summary.endOfBook)
        assertEquals(listOf(2, 3, 4), voice.rendered.map { it.page }.distinct())
        assertNotNull(store.page(key, 4))
    }

    @Test
    fun `a second run keeps ready pages and renders nothing again`() {
        run(CountingVoice(sentenceSamples), FakeBook(pageCount = 10))
        val again = CountingVoice(sentenceSamples)
        val summary = (run(again, FakeBook(pageCount = 10)) as Outcome.Success).value
        assertTrue(again.rendered.isEmpty())
        assertEquals(0, summary.renderedPages)
        assertEquals(3_600_000L, summary.readyMs)
    }

    @Test
    fun `stops at the end of the book`() {
        val summary = (run(CountingVoice(100), FakeBook(pageCount = 2)) as Outcome.Success).value
        assertTrue(summary.endOfBook)
        assertEquals(2, summary.renderedPages)
    }

    @Test
    fun `a broken voice stops the run`() {
        val result = run(CountingVoice(100, failAll = true), FakeBook(pageCount = 5))
        assertTrue((result as Outcome.Failure).error is StudioError.VoiceFailed)
    }

    @Test
    fun `an unextractable page is skipped and an unreadable book stops`() {
        val book = FakeBook(4, mapOf(1 to StudioPagePlays.Skipped, 3 to StudioPagePlays.BookUnreadable))
        val result = run(CountingVoice(100), book)
        assertEquals(StudioError.BookUnreadable(3), (result as Outcome.Failure).error)
        assertNotNull(store.page(key, 2))
    }

    @Test
    fun `asks the gate before every sentence`() {
        var asked = 0
        run(CountingVoice(100), FakeBook(pageCount = 2), gate = { asked++ })
        assertEquals(4, asked)
    }

    @Test
    fun `stops when the phone is nearly full`() {
        val result = StudioRun(CountingVoice(100), store, {}, config, freeBytes = { 0L }).run(key, FakeBook(3), 0)
        assertEquals(StudioError.NoSpace, (result as Outcome.Failure).error)
    }
}
