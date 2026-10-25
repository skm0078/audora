// File: app/src/test/kotlin/io/github/skm0078/audora/tile/TileTapTest.kt
// Purpose: The Quick Settings tile pauses or plays the loaded book, opens the app with none, and waits while it is
//          still connecting instead of opening the app over a book that is playing.

package io.github.skm0078.audora.tile

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.ui.reader.PlaybackSnapshot
import io.github.skm0078.audora.ui.reader.PlaybackSnapshot.Connection
import io.github.skm0078.audora.ui.reader.ReadingPosition
import org.junit.Assert.assertEquals
import org.junit.Test

class TileTapTest {
    private val loaded = ReadingPosition(BookId("moby"), 3, 0)

    @Test
    fun `a playing book is paused`() {
        val snapshot = PlaybackSnapshot(Connection.CONNECTED, loaded, wantsToPlay = true)

        assertEquals(TileTap.PAUSE, tileTap(snapshot))
    }

    @Test
    fun `a paused book plays`() {
        assertEquals(TileTap.PLAY, tileTap(PlaybackSnapshot(Connection.CONNECTED, loaded)))
    }

    @Test
    fun `nothing loaded opens the app`() {
        assertEquals(TileTap.OPEN_APP, tileTap(PlaybackSnapshot(Connection.CONNECTED)))
        assertEquals(TileTap.OPEN_APP, tileTap(PlaybackSnapshot(Connection.UNAVAILABLE)))
        assertEquals(TileTap.OPEN_APP, tileTap(null))
    }

    @Test
    fun `a tap while connecting waits for the player`() {
        assertEquals(TileTap.WAIT, tileTap(PlaybackSnapshot.Connecting))
    }
}
