// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/audio/VoiceShaperTest.kt
// Purpose: Even loudness, boost without clipping, and pause trim keeping Audora's rhythm (plan items 9, 10).

package io.github.skm0078.audora.speech.audio

import io.github.skm0078.audora.core.config.VoiceShapingConfig
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceShaperTest {
    private val config = VoiceShapingConfig()
    private val shaper = VoiceShaper(config)

    @Test
    fun `quiet and loud voices end up at the same loudness`() {
        val quiet = shaper.level(tone(amplitude = 2_000, ms = 500), evenLoudness = true, boostDb = 0)
        val loud = shaper.level(tone(amplitude = 12_000, ms = 500), evenLoudness = true, boostDb = 0)
        val quietDb = shaper.speechDbfs(quiet)!!
        val loudDb = shaper.speechDbfs(loud)!!
        assertEquals(config.targetDbfs, quietDb, 0.5f)
        assertEquals(config.targetDbfs, loudDb, 0.5f)
    }

    @Test
    fun `evening never raises more than the cap`() {
        val whisper = tone(amplitude = 20, ms = 500) // about -67 dBFS, below the gate: left alone
        assertSame(whisper, shaper.level(whisper, evenLoudness = true, boostDb = 0))
        val faint = tone(amplitude = 150, ms = 500) // about -49 dBFS... below the gate too
        assertNull(shaper.speechDbfs(faint))
        val soft = tone(amplitude = 400, ms = 500) // about -41 dBFS: needs 21 dB, gets 12
        val raised = shaper.speechDbfs(shaper.level(soft, evenLoudness = true, boostDb = 0))!!
        assertEquals(shaper.speechDbfs(soft)!! + config.maxGainDb, raised, 0.5f)
    }

    @Test
    fun `boost is louder and never clips`() {
        val voice = tone(amplitude = 30_000, ms = 300)
        val boosted = shaper.level(voice, evenLoudness = false, boostDb = 9)
        val ceiling = 32_768 * 10.0.pow(config.ceilingDbfs / 20.0)
        assertTrue(boosted.samples.all { abs(it.toInt()) <= ceiling + 1 })
        assertTrue(shaper.speechDbfs(boosted)!! > shaper.speechDbfs(voice)!!)
    }

    @Test
    fun `nothing to do returns the same audio`() {
        val voice = tone(amplitude = 3_000, ms = 200)
        assertSame(voice, shaper.level(voice, evenLoudness = false, boostDb = 0))
    }

    @Test
    fun `long inner pauses shrink, short ones stay, edges are cut`() {
        val audio = PcmAudio.concat(
            listOf(
                silence(300),
                tone(5_000, 200),
                silence(100), // short: kept
                tone(5_000, 200),
                silence(800), // long: cut to longPauseMs
                tone(5_000, 200),
                silence(500),
            ),
        )
        val trimmed = shaper.trimPauses(audio)
        val expectedMs = config.edgeMs + 200 + 100 + 200 + config.longPauseMs + 200 + config.edgeMs
        assertEquals(expectedMs.toLong(), trimmed.durationMs)
    }

    @Test
    fun `all silence and no pauses are left alone`() {
        val quiet = silence(400)
        assertSame(quiet, shaper.trimPauses(quiet))
        val speech = tone(5_000, 400)
        assertSame(speech, shaper.trimPauses(speech))
    }

    private fun tone(amplitude: Int, ms: Int): PcmAudio {
        val count = RATE * ms / 1_000
        return PcmAudio(
            ShortArray(count) { i -> (amplitude * sin(2 * PI * TONE_HZ * i / RATE)).roundToInt().toShort() },
            RATE,
        )
    }

    private fun silence(ms: Int) = PcmAudio.silence(RATE, ms)

    private companion object {
        const val RATE = 16_000
        const val TONE_HZ = 220.0
    }
}
