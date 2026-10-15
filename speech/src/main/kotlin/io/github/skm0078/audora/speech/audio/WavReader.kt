// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/audio/WavReader.kt
// Purpose: Reads the 16-bit mono WAV files the phone's TTS engine writes with synthesizeToFile.

package io.github.skm0078.audora.speech.audio

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavReader {
    private const val MIN_BYTES = 44
    private const val PCM_FORMAT: Short = 1
    private const val BITS_PER_SAMPLE: Short = 16
    private const val BYTES_PER_SAMPLE = 2
    private const val FOURCC_BYTES = 4
    private const val CHUNK_HEADER_BYTES = 8
    private const val RIFF_FORM_BYTES = 12
    private const val WAVE_OFFSET = 8
    private const val FMT_CHANNELS_OFFSET = 2
    private const val FMT_RATE_OFFSET = 4
    private const val FMT_BITS_OFFSET = 14
    private const val FMT_MIN_BYTES = 16

    /**
     * Parses a RIFF/WAVE file, walking chunks so LIST/fact chunks before "data" are skipped.
     *
     * @throws IOException when the bytes are not 16-bit mono PCM WAV. The message says what was found.
     */
    fun read(bytes: ByteArray): PcmAudio {
        if (bytes.size < MIN_BYTES || ascii(bytes, 0) != "RIFF" || ascii(bytes, WAVE_OFFSET) != "WAVE") {
            fail("Not a RIFF/WAVE file (${bytes.size} bytes)")
        }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var offset = RIFF_FORM_BYTES
        var sampleRate = 0
        while (offset + CHUNK_HEADER_BYTES <= bytes.size) {
            val id = ascii(bytes, offset)
            val size = buffer.getInt(offset + FOURCC_BYTES)
            val body = offset + CHUNK_HEADER_BYTES
            when (id) {
                "fmt " -> sampleRate = readFormat(buffer, body, size)
                "data" -> return readData(bytes, body, size, sampleRate)
            }
            if (size < 0) fail("Chunk '$id' has an invalid size")
            offset = body + size + (size and 1) // chunks are word-aligned
        }
        fail("No data chunk")
    }

    private fun readFormat(buffer: ByteBuffer, body: Int, size: Int): Int {
        if (size < FMT_MIN_BYTES || body + FMT_MIN_BYTES > buffer.limit()) fail("fmt chunk too short ($size bytes)")
        val format = buffer.getShort(body)
        val channels = buffer.getShort(body + FMT_CHANNELS_OFFSET).toInt()
        val bits = buffer.getShort(body + FMT_BITS_OFFSET)
        if (format != PCM_FORMAT || channels != 1 || bits != BITS_PER_SAMPLE) {
            fail("Expected 16-bit mono PCM, got format $format, $bits-bit, $channels channels")
        }
        val rate = buffer.getInt(body + FMT_RATE_OFFSET)
        if (rate <= 0) fail("Invalid sample rate $rate")
        return rate
    }

    private fun readData(bytes: ByteArray, body: Int, size: Int, sampleRate: Int): PcmAudio {
        if (sampleRate == 0) fail("data chunk before fmt chunk")
        // Engines that stream to the file may leave the size at 0 or 0xFFFFFFFF; trust the file length then.
        val available = bytes.size - body
        val length = if (size in 1..available) size else available
        val pcm = ShortArray(length / BYTES_PER_SAMPLE)
        ByteBuffer.wrap(
            bytes,
            body,
            pcm.size * BYTES_PER_SAMPLE,
        ).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm)
        return PcmAudio(pcm, sampleRate)
    }

    private fun fail(message: String): Nothing = throw IOException(message)

    private fun ascii(bytes: ByteArray, offset: Int) = String(bytes, offset, FOURCC_BYTES, Charsets.US_ASCII)
}
