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
    private val config = ClipConfig(minMs = 5_000, maxMs = 9_000, paragraphPauseMs = 400, maxPages = 2)

    /** Page [page] with [count] sentences; those in [paragraphEnds] end a paragraph. */
    private fun page(page: Int, count: Int, paragraphEnds: Set<Int> = emptySet()): List<ReadingUnit> =
        List(count) { i -> unit(page, i).copy(pauseAfterMs = if (i in paragraphEnds) 400 else 0) }

    private fun make(
        pages: Map<Int, List<ReadingUnit>?>,
        start: UnitKey,
        plays: (List<ReadingUnit>) -> List<ReadingUnit> = { it },
        render: SentenceRenderer = renderer,
    ) = ClipMaker(render, config).make(start, pageCount = pages.size, pageUnits = { pages[it] }, plays = plays)

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
