// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/outputs/MadePositionsTest.kt
// Purpose: A saved audio's media items name their set, and a stored position reads back or is dropped when damaged.

package io.github.skm0078.audora.playback.outputs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MadePositionsTest {
    @Test
    fun `a media item id names its set and part`() {
        val id = MadePositions.mediaId("3f2a-set", 4)

        assertEquals("3f2a-set#4", id)
        assertEquals("3f2a-set", MadePositions.setOf(id))
        assertNull(MadePositions.setOf("book-sentence-12"))
    }

    @Test
    fun `stored positions read back, damaged ones are dropped`() {
        assertEquals(MadePosition(2, 61_000), MadePositions.parse("2:61000"))
        listOf("", "2", "x:1", "1:y", "-1:5", "1:-5", "1:2:3").forEach { assertNull(it, MadePositions.parse(it)) }
    }
}
