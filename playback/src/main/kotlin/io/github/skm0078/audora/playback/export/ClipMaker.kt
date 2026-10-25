// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/ClipMaker.kt
// Purpose: Renders a passage, from a picked sentence to the end of its paragraph or a highlight's end, as one audio
//          clip (plan item 41).

package io.github.skm0078.audora.playback.export

import io.github.skm0078.audora.core.config.ClipConfig
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.UnitKind
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.queue.SentenceRenderer
import io.github.skm0078.audora.playback.queue.UnitKey
import io.github.skm0078.audora.speech.audio.PcmAudio

/** The rendered clip: its audio and the sentences it holds (in order), for the text that goes with it. */
class Clip(val audio: PcmAudio, val units: List<ReadingUnit>)

/**
 * A highlighted passage: from the sentence that holds [startChar] on the clip's start page through the sentences that
 * begin before [endChar] on [endPage]. Offsets, not indexes: a highlight is saved by them, and its pages need not be
 * the one showing.
 */
data class ClipPassage(val startChar: Int, val endPage: Int, val endChar: Int)

/** Why there is no clip. */
sealed interface ClipError {
    /** The picked sentence and the ones after it are not read (a skipped table, the end of the book). */
    data object NothingToRead : ClipError

    data class VoiceFailed(val detail: String) : ClipError

    data class PageUnreadable(val page: Int) : ClipError
}

/**
 * Blocking, on one worker thread; throws [InterruptedException] on cancel. Sentences go through the renderer playback
 * uses (studio audio first when it is ready), so the clip sounds exactly like listening. Only the sentences playback
 * plays are used ([plays], the listener's reading modes); a picked sentence that is not played starts the clip at the
 * next one that is.
 *
 * The clip ends at the first paragraph end (or heading) once it is [ClipConfig.minMs] long, and before any sentence
 * that would take it past [ClipConfig.maxMs]; its first sentence is always kept. Voices of different rates are joined
 * at the first sentence's rate.
 *
 * A [ClipPassage] (a highlight shared as audio) runs from its first sentence to its last instead, past paragraph ends
 * and headings, up to [ClipConfig.passageMaxMs] and [ClipConfig.passageMaxPages].
 */
class ClipMaker(private val renderer: SentenceRenderer, private val config: ClipConfig = ClipConfig()) {
    /**
     * @param pageUnits a page's units, or null when the page cannot be read; [pageCount] bounds the search.
     * @param plays the units of a page that playback plays, in order.
     * @param passage a highlight's passage; [start]'s index is then ignored, its page is where the passage begins.
     */
    fun make(
        start: UnitKey,
        pageCount: Int,
        pageUnits: (Int) -> List<ReadingUnit>?,
        plays: (List<ReadingUnit>) -> List<ReadingUnit>,
        passage: ClipPassage? = null,
    ): Outcome<Clip, ClipError> {
        val clip = Collector(passage != null)
        val lastPage = when (passage) {
            null -> minOf(pageCount, start.page + config.maxPages) - 1
            else -> minOf(pageCount - 1, passage.endPage, start.page + config.passageMaxPages - 1)
        }
        val stop = (start.page..lastPage).asSequence().firstNotNullOfOrNull { page ->
            val units = pageUnits(page)
            if (units == null) {
                Stop.Failed(ClipError.PageUnreadable(page))
            } else {
                plays(units).asSequence()
                    .filter { page > start.page || startsAfter(it, start, passage) }
                    .firstNotNullOfOrNull { unit -> if (passage.endsBefore(unit)) Stop.Done else clip.add(unit) }
            }
        }
        return if (stop is Stop.Failed) Outcome.Failure(stop.error) else clip.finish()
    }

    /** On the start page: at or after the picked sentence, or ending after the passage's start. */
    private fun startsAfter(unit: ReadingUnit, start: UnitKey, passage: ClipPassage?): Boolean =
        if (passage == null) unit.index >= start.index else unit.charEnd > passage.startChar

    /** [unit] comes after the passage: on a later page, or beginning at or after its end. */
    private fun ClipPassage?.endsBefore(unit: ReadingUnit): Boolean {
        if (this == null) return false
        return unit.page > endPage || (unit.page == endPage && unit.charStart >= endChar)
    }

    private sealed interface Stop {
        data object Done : Stop

        data class Failed(val error: ClipError) : Stop
    }

    /** The clip so far; a [passage]'s ends where the passage does, so paragraphs and headings do not end it. */
    private inner class Collector(private val passage: Boolean) {
        private val maxMs = if (passage) config.passageMaxMs else config.maxMs
        private val parts = ArrayList<PcmAudio>()
        private val used = ArrayList<ReadingUnit>()
        private var totalMs = 0L

        /** Null to go on with the next sentence; [Stop.Done] when the clip is complete. */
        fun add(unit: ReadingUnit): Stop? {
            val headingEnds = used.isNotEmpty() && totalMs >= config.minMs && unit.kind == UnitKind.HEADING
            if (!passage && headingEnds) return Stop.Done
            return when (val rendered = renderer.render(unit)) {
                is Outcome.Failure -> Stop.Failed(ClipError.VoiceFailed(rendered.error.message))
                is Outcome.Success -> append(unit, rendered.value)
            }
        }

        private fun append(unit: ReadingUnit, audio: PcmAudio): Stop? {
            if (used.isNotEmpty() && totalMs + audio.durationMs > maxMs) return Stop.Done
            parts.add(audio)
            used.add(unit)
            totalMs += audio.durationMs
            val paragraphEnd = totalMs >= config.minMs && unit.pauseAfterMs >= config.paragraphPauseMs
            return if (!passage && paragraphEnd) Stop.Done else null
        }

        fun finish(): Outcome<Clip, ClipError> {
            if (parts.isEmpty()) return Outcome.Failure(ClipError.NothingToRead)
            val rate = parts.first().sampleRate
            return Outcome.Success(Clip(PcmAudio.concat(parts.map { it.resampled(rate) }), used.toList()))
        }
    }
}
