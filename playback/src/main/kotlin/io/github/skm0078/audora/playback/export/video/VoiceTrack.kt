// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/video/VoiceTrack.kt
// Purpose: A quote video's voice as AAC-LC frames held in memory, ready to be woven between the video's frames.

package io.github.skm0078.audora.playback.export.video

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import io.github.skm0078.audora.core.config.QuoteVideoConfig
import io.github.skm0078.audora.speech.audio.PcmAudio
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** One encoded AAC frame: its bytes, its time and its flags, as the muxer takes them. */
internal class EncodedSample(val bytes: ByteArray, val timeUs: Long, val flags: Int) {
    fun info(): MediaCodec.BufferInfo = MediaCodec.BufferInfo().apply { set(0, bytes.size, timeUs, flags) }
}

/**
 * The voice encoded whole before the first video frame: a minute of mono speech at 64 kbit/s is about 480 KB, and
 * holding it lets the muxer interleave audio with video by time instead of writing one track after the other.
 */
internal class VoiceTrack private constructor(val format: MediaFormat, val samples: List<EncodedSample>) {
    companion object {
        private const val CHANNELS = 1
        private const val BYTES_PER_SAMPLE = 2
        private const val EVEN_MASK = 1.inv()
        private const val US_PER_S = 1_000_000L
        private const val MS_PER_S = 1_000L

        /**
         * Encodes [audio] up to [durationMs]. Throws [IllegalStateException] (CodecException is one) or
         * [IllegalArgumentException] if the phone's AAC encoder fails, as MediaCodec does.
         */
        fun encode(audio: PcmAudio, durationMs: Long, config: QuoteVideoConfig): VoiceTrack {
            val rate = audio.sampleRate
            val kept = minOf(audio.samples.size.toLong(), durationMs * rate / MS_PER_S).toInt()
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, CHANNELS).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, config.aacBitRate)
            }
            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            try {
                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                codec.start()
                return Encoding(codec, rate, config).run(audio.samples, kept)
            } finally {
                codec.release()
            }
        }

        private class Encoding(val codec: MediaCodec, val rate: Int, val config: QuoteVideoConfig) {
            private val info = MediaCodec.BufferInfo()
            private val samples = mutableListOf<EncodedSample>()
            private var format: MediaFormat? = null
            private var queued = 0L

            fun run(pcm: ShortArray, count: Int): VoiceTrack {
                val bytes = ByteBuffer.allocate(count * BYTES_PER_SAMPLE).order(ByteOrder.LITTLE_ENDIAN)
                bytes.asShortBuffer().put(pcm, 0, count)
                while (bytes.hasRemaining()) {
                    val index = codec.dequeueInputBuffer(config.codecTimeoutUs)
                    if (index >= 0) queue(index, bytes)
                    drain(endOfStream = false)
                }
                var index = codec.dequeueInputBuffer(config.codecTimeoutUs)
                var attempts = 1
                while (index < 0) {
                    check(attempts++ < config.codecDrainAttempts) { "AAC encoder never took the end of the stream" }
                    drain(endOfStream = false)
                    index = codec.dequeueInputBuffer(config.codecTimeoutUs)
                }
                codec.queueInputBuffer(index, 0, 0, timeUs(), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                drain(endOfStream = true)
                codec.stop()
                return VoiceTrack(checkNotNull(format) { "AAC encoder gave no format" }, samples)
            }

            private fun queue(index: Int, bytes: ByteBuffer) {
                val input = checkNotNull(codec.getInputBuffer(index)) { "AAC input buffer $index is gone" }
                input.clear()
                // Whole samples only, as in AacChapterWriter: a split sample would shift every later one by a byte.
                val size = minOf(bytes.remaining(), input.remaining()) and EVEN_MASK
                input.put(bytes.duplicate().apply { limit(position() + size) })
                bytes.position(bytes.position() + size)
                codec.queueInputBuffer(index, 0, size, timeUs(), 0)
                queued += size / BYTES_PER_SAMPLE
            }

            private fun drain(endOfStream: Boolean) {
                var idle = 0
                while (true) {
                    val index = codec.dequeueOutputBuffer(info, config.codecTimeoutUs)
                    when {
                        index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!endOfStream) return
                            check(++idle < config.codecDrainAttempts) { "AAC encoder stopped producing output" }
                        }

                        index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> format = codec.outputFormat

                        index >= 0 -> if (keep(index)) return
                    }
                }
            }

            /** True at the end of the stream. */
            private fun keep(index: Int): Boolean {
                val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                if (!isConfig && info.size > 0) {
                    val output = checkNotNull(codec.getOutputBuffer(index)) { "AAC output buffer $index is gone" }
                    output.position(info.offset).limit(info.offset + info.size)
                    val bytes = ByteArray(info.size).also { output.get(it) }
                    val flags = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM.inv()
                    samples += EncodedSample(bytes, info.presentationTimeUs, flags)
                }
                codec.releaseOutputBuffer(index, false)
                return info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
            }

            private fun timeUs(): Long = queued * US_PER_S / rate
        }
    }
}
