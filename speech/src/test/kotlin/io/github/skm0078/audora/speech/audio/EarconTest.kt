// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/audio/EarconTest.kt
// Purpose: The cue is short, quiet, click-free and follows the voice's sample rate (RI-2b, RI-7b).

package io.github.skm0078.audora.speech.audio

import io.github.skm0078.audora.core.config.CueConfig
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EarconTest {
    private val config = CueConfig()
    private val earcon = Earcon(config)

    @Test
    fun `the cue is two tones long, below its peak, silent at both ends`() {
        val cue = earcon.cue(16_000)
        assertEquals(config.toneMs * config.tonesHz.size.toLong(), cue.durationMs)
        val peak = cue.samples.maxOf { abs(it.toInt()) }
        assertTrue("peak $peak", peak in 3_000..3_400) // -20 dBFS is 3,277
        assertEquals(0, cue.samples.first().toInt())
        assertTrue(abs(cue.samples.last().toInt()) < 50)
        assertSame("cached per rate", cue, earcon.cue(16_000))
    }

    @Test
    fun `before joins the cue, the gap and the words at the words' rate`() {
        val words = PcmAudio(ShortArray(1_600) { 1 }, 16_000)
        val joined = earcon.before(words)
        assertEquals(16_000, joined.sampleRate)
        val cueAndGap = (config.toneMs * config.tonesHz.size + config.gapAfterMs) * 16
        assertEquals(cueAndGap + 1_600, joined.samples.size)
        assertEquals(1, joined.samples.last().toInt())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a tone above the Nyquist rate is refused`() {
        CueConfig(tonesHz = listOf(12_000f))
    }
}
