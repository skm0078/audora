// File: spikes/voices/src/main/kotlin/io/github/skm0078/audora/spike/voices/engine/WavFile.kt
// Purpose: Float → 16-bit PCM conversion and minimal WAV read/write: sherpa-onnx returns floats, the system TTS
//          writes WAV files, and the listening test plays WAV files back.

package io.github.skm0078.audora.spike.voices.engine

import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavFile {
    const val HEADER_BYTES = 44
    private const val PCM_FORMAT: Short = 1
    private const val BITS_PER_SAMPLE: Short = 16
    private const val BYTES_PER_SAMPLE = 2
    private const val FMT_CHUNK_BYTES = 16
    private const val RIFF_PREFIX_BYTES = 8
    private const val FOURCC_BYTES = 4
    private const val CHUNK_HEADER_BYTES = 8
    private const val RIFF_FORM_BYTES = 12
    private const val FMT_CHANNELS_OFFSET = 2
    private const val FMT_RATE_OFFSET = 4
    private const val FMT_BITS_OFFSET = 14
    private const val PCM_MAX = Short.MAX_VALUE.toFloat()

    fun floatToPcm16(samples: FloatArray): ShortArray =
        ShortArray(samples.size) { i -> (samples[i].coerceIn(-1f, 1f) * PCM_MAX).toInt().toShort() }

    fun encode(pcm: ShortArray, sampleRate: Int): ByteArray {
        require(sampleRate > 0) { "sampleRate must be positive, was $sampleRate" }
        val dataBytes = pcm.size * BYTES_PER_SAMPLE
        val buffer = ByteBuffer.allocate(HEADER_BYTES + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(HEADER_BYTES + dataBytes - RIFF_PREFIX_BYTES)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(FMT_CHUNK_BYTES)
        buffer.putShort(PCM_FORMAT)
        buffer.putShort(1)
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * BYTES_PER_SAMPLE)
        buffer.putShort(BYTES_PER_SAMPLE.toShort())
        buffer.putShort(BITS_PER_SAMPLE)
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataBytes)
        buffer.asShortBuffer().put(pcm)
        return buffer.array()
    }

    fun write(file: File, pcm: ShortArray, sampleRate: Int) {
        file.parentFile?.mkdirs()
        file.writeBytes(encode(pcm, sampleRate))
    }

    /** Reads a 16-bit mono PCM WAV, walking chunks so LIST/fact chunks before "data" are skipped. */
    fun read(bytes: ByteArray): Rendered {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (bytes.size < HEADER_BYTES || ascii(bytes, 0) != "RIFF" || ascii(bytes, RIFF_PREFIX_BYTES) != "WAVE") {
            fail("Not a RIFF/WAVE file (${bytes.size} bytes)")
        }
        var offset = RIFF_FORM_BYTES
        var sampleRate = 0
        while (offset + CHUNK_HEADER_BYTES <= bytes.size) {
            val id = ascii(bytes, offset)
            val size = buffer.getInt(offset + FOURCC_BYTES)
            val body = offset + CHUNK_HEADER_BYTES
            when (id) {
                "fmt " -> sampleRate = readFormat(buffer, body)
                "data" -> return readData(bytes, body, size, sampleRate)
            }
            if (size < 0) fail("Chunk '$id' has an invalid size")
            offset = body + size + (size and 1) // chunks are word-aligned
        }
        fail("No data chunk")
    }

    /** Validates the fmt chunk at [body] and returns its sample rate. */
    private fun readFormat(buffer: ByteBuffer, body: Int): Int {
        val channels = buffer.getShort(body + FMT_CHANNELS_OFFSET).toInt()
        val bits = buffer.getShort(body + FMT_BITS_OFFSET)
        val mono16 = channels == 1 && bits == BITS_PER_SAMPLE
        if (!mono16) fail("Expected 16-bit mono, got $bits-bit with $channels channels")
        return buffer.getInt(body + FMT_RATE_OFFSET)
    }

    private fun readData(bytes: ByteArray, body: Int, size: Int, sampleRate: Int): Rendered {
        if (sampleRate == 0) fail("data chunk before fmt chunk")
        // Some writers leave the size at 0 or 0xFFFFFFFF while streaming; trust the file length then.
        val available = bytes.size - body
        val length = if (size in 1..available) size else available
        val pcm = ShortArray(length / BYTES_PER_SAMPLE)
        val data = ByteBuffer.wrap(bytes, body, pcm.size * BYTES_PER_SAMPLE).order(ByteOrder.LITTLE_ENDIAN)
        data.asShortBuffer().get(pcm)
        return Rendered(pcm, sampleRate)
    }

    private fun fail(message: String): Nothing = throw IOException(message)

    private fun ascii(bytes: ByteArray, offset: Int) = String(bytes, offset, FOURCC_BYTES, Charsets.US_ASCII)
}
