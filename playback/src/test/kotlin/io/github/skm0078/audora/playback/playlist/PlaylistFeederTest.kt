// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/playlist/PlaylistFeederTest.kt
// Purpose: The playlist always ends with the placeholder while pages remain, so the player never ends early (C5).

package io.github.skm0078.audora.playback.playlist

import io.github.skm0078.audora.core.config.QueueConfig
import io.github.skm0078.audora.playback.page
import io.github.skm0078.audora.playback.unit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistFeederTest {
    private val config = QueueConfig(prefetchUnits = 2, minUnitsAhead = 3, keepBehindUnits = 2)
    private val playlist = FakePlaylist()
    private val feeder = PlaylistFeeder(playlist, config)

    private fun startFresh() = playlist.set(feeder.initialItems())

    @Test
    fun aFreshStartIsJustThePlaceholderAndWantsPages() {
        startFresh()
        assertEquals(1, playlist.size)
        assertTrue(playlist.item(0) is PlaybackItem.More)
        assertTrue(feeder.wantsMore())
        assertEquals(emptyList<Any>(), feeder.window())
    }

    @Test
    fun aPageArrivingWhilePlaybackWaitsOnThePlaceholderPlaysAtOnce() {
        startFresh()
        feeder.append(page(0, 2))
        assertEquals(listOf("p0u0", "p0u1", "more"), playlist.describe())
        assertEquals("playback moved on to the first sentence", 0, playlist.currentIndex)
        assertEquals(listOf(unit(0, 0), unit(0, 1)), feeder.window())
    }

    @Test
    fun laterPagesGoInFrontOfThePlaceholder() {
        startFresh()
        feeder.append(page(0, 2))
        feeder.append(page(1, 1))
        assertEquals(listOf("p0u0", "p0u1", "p1u0", "more"), playlist.describe())
        assertEquals(0, playlist.currentIndex)
    }

    @Test
    fun placeholdersAreDistinct() {
        startFresh()
        val first = playlist.item(0)
        feeder.append(page(0, 1))
        assertTrue(first != playlist.item(playlist.size - 1))
    }

    @Test
    fun anEmptyPageChangesNothing() {
        startFresh()
        feeder.append(emptyList())
        assertEquals(listOf("more"), playlist.describe())
    }

    @Test
    fun wantsMoreUntilEnoughSentencesAreQueuedAhead() {
        startFresh()
        feeder.append(page(0, 3))
        assertEquals(2, feeder.sentencesAhead())
        assertTrue(feeder.wantsMore())
        feeder.append(page(1, 1))
        assertFalse(feeder.wantsMore())
    }

    @Test
    fun finishingRemovesThePlaceholderSoOnlyTheLastSentenceEnds() {
        startFresh()
        feeder.append(page(0, 2))
        feeder.finish()
        assertEquals(listOf("p0u0", "p0u1"), playlist.describe())
        assertFalse(feeder.wantsMore())
        feeder.append(page(1, 1))
        assertEquals("nothing is appended after the end", 2, playlist.size)
    }

    @Test
    fun finishingWhileWaitingOnThePlaceholderEndsPlayback() {
        startFresh()
        feeder.append(page(0, 1))
        playlist.currentIndex = 1 // the last sentence ended; the placeholder is playing
        feeder.finish()
        assertEquals(listOf("p0u0"), playlist.describe())
        assertTrue(playlist.ended)
    }

    @Test
    fun windowStopsAtThePlaceholderAndAtTheLimit() {
        startFresh()
        feeder.append(page(0, 5))
        assertEquals(3, feeder.window().size)
        playlist.currentIndex = 3
        assertEquals(listOf(unit(0, 3), unit(0, 4)), feeder.window())
    }

    @Test
    fun trimBehindKeepsOnlyRecentSentences() {
        startFresh()
        feeder.append(page(0, 8))
        playlist.currentIndex = 3
        feeder.trimBehind()
        assertEquals("below the trim threshold", 9, playlist.size)
        playlist.currentIndex = 5
        feeder.trimBehind()
        assertEquals(listOf("p0u3", "p0u4", "p0u5", "p0u6", "p0u7", "more"), playlist.describe())
        assertEquals(2, playlist.currentIndex)
    }
}

/** Mimics ExoPlayer's playlist: inserting before the current item shifts it, removing it moves to the next. */
class FakePlaylist : Playlist {
    private val items = ArrayList<PlaybackItem>()
    var ended = false
        private set

    override val size: Int get() = items.size
    override var currentIndex: Int = -1

    fun set(newItems: List<PlaybackItem>) {
        items.clear()
        items.addAll(newItems)
        currentIndex = if (items.isEmpty()) -1 else 0
        ended = false
    }

    override fun item(index: Int): PlaybackItem = items[index]

    override fun add(index: Int, items: List<PlaybackItem>) {
        this.items.addAll(index, items)
        if (currentIndex < 0) {
            currentIndex = 0
        } else if (index <= currentIndex) {
            currentIndex += items.size
        }
    }

    override fun remove(fromIndex: Int, toIndex: Int) {
        val count = toIndex - fromIndex
        val removesCurrent = currentIndex in fromIndex until toIndex
        repeat(count) { items.removeAt(fromIndex) }
        when {
            items.isEmpty() -> currentIndex = -1

            removesCurrent && fromIndex >= items.size -> {
                currentIndex = items.size - 1
                ended = true
            }

            removesCurrent -> currentIndex = fromIndex

            currentIndex >= toIndex -> currentIndex -= count
        }
    }

    fun describe(): List<String> = items.map {
        when (it) {
            is PlaybackItem.Sentence -> "p${it.unit.page}u${it.unit.index}"
            is PlaybackItem.More -> "more"
        }
    }
}
