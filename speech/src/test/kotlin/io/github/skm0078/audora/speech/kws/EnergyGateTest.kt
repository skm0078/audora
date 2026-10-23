// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/kws/EnergyGateTest.kt
// Purpose: The energy gate passes sound with its pre-roll and tail, and holds back a quiet room.

package io.github.skm0078.audora.speech.kws

import io.github.skm0078.audora.core.config.VoiceCommandConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EnergyGateTest {
    private val config = VoiceCommandConfig(chunkMs = 100, gateDbfs = -40f, preRollChunks = 2, hangoverMs = 150)

    private fun chunk(level: Float) = FloatArray(config.chunkSamples) { level }

    @Test
    fun `a quiet room is never decoded`() {
        val gate = EnergyGate(config)
        repeat(50) { assertTrue(gate.pass(chunk(QUIET)).isEmpty()) }
    }

    @Test
    fun `sound comes with the last quiet chunks before it and a tail after it`() {
        val gate = EnergyGate(config)
        val quiet = List(3) { chunk(QUIET) }
        quiet.forEach { gate.pass(it) }
        val loud = chunk(LOUD)

        val passed = gate.pass(loud)
        assertEquals(3, passed.size)
        assertSame(quiet[1], passed[0])
        assertSame(loud, passed[2])
        // 150 ms of tail is two 100 ms chunks, then quiet is held back again.
        assertEquals(1, gate.pass(chunk(QUIET)).size)
        assertEquals(1, gate.pass(chunk(QUIET)).size)
        assertTrue(gate.pass(chunk(QUIET)).isEmpty())
    }

    @Test
    fun `the threshold is the chunk's RMS in dBFS`() {
        val gate = EnergyGate(config)
        assertTrue(gate.pass(chunk(JUST_BELOW)).isEmpty())
        assertEquals(2, gate.pass(chunk(JUST_ABOVE)).size)
    }

    private companion object {
        const val QUIET = 0.001f // -60 dBFS
        const val LOUD = 0.3f
        const val JUST_BELOW = 0.0095f // -40.4 dBFS
        const val JUST_ABOVE = 0.0105f // -39.6 dBFS
    }
}
