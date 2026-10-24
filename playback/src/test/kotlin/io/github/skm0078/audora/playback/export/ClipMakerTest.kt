// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/export/ClipMakerTest.kt
// Purpose: A clip runs from the picked sentence to its paragraph's end, within the configured length.

package io.github.skm0078.audora.playback.export

import io.github.skm0078.audora.core.config.ClipConfig
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.UnitKind
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.pcm
import io.github.skm0078.audora.playback.queue.SentenceRenderer
import io.github.skm0078.audora.playback.queue.UnitKey
import io.github.skm0078.audora.playback.unit
import io.github.skm0078.audora.speech.SpeechError
import org.junit.Assert.assertEquals
import org.junit.Test

class ClipMakerTest {
    /** Each sentence renders to 2 s at the test rate (1 kHz). */
    private val renderer = SentenceRenderer { Outcome.Success(pcm(2_000)) }
    private val config =
        ClipConfig(minMs = 5_000, maxMs = 9_000, paragraphPauseMs = 400, maxPages = 2, passageMaxMs = 9_000)

    /** Page [page] with [count] sentences; those in [paragraphEnds] end a paragraph. */
    private fun page(page: Int, count: Int, paragraphEnds: Set<Int> = emptySet()): List<ReadingUnit> =
        List(count) { i -> unit(page, i).copy(pauseAfterMs = if (i in paragraphEnds) 400 else 0) }

    private fun make(
        pages: Map<Int, List<ReadingUnit>?>,
        start: UnitKey,
        plays: (List<ReadingUnit>) -> List<ReadingUnit> = { it },
        render: SentenceRenderer = renderer,
        passage: ClipPassage? = null,
    ) = ClipMaker(render, config).make(start, pages.size, pageUnits = { pages[it] }, plays = plays, passage = passage)

    private fun Outcome<Clip, ClipError>.keys() = (this as Outcome.Success).value.units.map { UnitKey.of(it) }

    @Test
    fun `ends at the first paragraph end once long enough`() {
        val clip = make(mapOf(0 to page(0, 8, paragraphEnds = setOf(1, 3))), UnitKey(0, 1))
        // Sentence 1 ends a paragraph at 2 s (too short), sentence 3 ends one at 6 s.
        assertEquals(listOf(UnitKey(0, 1), UnitKey(0, 2), UnitKey(0, 3)), clip.keys())
        assertEquals(6_000L, (clip as Outcome.Success).value.audio.durationMs)
    }

    @Test
    fun `stops before the sentence that would pass the maximum`() {
        val clip = make(mapOf(0 to page(0, 8)), UnitKey(0, 0))
        assertEquals(4, clip.keys().size) // 8 s; a fifth sentence would make 10 s.
    }

    @Test
    fun `carries on to the next page and stops before a heading`() {
        val next = page(1, 3).toMutableList().apply { this[1] = this[1].copy(kind = UnitKind.HEADING) }
        val clip = make(mapOf(0 to page(0, 3), 1 to next), UnitKey(0, 1))
        assertEquals(listOf(UnitKey(0, 1), UnitKey(0, 2), UnitKey(1, 0)), clip.keys())
    }

    @Test
    fun `starts at the next played sentence when the picked one is not read`() {
        val clip = make(
            mapOf(0 to page(0, 4, paragraphEnds = setOf(3))),
            UnitKey(0, 1),
            plays = { units -> units.filter { it.index != 1 } },
        )
        assertEquals(UnitKey(0, 2), clip.keys().first())
    }

    @Test
    fun `a passage runs from its first sentence to its last, past paragraph ends and headings`() {
        val next = page(1, 4, paragraphEnds = setOf(0)).toMutableList()
            .apply { this[1] = this[1].copy(kind = UnitKind.HEADING) }
        val long = config.copy(passageMaxMs = 60_000)
        // Page 0 from the sentence holding offset 15 (sentence 1); page 1 up to the one starting before offset 25.
        val clip = ClipMaker(renderer, long).make(
            UnitKey(0, 0),
            pageCount = 3,
            pageUnits = { mapOf(0 to page(0, 3, paragraphEnds = setOf(1)), 1 to next, 2 to page(2, 2))[it] },
            plays = { it },
            passage = ClipPassage(startChar = 15, endPage = 1, endChar = 25),
        )
        assertEquals(
            listOf(UnitKey(0, 1), UnitKey(0, 2), UnitKey(1, 0), UnitKey(1, 1), UnitKey(1, 2)),
            clip.keys(),
        )
    }

    @Test
    fun `a passage stops before the sentence that would pass its maximum`() {
        val clip = make(mapOf(0 to page(0, 8)), UnitKey(0, 0), passage = ClipPassage(0, endPage = 0, endChar = 80))
        assertEquals(4, clip.keys().size) // passageMaxMs is 9 s here too: a fifth sentence would make 10 s.
    }

    @Test
    fun `reports a failing voice, an unreadable page and nothing to read`() {
        val error = SpeechError.EngineUnavailable(EngineId.SHERPA, "gone")
        val broken = SentenceRenderer { Outcome.Failure(error) }
        assertEquals(
            ClipError.VoiceFailed(error.message),
            (make(mapOf(0 to page(0, 2)), UnitKey(0, 0), render = broken) as Outcome.Failure).error,
        )
        assertEquals(ClipError.PageUnreadable(0), (make(mapOf(0 to null), UnitKey(0, 0)) as Outcome.Failure).error)
        assertEquals(ClipError.NothingToRead, (make(mapOf(0 to page(0, 2)), UnitKey(0, 5)) as Outcome.Failure).error)
    }
}
