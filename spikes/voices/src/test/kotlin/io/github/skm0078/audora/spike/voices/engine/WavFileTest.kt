// File: spikes/voices/src/test/kotlin/io/github/skm0078/audora/spike/voices/engine/WavFileTest.kt
// Purpose: WAV round trip, extra chunks from system TTS writers, and float clipping.

package io.github.skm0078.audora.spike.voices.engine

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WavFileTest {
    @Test
    fun `round trips PCM and sample rate`() {
        val pcm = shortArrayOf(0, 1000, -1000, Short.MAX_VALUE, Short.MIN_VALUE)

        val read = WavFile.read(WavFile.encode(pcm, 24_000))

        assertEquals(24_000, read.sampleRate)
        assertArrayEquals(pcm, read.pcm)
    }

    @Test
    fun `skips chunks before data`() {
        val plain = WavFile.encode(shortArrayOf(7, 8), 16_000)
        val list = ByteBuffer.allocate(8 + 4).order(ByteOrder.LITTLE_ENDIAN)
            .put("LIST".toByteArray()).putInt(4).put("INFO".toByteArray()).array()
        // Insert the LIST chunk between "fmt " (ends at byte 36) and "data".
        val withList = plain.copyOfRange(0, 36) + list + plain.copyOfRange(36, plain.size)

        val read = WavFile.read(withList)

        assertArrayEquals(shortArrayOf(7, 8), read.pcm)
    }

    @Test
    fun `trusts the file length when the data size is unset`() {
        val bytes = WavFile.encode(shortArrayOf(1, 2, 3), 22_050)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(40, -1)

        assertArrayEquals(shortArrayOf(1, 2, 3), WavFile.read(bytes).pcm)
    }

    @Test
    fun `rejects stereo`() {
        val bytes = WavFile.encode(shortArrayOf(1, 2), 22_050)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putShort(22, 2)

        assertThrows(IOException::class.java) { WavFile.read(bytes) }
    }

    @Test
    fun `clips floats outside -1 to 1`() {
        val pcm = WavFile.floatToPcm16(floatArrayOf(2f, -2f, 0f))

        assertArrayEquals(shortArrayOf(Short.MAX_VALUE, (-Short.MAX_VALUE).toShort(), 0), pcm)
    }

    @Test
    fun `audio length follows sample count and rate`() {
        assertEquals(500L, Rendered(ShortArray(8_000), 16_000).audioMs)
    }
}
