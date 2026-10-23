// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/playlist/PlaylistFeeder.kt
// Purpose: Grows the playlist a page at a time ahead of the playhead, so it never runs out before the book does.

package io.github.skm0078.audora.playback.playlist

import io.github.skm0078.audora.core.config.QueueConfig
import io.github.skm0078.audora.core.model.ReadingModes
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.playback.reading.UnitReading

/** One playlist entry: a sentence, or the placeholder that stands for the pages not extracted yet. */
sealed interface PlaybackItem {
    data class Sentence(val unit: ReadingUnit) : PlaybackItem

    /**
     * Always the last item while the book has pages left. If playback reaches it before the next page is
     * extracted, its data source blocks, so the player waits in BUFFERING instead of reaching ENDED (C5).
     * [serial] tells successive placeholders apart (each needs its own media id).
     */
    data class More(val serial: Int) : PlaybackItem
}

/** The player's playlist, seen as [PlaybackItem]s. Implemented over ExoPlayer; a list in tests. */
interface Playlist {
    val size: Int

    /** Index of the item playing or about to play; -1 when the playlist is empty. */
    val currentIndex: Int

    fun item(index: Int): PlaybackItem

    fun add(index: Int, items: List<PlaybackItem>)

    /** Removes [fromIndex] until [toIndex] (exclusive). Removing the current item moves playback to the next. */
    fun remove(fromIndex: Int, toIndex: Int)
}

/**
 * Keeps the invariant "the playlist ends with [PlaybackItem.More] until the last page is in it". Pages are
 * appended in front of the placeholder; when playback has already reached the placeholder, the new sentences
 * go after it and the placeholder is removed, which moves playback straight on to them. At the end of the book
 * the placeholder is removed, so the last sentence's end is the only ENDED the player ever sees.
 *
 * Only the units [reading] plays go in (ADR-035): the reading modes leave out skipped tables, footnotes, equation
 * lines and runs of unreadable text. Main thread only (the player's thread).
 */
class PlaylistFeeder(
    private val playlist: Playlist,
    private val config: QueueConfig,
    private val reading: UnitReading = UnitReading(ReadingModes()),
) {
    private var nextSerial = 0

    /** True once the last page has been appended. */
    var finished: Boolean = false
        private set

    /** The playlist for a fresh start: just the placeholder; the first page follows through [append]. */
    fun initialItems(): List<PlaybackItem> {
        finished = false
        return listOf(newMore())
    }

    /** Sentences queued after the current item. */
    fun sentencesAhead(): Int {
        val current = playlist.currentIndex
        if (current < 0) return 0
        return (current + 1 until playlist.size).count { playlist.item(it) is PlaybackItem.Sentence }
    }

    fun wantsMore(): Boolean = !finished && sentencesAhead() < config.minUnitsAhead

    /**
     * Appends one extracted page; returns how many of its units were unreadable junk (each run plays one cue). A page
     * with nothing to play changes nothing.
     */
    fun append(units: List<ReadingUnit>): Int {
        val spoken = reading.plays(units)
        val more = moreIndex()
        if (!finished && spoken.isNotEmpty() && more != null) insert(more, spoken.map { PlaybackItem.Sentence(it) })
        return reading.unreadableCount(units)
    }

    private fun insert(more: Int, sentences: List<PlaybackItem>) {
        if (more == playlist.currentIndex) {
            playlist.add(more + 1, sentences + newMore())
            playlist.remove(more, more + 1)
        } else {
            playlist.add(more, sentences)
        }
    }

    /** No pages left: drop the placeholder. If playback is waiting on it, the player ends now. */
    fun finish() {
        if (finished) return
        finished = true
        moreIndex()?.let { playlist.remove(it, it + 1) }
    }

    /** Drops items far behind the current one; call after each transition. */
    fun trimBehind() {
        val current = playlist.currentIndex
        val keep = config.keepBehindUnits
        val trimFrom = if (keep == 0) 1 else keep * 2
        if (current >= trimFrom) playlist.remove(0, current - keep)
    }

    /** The current sentence and the ones after it, up to the placeholder, at most [limit]. */
    fun window(limit: Int = 1 + config.prefetchUnits): List<ReadingUnit> {
        val current = playlist.currentIndex
        if (current < 0) return emptyList()
        val units = ArrayList<ReadingUnit>(limit)
        var index = current
        while (index < playlist.size && units.size < limit) {
            val item = playlist.item(index) as? PlaybackItem.Sentence ?: break
            units.add(item.unit)
            index++
        }
        return units
    }

    private fun moreIndex(): Int? = (playlist.size - 1).takeIf { it >= 0 && playlist.item(it) is PlaybackItem.More }

    private fun newMore(): PlaybackItem.More = PlaybackItem.More(nextSerial++)
}
