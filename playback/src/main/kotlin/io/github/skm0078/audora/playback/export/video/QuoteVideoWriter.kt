// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/video/QuoteVideoWriter.kt
// Purpose: Writes a quote video: H.264 frames from the phone's own encoder and the voice in AAC, in one MP4, with no
//          FFmpeg and no new library (ADR 2026-10-28-quote-video).

package io.github.skm0078.audora.playback.export.video

import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.SystemClock
import android.view.Surface
import io.github.skm0078.audora.core.config.QuoteVideoConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.share.VideoTimeline
import io.github.skm0078.audora.speech.audio.PcmAudio
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

/** A written quote video. */
data class QuoteVideo(val file: File, val durationMs: Long, val frameCount: Int, val isCut: Boolean)

/** Why a video could not be made: the phone's encoder or the file failed. [detail] is for the log. */
data class QuoteVideoError(val detail: String)

/**
 * The voice is encoded first, in memory ([VoiceTrack]); then each frame is drawn on the CPU into one reused bitmap,
 * uploaded to the GPU and stamped with its time ([EncoderCanvas]), so the video takes as long as the phone needs
 * and still plays at real speed. A frame that looks like the one before is not drawn again. Audio frames are written
 * between video frames by time.
 *
 * Cost: one 720 x 1280 bitmap (3.7 MB) and the encoder's own buffers; no new dependency (MediaCodec, MediaMuxer and
 * EGL are the platform's). [onProgress] hears each frame handed to the encoder, on the writing thread.
 */
class QuoteVideoWriter(
    private val config: QuoteVideoConfig = QuoteVideoConfig(),
    private val onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
) {
    /**
     * Writes [frames] over [audio] into [target] (replaced), as [timeline] times them, with [label] on every frame.
     * Blocks: call it on one background thread, which the GPU context is bound to. On failure [target] is deleted.
     */
    fun write(
        target: File,
        audio: PcmAudio,
        timeline: VideoTimeline,
        frames: QuoteVideoFrames,
        label: AiVoiceLabel,
    ): Outcome<QuoteVideo, QuoteVideoError> {
        val started = SystemClock.elapsedRealtime()
        return try {
            val voice = VoiceTrack.encode(audio, timeline.durationMs, config)
            val drawn = Session(target, voice, timeline, frames, label).run()
            AudoraLog.i(
                TAG,
                "Wrote ${timeline.frameCount} frames ($drawn drawn), ${timeline.durationMs} ms, " +
                    "in ${SystemClock.elapsedRealtime() - started} ms",
            )
            Outcome.Success(QuoteVideo(target, timeline.durationMs, timeline.frameCount, timeline.isCut))
        } catch (e: IllegalStateException) {
            failed(target, e)
        } catch (e: IllegalArgumentException) {
            failed(target, e)
        } catch (e: IOException) {
            failed(target, e)
        }
    }

    private fun failed(target: File, e: Exception): Outcome.Failure<QuoteVideoError> {
        AudoraLog.e(TAG, "Quote video failed for ${target.name}", e)
        if (target.exists() && !target.delete()) AudoraLog.w(TAG, "Could not delete the broken ${target.name}")
        return Outcome.Failure(QuoteVideoError("the video encoder failed (${e.javaClass.simpleName}: ${e.message})"))
    }

    /** One video being written; everything it opens is released by [run], whatever happens. */
    private inner class Session(
        private val target: File,
        private val voice: VoiceTrack,
        private val timeline: VideoTimeline,
        private val frames: QuoteVideoFrames,
        private val label: AiVoiceLabel,
    ) {
        private val info = MediaCodec.BufferInfo()
        private var muxer: MediaMuxer? = null
        private var videoTrack = NO_TRACK
        private var audioTrack = NO_TRACK
        private var nextAudio = 0
        private var videoWritten = 0

        /** The number of frames drawn (the rest repeat the picture before them). */
        fun run(): Int {
            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            var surface: Surface? = null
            var canvas: EncoderCanvas? = null
            var picture: Bitmap? = null
            var finished = false
            try {
                codec.configure(videoFormat(), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                surface = codec.createInputSurface()
                canvas = EncoderCanvas(surface, config.widthPx, config.heightPx)
                codec.start()
                target.delete()
                muxer = MediaMuxer(target.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                audioTrack = checkNotNull(muxer).addTrack(voice.format)
                picture = Bitmap.createBitmap(config.widthPx, config.heightPx, Bitmap.Config.ARGB_8888)
                val drawn = encodeFrames(codec, canvas, picture)
                codec.signalEndOfInputStream()
                drain(codec, endOfStream = true)
                writeAudioUpTo(Long.MAX_VALUE)
                // A frame the encoder dropped still plays (the one before shows longer); worth knowing, not failing.
                if (videoWritten != timeline.frameCount) {
                    AudoraLog.w(TAG, "Encoder wrote $videoWritten of ${timeline.frameCount} frames")
                }
                finished = true
                return drawn
            } finally {
                canvas?.close()
                surface?.release()
                picture?.recycle()
                release(codec, finished)
            }
        }

        private fun encodeFrames(codec: MediaCodec, canvas: EncoderCanvas, picture: Bitmap): Int {
            val drawing = Canvas(picture)
            var look: Int? = null
            var drawn = 0
            for (frame in 0 until timeline.frameCount) {
                val next = frames.lookOf(frame)
                if (next != look) {
                    frames.draw(drawing, frame)
                    label.draw(drawing)
                    canvas.upload(picture)
                    look = next
                    drawn++
                }
                canvas.present(timeline.timeUs(frame) * NS_PER_US)
                drain(codec, endOfStream = false)
                onProgress(frame + 1, timeline.frameCount)
            }
            return drawn
        }

        private fun videoFormat(): MediaFormat =
            MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, config.widthPx, config.heightPx).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, config.videoBitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, config.framesPerSecond)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, config.keyFrameIntervalS)
            }

        /** Moves encoded frames into the file. With [endOfStream], waits until the encoder's last frame is out. */
        private fun drain(codec: MediaCodec, endOfStream: Boolean) {
            var idle = 0
            while (true) {
                val index = codec.dequeueOutputBuffer(info, if (endOfStream) config.codecTimeoutUs else 0L)
                when {
                    index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (!endOfStream) return
                        check(++idle < config.codecDrainAttempts) { "Video encoder stopped producing output" }
                    }

                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        check(videoTrack == NO_TRACK) { "Video encoder changed its output format twice" }
                        val file = checkNotNull(muxer)
                        videoTrack = file.addTrack(codec.outputFormat)
                        file.start()
                    }

                    index >= 0 -> if (writeVideo(codec, index)) return
                }
            }
        }

        /** True at the end of the stream. */
        private fun writeVideo(codec: MediaCodec, index: Int): Boolean {
            val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
            if (!isConfig && info.size > 0) {
                check(videoTrack != NO_TRACK) { "Video encoder produced frames before its format" }
                writeAudioUpTo(info.presentationTimeUs)
                val output = checkNotNull(codec.getOutputBuffer(index)) { "Video output buffer $index is gone" }
                output.position(info.offset).limit(info.offset + info.size)
                checkNotNull(muxer).writeSampleData(videoTrack, output, info)
                videoWritten++
            }
            codec.releaseOutputBuffer(index, false)
            return info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
        }

        /** The voice's frames up to [timeUs], so the file holds both tracks side by side in time. */
        private fun writeAudioUpTo(timeUs: Long) {
            val file = checkNotNull(muxer)
            check(videoTrack != NO_TRACK) { "Audio before the video's format" }
            while (nextAudio < voice.samples.size && voice.samples[nextAudio].timeUs <= timeUs) {
                val sample = voice.samples[nextAudio++]
                file.writeSampleData(audioTrack, ByteBuffer.wrap(sample.bytes), sample.info())
            }
        }

        /** Stops cleanly after a finished video; after a failure only frees (the half-written file is deleted). */
        private fun release(codec: MediaCodec, finished: Boolean) {
            try {
                if (finished) codec.stop()
            } finally {
                codec.release()
                muxer?.let { closeFile(it, finished) }
            }
        }

        private fun closeFile(file: MediaMuxer, finished: Boolean) {
            try {
                // stop() writes the file's index, so a finished video is playable once it returns.
                if (finished) file.stop()
            } finally {
                file.release()
            }
        }
    }

    private companion object {
        const val TAG = "Share.Video"
        const val NO_TRACK = -1
        const val NS_PER_US = 1_000L
    }
}
