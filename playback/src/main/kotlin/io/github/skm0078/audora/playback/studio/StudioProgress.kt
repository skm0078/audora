// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/studio/StudioProgress.kt
// Purpose: How far a studio run is, how fast this phone renders with the book's voice, and the honest time left.

package io.github.skm0078.audora.playback.studio

/**
 * A studio run's state after a sentence or a page, for the progress the listener sees (ADR-062).
 *
 * @property readyMs speech ready from the start page on, including the sentences of the page being rendered.
 * @property pagesReadyMs the part of [readyMs] on finished pages, before [page].
 * @property page the zero-based page being worked on.
 * @property fromPage the page the run started on (the listener's place).
 * @property pageCount the book's pages.
 * @property renderedMs speech this run rendered itself (pages found from an earlier run are not counted).
 * @property renderingMs the time the voice took to render [renderedMs]; waits for the charger or for other audio are
 *   left out, so the speed is the phone's and the voice's, not how long the phone sat unplugged.
 */
data class StudioProgress(
    val readyMs: Long,
    val pagesReadyMs: Long,
    val page: Int,
    val fromPage: Int,
    val pageCount: Int,
    val renderedMs: Long,
    val renderingMs: Long,
) {
    /** One-based, for "page 15 of 120". */
    val shownPage: Int get() = page + 1

    /**
     * Milliseconds the voice takes per millisecond of speech; null until it has rendered for [minMeasureMs], since
     * the first sentences include loading the model and would make the estimate far too slow.
     */
    fun renderFactor(minMeasureMs: Long): Double? =
        if (renderingMs >= minMeasureMs && renderedMs > 0) renderingMs.toDouble() / renderedMs else null

    /**
     * Speech still to make before the run stops: up to [aheadMs], or to the book's end if that comes first. The rest
     * of the book is judged from the speech per finished page so far; before a page is finished only the target counts.
     */
    fun speechLeftMs(aheadMs: Long): Long {
        val toTarget = (aheadMs - readyMs).coerceAtLeast(0L)
        val finishedPages = page - fromPage
        if (finishedPages <= 0) return toTarget
        val perPage = pagesReadyMs / finishedPages
        val restOfBook = (perPage * (pageCount - page) - (readyMs - pagesReadyMs)).coerceAtLeast(0L)
        return minOf(toTarget, restOfBook)
    }

    /** 0-100: [readyMs] against what the run will have made when it stops. */
    fun percent(aheadMs: Long): Int {
        val total = readyMs + speechLeftMs(aheadMs)
        return if (total <= 0L) 0 else (readyMs * PERCENT / total).toInt().coerceIn(0, PERCENT)
    }

    /** The rendering time still needed at the measured speed; null while the speed is still being measured. */
    fun timeLeftMs(aheadMs: Long, minMeasureMs: Long): Long? =
        renderFactor(minMeasureMs)?.let { factor -> (speechLeftMs(aheadMs) * factor).toLong() }

    private companion object {
        const val PERCENT = 100
    }
}
