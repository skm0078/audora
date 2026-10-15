// File: spikes/voices/src/test/kotlin/io/github/skm0078/audora/spike/voices/listen/BlindOrderTest.kt
// Purpose: Blind letters are stable for a seed, independent of catalog order, and cover every voice once.

package io.github.skm0078.audora.spike.voices.listen

import org.junit.Assert.assertEquals
import org.junit.Test

class BlindOrderTest {
    private val ids = listOf("piper", "kitten", "kokoro", "supertonic", "system-en")

    @Test
    fun `same seed gives the same letters whatever the input order`() {
        assertEquals(BlindOrder.assign(ids, 42), BlindOrder.assign(ids.reversed(), 42))
    }

    @Test
    fun `every voice gets exactly one letter in order`() {
        val entries = BlindOrder.assign(ids, 7)

        assertEquals(ids.sorted(), entries.map { it.voiceId }.sorted())
        assertEquals(listOf("A", "B", "C", "D", "E"), entries.map { it.letter })
    }

    @Test
    fun `letters continue past Z`() {
        assertEquals("Z", BlindOrder.letter(25))
        assertEquals("AA", BlindOrder.letter(26))
        assertEquals("AB", BlindOrder.letter(27))
    }
}
