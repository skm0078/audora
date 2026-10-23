// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/studio/StudioRun.kt
// Purpose: Renders a book ahead of the listener, page by page, into studio audio (plan item 6, ADR-039).

package io.github.skm0078.audora.playback.studio

import io.github.skm0078.audora.core.config.StudioConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.audio.PcmAudio

/** The book as studio mode sees it: for each page, the units playback would play, with the listener's reading modes. */
interface StudioBook {
    val pageCount: Int

    fun plays(page: Int): StudioPagePlays
}

sealed interface StudioPagePlays {
    data class Units(val units: List<ReadingUnit>) : StudioPagePlays

    /** The page's text cannot be extracted; playback skips it too. */
    data object Skipped : StudioPagePlays

    /** The PDF can no longer be read at all (moved, deleted). */
    data object BookUnreadable : StudioPagePlays
}

/**
 * The live voice, exactly as playback renders: same voices, style, reading modes and fixes. Blocking; throws
 * [InterruptedException] when the run is cancelled. A failure's text is for the log.
 */
fun interface StudioVoice {
    fun render(unit: ReadingUnit): Outcome<StudioVoiced, String>
}

/** A rendered sentence and where its voice is in it ([leadMs] of cue first, [speechMs] of speech), for playback. */
data class StudioVoiced(val audio: PcmAudio, val leadMs: Long = 0L, val speechMs: Long = audio.durationMs)

/**
 * Blocks until rendering may go on: the phone is charging (or the listener chose to render on battery) and no audio
 * is playing, so listening never competes with studio mode for the CPU. Throws [InterruptedException] on cancel.
 */
fun interface StudioGate {
    fun await()
}

/** Progress after every sentence and page, on the run's thread. Keep it cheap. */
fun interface StudioListener {
    fun onProgress(progress: StudioProgress)
}

/**
 * @property fromPage the zero-based page the run started on (the listener's place).
 * @property readyMs audio ready from the start page on, whether rendered now or found from an earlier run.
 * @property renderedPages pages rendered in this run.
 * @property endOfBook the book's end came before [StudioConfig.hoursAhead] were ready.
 */
data class StudioSummary(
    val readyMs: Long,
    val renderedPages: Int,
    val skippedSentences: Int,
    val endOfBook: Boolean,
    val fromPage: Int = 0,
)

/**
 * Blocking, on one worker thread. A page already rendered with the same [StudioKey] is kept and counted; a page
 * partly matching (a few sentences changed) renders only what changed. A sentence that fails to render is left out,
 * so playback renders it live; [StudioConfig.renderRetryLimit] in a row stop the run.
 *
 * Before it starts, pages the listener is past (beyond [StudioConfig.keepBehindPages]) and audio of an old signature
 * are deleted, and other books' audio makes room within [StudioConfig.maxBytes].
 */
class StudioRun(
    private val voice: StudioVoice,
    private val store: StudioStore,
    private val gate: StudioGate,
    private val config: StudioConfig = StudioConfig(),
    private val freeBytes: () -> Long = { Long.MAX_VALUE },
    private val listener: StudioListener = StudioListener { },
) {
    private val codec = LosslessPcm(config.blockSamples)

    fun run(key: StudioKey, book: StudioBook, fromPage: Int): Outcome<StudioSummary, StudioError> {
        val start = fromPage.coerceIn(0, maxOf(0, book.pageCount - 1))
        store.prune(key, keepFromPage = start - config.keepBehindPages)
        if (!store.fitWithin(config.maxBytes, key.book)) return Outcome.Failure(StudioError.NoSpace)
        val pass = Pass(key, start, book.pageCount)
        // Lazy: the target is checked again before each page, after the pages before it added their audio.
        val stop = (start until book.pageCount).asSequence()
            .takeWhile { pass.readyMs < config.aheadMs }
            .firstNotNullOfOrNull { page -> pass.page(page, book.plays(page)) }
        stop?.let { AudoraLog.w(TAG, "Studio stopped, book=${key.book.logLabel}: ${it.message}") }
        return stop?.let { Outcome.Failure(it) }
            ?: Outcome.Success(pass.summary(endOfBook = pass.readyMs < config.aheadMs))
    }

    /** One run's mutable state. */
    private inner class Pass(private val key: StudioKey, private val fromPage: Int, private val pageCount: Int) {
        var readyMs = 0L
        private var renderedPages = 0
        private var skippedSentences = 0
        private var failuresInARow = 0

        /** The page being worked on, and its audio rendered so far (not yet in [readyMs]). */
        private var page = fromPage
        private var pageSoFarMs = 0L

        /** What this run has measured: audio made and the time the voice took to make it, waits left out. */
        private var renderedMs = 0L
        private var renderingNs = 0L

        fun summary(endOfBook: Boolean) =
            StudioSummary(readyMs, renderedPages, skippedSentences, endOfBook, fromPage)

        private fun report() = listener.onProgress(
            StudioProgress(
                readyMs = readyMs + pageSoFarMs,
                pagesReadyMs = readyMs,
                page = page,
                fromPage = fromPage,
                pageCount = pageCount,
                renderedMs = renderedMs,
                renderingMs = renderingNs / NS_PER_MS,
            ),
        )

        /** Null when the page is ready (or skipped, as playback skips it); the reason to stop otherwise. */
        fun page(page: Int, plays: StudioPagePlays): StudioError? {
            this.page = page
            pageSoFarMs = 0L
            return when (plays) {
                is StudioPagePlays.Units -> units(page, plays.units)
                StudioPagePlays.Skipped -> null
                StudioPagePlays.BookUnreadable -> StudioError.BookUnreadable(page)
            }
        }

        private fun units(page: Int, plays: List<ReadingUnit>): StudioError? {
            val existing = store.page(key, page)
            return when {
                existing != null && existing.covers(plays) -> {
                    readyMs += plays.sumOf { existing.clipFor(it)?.durationMs ?: 0L }
                    report()
                    null
                }

                freeBytes() < config.minFreeBytes -> StudioError.NoSpace

                else -> renderPage(page, plays, existing)
            }
        }

        /** Renders what [existing] lacks; sentences it already has keep their audio. */
        private fun renderPage(page: Int, plays: List<ReadingUnit>, existing: StudioPage?): StudioError? {
            val clips = ArrayList<StudioClip>(plays.size)
            for (unit in plays) {
                (existing?.clipFor(unit) ?: render(unit))?.let { clip ->
                    clips.add(clip)
                    pageSoFarMs += clip.durationMs
                    report()
                }
                if (failuresInARow >= config.renderRetryLimit) return StudioError.VoiceFailed("the voice failed")
            }
            return save(page, clips)
        }

        private fun render(unit: ReadingUnit): StudioClip? {
            gate.await()
            if (Thread.interrupted()) throw InterruptedException("Studio mode stopped")
            val started = System.nanoTime()
            val rendered = voice.render(unit)
            renderingNs += System.nanoTime() - started
            return when (rendered) {
                is Outcome.Success -> {
                    failuresInARow = 0
                    renderedMs += rendered.value.audio.durationMs
                    StudioClip.of(unit, rendered.value, codec)
                }

                is Outcome.Failure -> {
                    failuresInARow++
                    skippedSentences++
                    AudoraLog.w(
                        TAG,
                        "p${unit.page}u${unit.index} left to live rendering, book=${key.book.logLabel}: " +
                            rendered.error,
                    )
                    null
                }
            }
        }

        private fun save(page: Int, clips: List<StudioClip>): StudioError? {
            val studioPage = StudioPage(page, clips)
            when (val written = store.write(key, studioPage)) {
                is Outcome.Failure -> return written.error
                is Outcome.Success -> Unit
            }
            renderedPages++
            readyMs += studioPage.durationMs
            pageSoFarMs = 0L
            // The measured speed, for the logs a listener shares when the time left looks wrong.
            AudoraLog.i(
                TAG,
                "Studio page $page ready, book=${key.book.logLabel}: readyMs=$readyMs renderedMs=$renderedMs " +
                    "renderingMs=${renderingNs / NS_PER_MS}",
            )
            report()
            return if (store.fitWithin(config.maxBytes, key.book)) null else StudioError.NoSpace
        }
    }

    private companion object {
        const val TAG = "Studio.Run"
        const val NS_PER_MS = 1_000_000L
    }
}
