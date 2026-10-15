// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/audio/WavEncoderTest.kt
// Purpose: The WAV header carries each sentence's own sample rate and the exact data length.

package io.github.skm0078.audora.playback.audio

import io.github.skm0078.audora.speech.audio.PcmAudio
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class WavEncoderTest {
    @Test
    fun headerAndSamplesAreLittleEndianPcm16() {
        val wav = WavEncoder.encode(PcmAudio(shortArrayOf(1, -2, 300), 22_050))
        val buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(WavEncoder.HEADER_BYTES + 6, wav.size)
        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals(wav.size - 8, buffer.getInt(4))
        assertEquals("WAVE", String(wav, 8, 4, Charsets.US_ASCII))
        assertEquals(1, buffer.getShort(22).toInt()) // mono
        assertEquals(22_050, buffer.getInt(24))
        assertEquals(44_100, buffer.getInt(28)) // byte rate
        assertEquals(16, buffer.getShort(34).toInt())
        assertEquals("data", String(wav, 36, 4, Charsets.US_ASCII))
        assertEquals(6, buffer.getInt(40))
        assertEquals(listOf<Short>(1, -2, 300), listOf(buffer.getShort(44), buffer.getShort(46), buffer.getShort(48)))
    }

    @Test
    fun silenceIsAValidEmptyWav() {
        val wav = WavEncoder.encode(PcmAudio.silence(24_000))
        assertEquals(WavEncoder.HEADER_BYTES, wav.size)
        assertEquals(0, ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN).getInt(40))
    }
}
