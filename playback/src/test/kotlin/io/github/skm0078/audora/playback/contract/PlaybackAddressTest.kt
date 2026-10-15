// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/contract/PlaybackAddressTest.kt
// Purpose: Media id round trip and rejection of anything that is not an Audora address.

package io.github.skm0078.audora.playback.contract

import io.github.skm0078.audora.core.library.BookId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackAddressTest {
    private val book = BookId("3f2a9c01-7b1e-4c55-9a0d-2f6c1e8b7d10")

    @Test
    fun `media id round trips`() {
        val address = PlaybackAddress(book, page = 573, charStart = 1_204)
        assertEquals("audora://book/${book.value}/page/573/char/1204", address.toMediaId())
        assertEquals(address, PlaybackAddress.parse(address.toMediaId()))
    }

    @Test
    fun `start of page is char 0`() {
        assertEquals(PlaybackAddress(book, 4, 0), PlaybackAddress.startOfPage(book, 4))
    }

    @Test
    fun `anything else parses to null`() {
        val notAddresses = listOf(
            null,
            "",
            "42",
            "audora://book//page/1/char/0",
            "audora://book/abc/page/-1/char/0",
            "audora://book/abc/page/1/unit/3",
            "audora://book/abc/page/1/char/0/extra",
            "audora://book/a b/page/1/char/0",
            "https://book/abc/page/1/char/0",
            "audora://book/abc/page/9999999999/char/0",
        )
        notAddresses.forEach { assertNull("parsed '$it'", PlaybackAddress.parse(it)) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a book id that cannot be carried is refused`() {
        PlaybackAddress(BookId("a/b"), 0, 0)
    }

    @Test
    fun `isOnPage compares book and page only`() {
        val address = PlaybackAddress(book, 2, 50)
        assertTrue(address.isOnPage(book, 2))
        assertFalse(address.isOnPage(book, 3))
        assertFalse(address.isOnPage(BookId("other"), 2))
    }
}
