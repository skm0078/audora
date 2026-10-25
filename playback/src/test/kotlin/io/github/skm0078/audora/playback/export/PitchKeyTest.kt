// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/export/PitchKeyTest.kt
// Purpose: A resumable job's key names the pitch only when it is not the voice's own (ADR-067).

package io.github.skm0078.audora.playback.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PitchKeyTest {
    @Test
    fun `a job at the voice's own pitch keeps the key it had before pitch existed`() {
        assertEquals("abc", withPitch("abc", 1f))
    }

    @Test
    fun `a job at another pitch has another key, one per pitch`() {
        assertNotEquals("abc", withPitch("abc", 1.1f))
        assertNotEquals(withPitch("abc", 1.1f), withPitch("abc", 0.9f))
        assertEquals(withPitch("abc", 1.1f), withPitch("abc", 1.1f))
    }
}
