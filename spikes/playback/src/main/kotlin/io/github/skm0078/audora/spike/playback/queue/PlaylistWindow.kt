// File: spikes/playback/src/main/kotlin/io/github/skm0078/audora/spike/playback/queue/PlaylistWindow.kt
// Purpose: Decides when to append the next page of items, so the playlist never runs out before the book does.

package io.github.skm0078.audora.spike.playback.queue

import io.github.skm0078.audora.spike.playback.engine.FakeBook

/**
 * Build Spec §6: "The playlist always holds at least the next page of items". The playlist starts at
 * unit 0 and only grows, so a playlist index is also a unit index.
 */
class PlaylistWindow(private val book: FakeBook, private val minItemsAhead: Int) {
    init {
        require(minItemsAhead >= 1) { "minItemsAhead must be at least 1, was $minItemsAhead" }
    }

    /** Units to append now, or null when enough are queued or the book is fully queued. */
    fun nextAppend(lastQueuedUnit: Int, currentUnit: Int): IntRange? {
        val bookFullyQueued = lastQueuedUnit >= book.unitCount - 1
        val enoughAhead = lastQueuedUnit - currentUnit >= minItemsAhead
        if (bookFullyQueued || enoughAhead) return null
        val first = lastQueuedUnit + 1
        return first..book.unitsOnPage(book.unit(first).page).last
    }
}
