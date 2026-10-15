// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/session/TtsUriTest.kt
// Purpose: Playlist item URIs round-trip and anything else is rejected.

package io.github.skm0078.audora.playback.session

import io.github.skm0078.audora.playback.queue.UnitKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TtsUriTest {
    private val book = "3f2a9c01-5d7e-4b1a-9c3e-0123456789ab"

    @Test
    fun sentenceRoundTrips() {
        val uri = TtsUri.Sentence(book, 7, UnitKey(12, 3))
        assertEquals("audora://book/$book/e/7/p/12/u/3", uri.toString())
        assertEquals(uri, TtsUri.parse(uri.toString()))
    }

    @Test
    fun placeholderRoundTrips() {
        val uri = TtsUri.More(book, 2, 5)
        assertEquals("audora://book/$book/e/2/more/5", uri.toString())
        assertEquals(uri, TtsUri.parse(uri.toString()))
    }

    @Test
    fun malformedUrisAreRejected() {
        listOf(
            "",
            "audora://book/",
            "https://book/$book/e/1/p/0/u/0",
            "audora://book/$book/e/x/p/0/u/0",
            "audora://book/$book/e/1/p/-1/u/0",
            "audora://book/$book/e/1/p/0/u/0/extra",
            "audora://book/$book/e/1/p/0",
            "audora://book/$book/e/1/more/",
            "audora://book/$book/e/1/p/99999999999/u/0",
        ).forEach { assertNull(it, TtsUri.parse(it)) }
    }
}
