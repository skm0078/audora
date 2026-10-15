// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/audio/WavReaderTest.kt
// Purpose: The system TTS output parser: plain files, extra chunks, streaming sizes and every rejection path.

package io.github.skm0078.audora.speech.audio

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WavReaderTest {
    @Test
    fun `reads a plain 16-bit mono file`() {
        val audio = WavReader.read(wav(samples = shortArrayOf(1, -2, 300)))

        assertEquals(24_000, audio.sampleRate)
        assertArrayEquals(shortArrayOf(1, -2, 300), audio.samples)
    }

    @Test
    fun `skips chunks before data, including odd-sized ones`() {
        val audio = WavReader.read(wav(samples = shortArrayOf(5, 6), extraChunk = "LIST" to ByteArray(3)))

        assertArrayEquals(shortArrayOf(5, 6), audio.samples)
    }

    @Test
    fun `streaming writers that leave the data size at 0 or -1 are read to the end of the file`() {
        for (size in listOf(0, -1)) {
            val audio = WavReader.read(wav(samples = shortArrayOf(9, 8, 7), dataSize = size))
            assertArrayEquals("size $size", shortArrayOf(9, 8, 7), audio.samples)
        }
    }

    @Test
    fun `rejects what is not 16-bit mono PCM WAV`() {
        val cases = mapOf(
            "too short" to ByteArray(10),
            "not RIFF" to wav(samples = shortArrayOf(1)).also { it[0] = 'X'.code.toByte() },
            "stereo" to wav(samples = shortArrayOf(1, 2), channels = 2),
            "8-bit" to wav(samples = shortArrayOf(1), bits = 8),
            "float format" to wav(samples = shortArrayOf(1), format = 3),
            "no data chunk" to wav(samples = shortArrayOf(1), dataId = "junk"),
        )
        for ((name, bytes) in cases) {
            try {
                WavReader.read(bytes)
                fail("$name: expected IOException")
            } catch (e: IOException) {
                assertTrue("$name: ${e.message}", e.message.orEmpty().isNotBlank())
            }
        }
    }

    @Suppress("LongParameterList")
    private fun wav(
        samples: ShortArray,
        rate: Int = 24_000,
        channels: Int = 1,
        bits: Int = 16,
        format: Int = 1,
        extraChunk: Pair<String, ByteArray>? = null,
        dataId: String = "data",
        dataSize: Int = samples.size * 2,
    ): ByteArray {
        val body = ByteArrayOutputStream()
        fun chunk(id: String, payload: ByteArray, size: Int = payload.size) {
            body.write(id.toByteArray(Charsets.US_ASCII))
            body.write(le(size))
            body.write(payload)
            if (payload.size % 2 == 1) body.write(0)
        }
        val fmt = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(format.toShort()).putShort(channels.toShort()).putInt(rate)
            .putInt(rate * channels * bits / 8).putShort((channels * bits / 8).toShort()).putShort(bits.toShort())
        chunk("fmt ", fmt.array())
        extraChunk?.let { (id, payload) -> chunk(id, payload) }
        val pcm = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { pcm.putShort(it) }
        chunk(dataId, pcm.array(), dataSize)
        val all = ByteArrayOutputStream()
        all.write("RIFF".toByteArray(Charsets.US_ASCII))
        all.write(le(body.size() + 4))
        all.write("WAVE".toByteArray(Charsets.US_ASCII))
        all.write(body.toByteArray())
        return all.toByteArray()
    }

    private fun le(value: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
}
