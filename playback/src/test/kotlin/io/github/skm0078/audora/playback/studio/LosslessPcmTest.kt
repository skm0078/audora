// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/studio/LosslessPcmTest.kt
// Purpose: Studio audio round-trips bit for bit, compresses speech-like audio, and rejects damaged streams.

package io.github.skm0078.audora.playback.studio

import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LosslessPcmTest {
    private val codec = LosslessPcm(BLOCK)

    private fun roundTrip(samples: ShortArray): ByteArray {
        val encoded = codec.encode(samples)
        assertArrayEquals(samples, codec.decode(encoded, samples.size))
        return encoded
    }

    /** A voiced tone with a little noise, then the exact-zero silence a sentence ends with. */
    private fun speechLike(seconds: Double, rate: Int = 22_050): ShortArray {
        val random = Random(SEED)
        val voiced = (seconds * rate).toInt()
        return ShortArray(voiced + rate / 2) { i ->
            if (i >= voiced) {
                0
            } else {
                val t = i.toDouble() / rate
                val tone = 6_000 * sin(2 * PI * 180 * t) + 2_500 * sin(2 * PI * 720 * t)
                (tone + random.nextInt(-40, 40)).roundToInt().toShort()
            }
        }
    }

    @Test
    fun `round trips every kind of block exactly`() {
        val cases = mapOf(
            "empty" to ShortArray(0),
            "one sample" to shortArrayOf(-7),
            "silence" to ShortArray(10_000),
            "constant" to ShortArray(5_000) { 1_234 },
            "extremes" to ShortArray(3_000) { if (it % 2 == 0) Short.MAX_VALUE else Short.MIN_VALUE },
            "noise" to Random(SEED).let { r -> ShortArray(9_999) { r.nextInt().toShort() } },
            "ramp" to ShortArray(BLOCK * 3 + 5) { (it * 13 % 65_536 - 32_768).toShort() },
            "speech" to speechLike(2.0),
            "short last block" to speechLike(0.01, rate = 1_000),
        )
        for ((name, samples) in cases) {
            try {
                roundTrip(samples)
            } catch (e: AssertionError) {
                throw AssertionError("case '$name' did not round trip", e)
            }
        }
    }

    @Test
    fun `speech with its silences shrinks to well under raw size`() {
        val samples = speechLike(3.0)
        val encoded = roundTrip(samples)
        val raw = samples.size * 2
        assertTrue("encoded ${encoded.size} of raw $raw bytes", encoded.size < raw * 0.6)
    }

    @Test
    fun `noise costs barely more than raw`() {
        val samples = Random(SEED).let { r -> ShortArray(20_000) { r.nextInt().toShort() } }
        val encoded = roundTrip(samples)
        assertTrue("encoded ${encoded.size} bytes", encoded.size <= samples.size * 2 + samples.size / BLOCK + 8)
    }

    @Test
    fun `a truncated stream is reported as corrupt`() {
        val encoded = codec.encode(speechLike(0.5))
        try {
            codec.decode(encoded.copyOf(encoded.size / 2), speechLike(0.5).size)
            fail("decoded a truncated stream")
        } catch (expected: CorruptStudioData) {
            // Expected.
        }
    }

    private companion object {
        const val BLOCK = 4_096
        const val SEED = 42
    }
}
