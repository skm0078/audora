// File: spikes/playback/src/test/kotlin/io/github/skm0078/audora/spike/playback/engine/WavEncoderTest.kt
// Purpose: The WAV header must match what ExoPlayer's WavExtractor expects, byte for byte.

package io.github.skm0078.audora.spike.playback.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class WavEncoderTest {
    @Test
    fun `header describes 16-bit mono PCM at the given rate`() {
        val bytes = WavEncoder.encode(shortArrayOf(1, -2, 3), sampleRateHz = 22_050)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals(44 + 6, bytes.size)
        assertEquals("RIFF", ascii(bytes, 0))
        assertEquals(bytes.size - 8, buffer.getInt(4))
        assertEquals("WAVE", ascii(bytes, 8))
        assertEquals("fmt ", ascii(bytes, 12))
        assertEquals(16, buffer.getInt(16))
        assertEquals(1, buffer.getShort(20).toInt()) // PCM
        assertEquals(1, buffer.getShort(22).toInt()) // mono
        assertEquals(22_050, buffer.getInt(24))
        assertEquals(44_100, buffer.getInt(28)) // byte rate
        assertEquals(2, buffer.getShort(32).toInt()) // block align
        assertEquals(16, buffer.getShort(34).toInt())
        assertEquals("data", ascii(bytes, 36))
        assertEquals(6, buffer.getInt(40))
    }

    @Test
    fun `samples follow the header in little-endian order`() {
        val bytes = WavEncoder.encode(shortArrayOf(0x0102, -1), sampleRateHz = 8_000)

        assertEquals(0x02.toByte(), bytes[44])
        assertEquals(0x01.toByte(), bytes[45])
        assertEquals(0xFF.toByte(), bytes[46])
        assertEquals(0xFF.toByte(), bytes[47])
    }

    private fun ascii(bytes: ByteArray, offset: Int) = String(bytes, offset, 4, Charsets.US_ASCII)
}
