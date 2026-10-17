// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/AacChapterWriter.kt
// Purpose: Encodes one chapter's 16-bit mono PCM to AAC-LC in an .m4a file with the platform's own codec.

package io.github.skm0078.audora.playback.export

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import io.github.skm0078.audora.core.config.AudiobookConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.audio.PcmAudio
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * MediaCodec (AAC-LC, every Android phone has an encoder) into MediaMuxer (MP4): no new dependency, no native
 * library of ours (ADR-034). .m4a plays in every audiobook app and in Media3 itself.
 *
 * MediaCodec and MediaMuxer report failure with [IllegalStateException] (CodecException is one) and
 * [IOException]; both become [ExportError.Storage] with the chapter in the log.
 */
class AacChapterWriter private constructor(
    private val chapter: AudioChapter,
    private val target: ChapterFile,
    private val codec: MediaCodec,
    private val muxer: MediaMuxer,
    override val sampleRate: Int,
    private val config: AudiobookConfig,
) : ChapterAudioWriter {
    private val info = MediaCodec.BufferInfo()
    private var track = NO_TRACK
    private var samplesQueued = 0L
    private var released = false

    override fun write(audio: PcmAudio): Outcome<Unit, ExportError.Storage> = guarded("write") {
        require(audio.sampleRate == sampleRate) { "Audio at ${audio.sampleRate} Hz in a $sampleRate Hz file" }
        val bytes = ByteBuffer.allocate(audio.samples.size * BYTES_PER_SAMPLE).order(ByteOrder.LITTLE_ENDIAN)
        bytes.asShortBuffer().put(audio.samples)
        while (bytes.hasRemaining()) {
            val index = codec.dequeueInputBuffer(config.codecTimeoutUs)
            if (index >= 0) queue(index, bytes)
            drain(endOfStream = false)
        }
    }

    override fun finish(): Outcome<ExportedChapter, ExportError.Storage> = guarded("finish") {
        var index = codec.dequeueInputBuffer(config.codecTimeoutUs)
        var attempts = 1
        while (index < 0) {
            check(attempts++ < config.codecDrainAttempts) { "Encoder never took the end of the stream" }
            drain(endOfStream = false)
            index = codec.dequeueInputBuffer(config.codecTimeoutUs)
        }
        codec.queueInputBuffer(index, 0, 0, presentationUs(), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        drain(endOfStream = true)
        release()
        val published = target.publish()
        if (published is Outcome.Failure) throw IOException(published.error.detail)
        ExportedChapter(chapter, name = target.name, durationMs = samplesQueued * MS_PER_S / sampleRate)
    }

    override fun abort() {
        try {
            release()
        } catch (e: IllegalStateException) {
            AudoraLog.w(TAG, "Encoder release failed while aborting ${target.name}", e)
        }
        target.discard()
    }

    private fun queue(index: Int, bytes: ByteBuffer) {
        val input = checkNotNull(codec.getInputBuffer(index)) { "Encoder input buffer $index is gone" }
        input.clear()
        // Whole samples only: a 16-bit sample split across two buffers would shift every later one by a byte.
        val size = minOf(bytes.remaining(), input.remaining()) and EVEN_MASK
        val slice = bytes.duplicate().apply { limit(position() + size) }
        input.put(slice)
        bytes.position(bytes.position() + size)
        codec.queueInputBuffer(index, 0, size, presentationUs(), 0)
        samplesQueued += size / BYTES_PER_SAMPLE
    }

    /** Moves encoded frames into the file. With [endOfStream], waits until the encoder's last frame is out. */
    private fun drain(endOfStream: Boolean) {
        var idle = 0
        while (true) {
            val index = codec.dequeueOutputBuffer(info, config.codecTimeoutUs)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) return
                    check(++idle < config.codecDrainAttempts) { "Encoder stopped producing output" }
                }

                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    check(track == NO_TRACK) { "Encoder changed its output format twice" }
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                }

                index >= 0 -> if (writeFrame(index)) return
            }
        }
    }

    /** True at the end of the stream. */
    private fun writeFrame(index: Int): Boolean {
        val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
        if (!isConfig && info.size > 0) {
            check(track != NO_TRACK) { "Encoder produced audio before its format" }
            val output = checkNotNull(codec.getOutputBuffer(index)) { "Encoder output buffer $index is gone" }
            output.position(info.offset).limit(info.offset + info.size)
            muxer.writeSampleData(track, output, info)
        }
        codec.releaseOutputBuffer(index, false)
        return info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
    }

    private fun presentationUs(): Long = samplesQueued * US_PER_S / sampleRate

    private fun release() {
        if (released) return
        released = true
        try {
            codec.stop()
        } finally {
            codec.release()
            try {
                if (track != NO_TRACK) muxer.stop()
            } finally {
                muxer.release()
            }
        }
    }

    private inline fun <T> guarded(step: String, block: () -> T): Outcome<T, ExportError.Storage> = try {
        Outcome.Success(block())
    } catch (e: IllegalStateException) {
        failed(step, e)
    } catch (e: IllegalArgumentException) {
        failed(step, e)
    } catch (e: IOException) {
        failed(step, e)
    }

    private fun failed(step: String, e: Exception): Outcome.Failure<ExportError.Storage> {
        AudoraLog.e(TAG, "Encoder $step failed for ${target.name} at $sampleRate Hz", e)
        return Outcome.Failure(ExportError.Storage("the audio encoder failed (${e.javaClass.simpleName})"))
    }

    companion object {
        private const val TAG = "Export.Aac"
        private const val NO_TRACK = -1
        private const val BYTES_PER_SAMPLE = 2
        private const val EVEN_MASK = 1.inv()
        private const val CHANNELS = 1
        private const val MS_PER_S = 1_000L
        private const val US_PER_S = 1_000_000L

        /** Starts the encoder into [target]. The caller owns the result and must finish or abort it. */
        fun create(
            chapter: AudioChapter,
            target: ChapterFile,
            sampleRate: Int,
            config: AudiobookConfig = AudiobookConfig(),
        ): Outcome<ChapterAudioWriter, ExportError.Storage> {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, CHANNELS).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, config.aacBitRate)
            }
            var codec: MediaCodec? = null
            return try {
                codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                codec.start()
                val muxer = MediaMuxer(target.descriptor.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                Outcome.Success(AacChapterWriter(chapter, target, codec, muxer, sampleRate, config))
            } catch (e: IOException) {
                createFailed(target, sampleRate, codec, e)
            } catch (e: IllegalStateException) {
                createFailed(target, sampleRate, codec, e)
            } catch (e: IllegalArgumentException) {
                createFailed(target, sampleRate, codec, e)
            }
        }

        private fun createFailed(
            target: ChapterFile,
            sampleRate: Int,
            codec: MediaCodec?,
            e: Exception,
        ): Outcome.Failure<ExportError.Storage> {
            AudoraLog.e(TAG, "Could not start the AAC encoder for ${target.name} at $sampleRate Hz", e)
            codec?.release()
            target.discard()
            return Outcome.Failure(ExportError.Storage("the audio encoder could not start (${e.javaClass.simpleName})"))
        }
    }
}
