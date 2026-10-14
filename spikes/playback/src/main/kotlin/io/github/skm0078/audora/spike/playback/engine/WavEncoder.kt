// File: spikes/playback/src/main/kotlin/io/github/skm0078/audora/spike/playback/engine/WavEncoder.kt
// Purpose: Wraps 16-bit mono PCM in a WAV header so ExoPlayer's stock WavExtractor can play it.

package io.github.skm0078.audora.spike.playback.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavEncoder {
    const val HEADER_BYTES = 44
    private const val BITS_PER_SAMPLE = 16
    private const val BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8
    private const val CHANNELS = 1
    private const val FMT_CHUNK_BYTES = 16
    private const val PCM_FORMAT: Short = 1

    /** RIFF size field excludes the 8-byte "RIFF" + size prefix. */
    private const val RIFF_PREFIX_BYTES = 8

    fun encode(pcm: ShortArray, sampleRateHz: Int): ByteArray {
        require(sampleRateHz > 0) { "sampleRateHz must be positive, was $sampleRateHz" }
        val dataBytes = pcm.size * BYTES_PER_SAMPLE
        val buffer = ByteBuffer.allocate(HEADER_BYTES + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(HEADER_BYTES + dataBytes - RIFF_PREFIX_BYTES)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(FMT_CHUNK_BYTES)
        buffer.putShort(PCM_FORMAT)
        buffer.putShort(CHANNELS.toShort())
        buffer.putInt(sampleRateHz)
        buffer.putInt(sampleRateHz * CHANNELS * BYTES_PER_SAMPLE)
        buffer.putShort((CHANNELS * BYTES_PER_SAMPLE).toShort())
        buffer.putShort(BITS_PER_SAMPLE.toShort())
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataBytes)
        buffer.asShortBuffer().put(pcm)
        return buffer.array()
    }
}
