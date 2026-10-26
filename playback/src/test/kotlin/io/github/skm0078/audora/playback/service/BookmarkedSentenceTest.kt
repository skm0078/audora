// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/service/BookmarkedSentenceTest.kt
// Purpose: The lock screen's bookmark shows filled (CF-1) while the sentence just bookmarked plays, and only then.

package io.github.skm0078.audora.playback.service

import io.github.skm0078.audora.playback.queue.UnitKey
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkedSentenceTest {
    private val marked = BookmarkedSentence()
    private val first = UnitKey(page = 3, index = 0)
    private val second = UnitKey(page = 3, index = 1)

    @Test
    fun `filled while its sentence plays, empty from the next one`() {
        assertFalse(marked.onPlaying(first))
        assertTrue("filling it is a change to send", marked.onBookmarked(first))
        assertTrue(marked.shown)
        assertFalse("bookmarking it again changes nothing", marked.onBookmarked(first))
        assertTrue("the next sentence empties it", marked.onPlaying(second))
        assertFalse(marked.shown)
        assertFalse("back on the first, the mark was let go", marked.onPlaying(first))
        assertFalse(marked.shown)
    }

    @Test
    fun `a bookmark saved after its sentence ended shows nothing`() {
        marked.onPlaying(second)
        assertFalse(marked.onBookmarked(first))
        assertFalse(marked.shown)
    }

    @Test
    fun `no sentence playing (the next page loading) empties it`() {
        marked.onPlaying(first)
        marked.onBookmarked(first)
        assertTrue(marked.onPlaying(null))
        assertFalse(marked.shown)
    }
}
