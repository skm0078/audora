// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/ClipMaker.kt
// Purpose: Renders a passage, from a picked sentence to the end of its paragraph, as one audio clip (plan item 41).

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
 */
class ClipMaker(private val renderer: SentenceRenderer, private val config: ClipConfig = ClipConfig()) {
    /**
     * @param pageUnits a page's units, or null when the page cannot be read; [pageCount] bounds the search.
     * @param plays the units of a page that playback plays, in order.
     */
    fun make(
        start: UnitKey,
        pageCount: Int,
        pageUnits: (Int) -> List<ReadingUnit>?,
        plays: (List<ReadingUnit>) -> List<ReadingUnit>,
    ): Outcome<Clip, ClipError> {
        val clip = Collector()
        val lastPage = minOf(pageCount, start.page + config.maxPages) - 1
        val stop = (start.page..lastPage).asSequence().firstNotNullOfOrNull { page ->
            val units = pageUnits(page)
            if (units == null) {
                Stop.Failed(ClipError.PageUnreadable(page))
            } else {
                plays(units).asSequence()
                    .filter { page > start.page || it.index >= start.index }
                    .firstNotNullOfOrNull(clip::add)
            }
        }
        return if (stop is Stop.Failed) Outcome.Failure(stop.error) else clip.finish()
    }

    private sealed interface Stop {
        data object Done : Stop

        data class Failed(val error: ClipError) : Stop
    }

    /** The clip so far. */
    private inner class Collector {
        private val parts = ArrayList<PcmAudio>()
        private val used = ArrayList<ReadingUnit>()
        private var totalMs = 0L

        /** Null to go on with the next sentence; [Stop.Done] when the clip is complete. */
        fun add(unit: ReadingUnit): Stop? {
            val long = totalMs >= config.minMs
            if (used.isNotEmpty() && long && unit.kind == UnitKind.HEADING) return Stop.Done
            return when (val rendered = renderer.render(unit)) {
                is Outcome.Failure -> Stop.Failed(ClipError.VoiceFailed(rendered.error.message))
                is Outcome.Success -> append(unit, rendered.value)
            }
        }

        private fun append(unit: ReadingUnit, audio: PcmAudio): Stop? {
            if (used.isNotEmpty() && totalMs + audio.durationMs > config.maxMs) return Stop.Done
            parts.add(audio)
            used.add(unit)
            totalMs += audio.durationMs
            return if (totalMs >= config.minMs && unit.pauseAfterMs >= config.paragraphPauseMs) Stop.Done else null
        }

        fun finish(): Outcome<Clip, ClipError> {
            if (parts.isEmpty()) return Outcome.Failure(ClipError.NothingToRead)
            val rate = parts.first().sampleRate
            return Outcome.Success(Clip(PcmAudio.concat(parts.map { it.resampled(rate) }), used.toList()))
        }
    }
}
