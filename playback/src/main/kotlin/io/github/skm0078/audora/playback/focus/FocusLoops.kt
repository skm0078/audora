// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/focus/FocusLoops.kt
// Purpose: Decodes the recorded focus-sound loops (Q-1) from the app's assets, once, at the voice's sample rate.

package io.github.skm0078.audora.playback.focus

import android.content.res.AssetManager
import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import io.github.skm0078.audora.core.config.FocusSoundConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.FocusSound
import java.io.IOException
import java.nio.ByteOrder
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * The service's [FocusBeds]: brown noise is made on the phone ([FocusNoise]); rain, water, birds, fire and wind are
 * Opus loops in `assets/focus/`. A loop is decoded and resampled on [loader] the first time it is asked for at a rate,
 * and played from memory after that: about 2 MB a minute of loop at 16 kHz, 5 MB at 44.1 kHz. Only the loop in use is
 * kept; another sound or rate replaces it. Until it is ready its source is silent; it then fades in
 * ([FocusSoundConfig.loopFadeInMs]). A loop that can't be decoded is logged and stays silent: never a crash, never a
 * different sound.
 */
class FocusLoops(
    private val assets: AssetManager,
    private val config: FocusSoundConfig = FocusSoundConfig(),
    private val loader: Executor = Executors.newSingleThreadExecutor {
        Thread(it, "focus-loops").apply { isDaemon = true }
    },
) : FocusBeds {
    private var current: Loaded? = null

    override fun of(sound: FocusSound, sampleRate: Int, seed: Long): NoiseSource? {
        val path = FocusLoopAssets.pathOf(sound) ?: return FocusNoise.of(sound, sampleRate, seed)
        val loop = loaded(path, sampleRate)
        return LoopSource(loop::pcm, (sampleRate.toLong() * config.loopFadeInMs / MS_PER_SECOND).toInt())
    }

    @Synchronized
    private fun loaded(path: String, rate: Int): Loaded {
        current?.takeIf { it.path == path && it.rate == rate }?.let { return it }
        return Loaded(path, rate).also { loop ->
            current = loop
            loader.execute { loop.pcm = decode(path, rate) }
        }
    }

    /** Null (logged) when the loop can't be read: the sound stays silent. */
    private fun decode(path: String, rate: Int): ShortArray? = try {
        val started = System.nanoTime()
        val bytes = assets.open(path).use { it.readBytes() }
        val extractor = MediaExtractor()
        val decoded = try {
            extractor.setDataSource(BytesSource(bytes))
            decodeFirstAudioTrack(extractor)
        } finally {
            extractor.release()
        }
        FocusResampler.resample(decoded.pcm, decoded.rate, rate).also { pcm ->
            val ms = (System.nanoTime() - started) / NANOS_PER_MS
            AudoraLog.i(TAG, "Loop $path ready at $rate Hz: ${pcm.size} samples in $ms ms")
        }
    } catch (e: IOException) {
        AudoraLog.e(TAG, "Loop $path could not be read; the sound stays silent", e)
        null
    } catch (e: IllegalStateException) {
        AudoraLog.e(TAG, "Loop $path could not be decoded; the sound stays silent", e)
        null
    } catch (e: IllegalArgumentException) {
        AudoraLog.e(TAG, "Loop $path has a format the phone can't decode; the sound stays silent", e)
        null
    }

    /** A loop decoded or being decoded; [pcm] is written once by the loader and read by the audio thread. */
    private class Loaded(val path: String, val rate: Int) {
        @Volatile
        var pcm: ShortArray? = null
    }

    private class Decoded(val pcm: ShortArray, val rate: Int)

    /** Mono 16-bit PCM of the file's audio track, its channels averaged. */
    @Suppress("NestedBlockDepth") // MediaCodec's input and output loop.
    private fun decodeFirstAudioTrack(extractor: MediaExtractor): Decoded {
        val track = (0 until extractor.trackCount).firstOrNull { i ->
            extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: throw IOException("no audio track")
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val codec = MediaCodec.createDecoderByType(requireNotNull(format.getString(MediaFormat.KEY_MIME)))
        val out = PcmBuilder()
        var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        try {
            codec.configure(format, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val deadline = System.nanoTime() + DECODE_LIMIT_NANOS
            while (!outputDone) {
                check(System.nanoTime() < deadline) {
                    "decoding took longer than ${DECODE_LIMIT_NANOS / NANOS_PER_MS} ms"
                }
                if (!inputDone) inputDone = feed(extractor, codec)
                when (val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> codec.outputFormat.let { changed ->
                        rate = changed.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = changed.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }

                    in 0..Int.MAX_VALUE -> {
                        val buffer = requireNotNull(codec.getOutputBuffer(index)).order(ByteOrder.nativeOrder())
                        buffer.position(info.offset).limit(info.offset + info.size)
                        out.addFrames(buffer.asShortBuffer(), channels)
                        codec.releaseOutputBuffer(index, false)
                        outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    }
                }
            }
        } finally {
            codec.release()
        }
        return Decoded(out.build(), rate)
    }

    /** Queues the next compressed sample; true once the end of the file is queued. */
    private fun feed(extractor: MediaExtractor, codec: MediaCodec): Boolean {
        val index = codec.dequeueInputBuffer(TIMEOUT_US)
        if (index < 0) return false
        val size = extractor.readSampleData(requireNotNull(codec.getInputBuffer(index)), 0)
        return if (size < 0) {
            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            true
        } else {
            codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
            extractor.advance()
            false
        }
    }

    private companion object {
        const val TAG = "Playback.FocusLoops"
        const val TIMEOUT_US = 10_000L
        const val MS_PER_SECOND = 1_000L
        const val NANOS_PER_MS = 1_000_000L

        /** A minute of Opus decodes in well under a second; a codec that never ends its stream is given up on. */
        const val DECODE_LIMIT_NANOS = 20_000_000_000L
    }

    /** The asset's bytes for MediaExtractor: works whether or not the APK stored the asset compressed. */
    private class BytesSource(private val bytes: ByteArray) : MediaDataSource() {
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= bytes.size) return END_OF_DATA
            val count = minOf(size.toLong(), bytes.size - position).toInt()
            System.arraycopy(bytes, position.toInt(), buffer, offset, count)
            return count
        }

        override fun getSize(): Long = bytes.size.toLong()

        override fun close() = Unit

        private companion object {
            const val END_OF_DATA = -1
        }
    }
}
