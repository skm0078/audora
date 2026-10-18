// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/session/ParagraphSkip.kt
// Purpose: Where "next paragraph" and "previous paragraph" go from the sentence playing.

package io.github.skm0078.audora.playback.session

import io.github.skm0078.audora.core.config.ReadingUnitConfig
import io.github.skm0078.audora.core.model.ReadingUnit

/** What a paragraph skip did: moved, had nowhere to go that way, or was dropped (reading moved on meanwhile). */
enum class ParagraphSkipResult { MOVED, NONE_THAT_WAY, DROPPED }

/**
 * A paragraph ends at a unit with a paragraph's pause after it (:pdf gives paragraphs, headings and list items a
 * longer one, ADR-029); paragraphs run on across page breaks. Pages come from [units] (null when a page cannot be
 * read: it is passed over), at most [maxPages] beyond the current one, so a book with no paragraph marks costs a
 * few page reads, not the whole book.
 */
class ParagraphSkip(
    private val units: (page: Int) -> List<ReadingUnit>?,
    private val pageCount: Int,
    config: ReadingUnitConfig = ReadingUnitConfig(),
    private val maxPages: Int = DEFAULT_MAX_PAGES,
) {
    private val endPauseMs = minOf(config.paragraphPauseMs, config.headingPauseMs, config.listItemPauseMs)
    private val sentencePauseMs = config.sentencePauseMs

    init {
        require(maxPages > 0) { "maxPages must be > 0, was $maxPages" }
    }

    /**
     * The first sentence after the paragraph holding [current], with its unit index; past [maxPages] with no
     * paragraph end, the start of the next page not looked at (no index: no unit there is known). Null when the book
     * ends first.
     */
    fun next(current: ReadingUnit): Spot? {
        var ended = endsParagraph(current)
        for (unit in after(current)) {
            if (ended) return unit.spot()
            ended = endsParagraph(unit)
        }
        val unread = current.page + 1 + maxPages
        return if (unread < pageCount) Spot(unread, 0) else null
    }

    /**
     * The start of the paragraph holding [current], or of the one before when [current] starts its paragraph (as a
     * music player's "previous" restarts the track, then goes back), with its unit index. At the book's start,
     * [current] itself. With no unit at all in the [maxPages] pages back (unreadable or empty pages), the start of
     * the page before them (no index), so a skip back never reads as "already at the start" mid-book.
     */
    fun previous(current: ReadingUnit): Spot {
        var start = current
        for (unit in before(current)) {
            // The first unit back is always taken: when it ends a paragraph, [current] starts one, so the
            // paragraph before is the one to go to.
            if (endsParagraph(unit) && start !== current) return start.spot()
            start = unit
        }
        val nothingBack = start === current && current.page > 0
        return if (nothingBack) Spot(maxOf(0, current.page - maxPages - 1), 0) else start.spot()
    }

    private fun endsParagraph(unit: ReadingUnit): Boolean =
        unit.pauseAfterMs >= endPauseMs && unit.pauseAfterMs > sentencePauseMs

    /** The units after [current] in reading order, to [maxPages] pages past its own. */
    private fun after(current: ReadingUnit): Sequence<ReadingUnit> = sequence {
        val last = minOf(pageCount - 1, current.page + maxPages)
        for (page in current.page..last) {
            val onPage = units(page) ?: continue
            yieldAll(if (page == current.page) onPage.filter { it.charStart > current.charStart } else onPage)
        }
    }

    /** The units before [current], nearest first, to [maxPages] pages before its own. */
    private fun before(current: ReadingUnit): Sequence<ReadingUnit> = sequence {
        val first = maxOf(0, current.page - maxPages)
        for (page in current.page downTo first) {
            val onPage = units(page) ?: continue
            val earlier = if (page == current.page) onPage.filter { it.charStart < current.charStart } else onPage
            yieldAll(earlier.asReversed())
        }
    }

    private companion object {
        const val DEFAULT_MAX_PAGES = 3
    }
}
