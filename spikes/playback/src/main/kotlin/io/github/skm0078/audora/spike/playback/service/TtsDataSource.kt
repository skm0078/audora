// File: spikes/playback/src/main/kotlin/io/github/skm0078/audora/spike/playback/service/TtsDataSource.kt
// Purpose: Serves a unit's synthesized audio as WAV bytes, blocking until the render queue has it (C5).

package io.github.skm0078.audora.spike.playback.service

import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import io.github.skm0078.audora.spike.playback.engine.WavEncoder
import io.github.skm0078.audora.spike.playback.queue.RenderAheadQueue
import java.io.IOException
import java.io.InterruptedIOException
import kotlin.math.min

/** URIs of the form audora-spike://unit/<index>. The real app will use audora://book/<id>/page/<p>/unit/<i>. */
object TtsUri {
    private const val SCHEME = "audora-spike"
    private const val AUTHORITY = "unit"

    fun forUnit(index: Int): Uri =
        Uri.Builder().scheme(SCHEME).authority(AUTHORITY).appendPath(index.toString()).build()

    fun unitIndex(uri: Uri): Int? = uri.takeIf { it.scheme == SCHEME && it.authority == AUTHORITY }
        ?.lastPathSegment
        ?.toIntOrNull()
}

/**
 * Runs on ExoPlayer's loader thread, where blocking is allowed. It blocks in [open], not [read], because the
 * WAV header needs the final length; the effect is the same: the player waits in BUFFERING, keeps its wake
 * lock and foreground service, and never reaches ENDED while text remains.
 *
 * Cancellation: ExoPlayer interrupts the loader thread, and [RenderAheadQueue.awaitPcm] throws, which this
 * maps to [InterruptedIOException] as the DataSource contract expects.
 */
@OptIn(UnstableApi::class)
class TtsDataSource(
    private val queue: RenderAheadQueue,
    private val sampleRateHz: Int,
    private val onWaited: (unitIndex: Int, waitedMs: Long) -> Unit,
) : BaseDataSource(IS_NETWORK) {
    private var uri: Uri? = null
    private var bytes: ByteArray? = null
    private var readPosition = 0
    private var bytesRemaining = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val unitIndex = TtsUri.unitIndex(dataSpec.uri)
            ?: throw IOException("Not a TTS unit URI: ${dataSpec.uri}")

        val waitStarted = SystemClock.elapsedRealtime()
        val pcm = try {
            queue.awaitPcm(unitIndex)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Cancelled while waiting for unit $unitIndex").apply { initCause(e) }
        }
        onWaited(unitIndex, SystemClock.elapsedRealtime() - waitStarted)

        val wav = WavEncoder.encode(pcm, sampleRateHz)
        if (dataSpec.position > wav.size) {
            throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
        }
        readPosition = dataSpec.position.toInt()
        bytesRemaining = wav.size - dataSpec.position
        if (dataSpec.length != C.LENGTH_UNSET.toLong()) bytesRemaining = min(bytesRemaining, dataSpec.length)
        bytes = wav
        uri = dataSpec.uri
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = when {
        length == 0 -> 0
        bytesRemaining == 0L -> C.RESULT_END_OF_INPUT
        else -> copyOut(buffer, offset, length)
    }

    private fun copyOut(buffer: ByteArray, offset: Int, length: Int): Int {
        val source = checkNotNull(bytes) { "read() before open()" }
        val count = min(length.toLong(), bytesRemaining).toInt()
        System.arraycopy(source, readPosition, buffer, offset, count)
        readPosition += count
        bytesRemaining -= count
        bytesTransferred(count)
        return count
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        if (opened) {
            opened = false
            transferEnded()
        }
        bytes = null
        uri = null
    }

    private companion object {
        /** Local synthesis, not a network stream; Media3 uses this for bandwidth estimates. */
        const val IS_NETWORK = false
    }
}
