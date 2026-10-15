// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/session/TtsDataSource.kt
// Purpose: Serves a sentence's synthesized audio as WAV bytes, blocking until the render queue has it (C5).

package io.github.skm0078.audora.playback.session

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import io.github.skm0078.audora.playback.audio.WavEncoder
import java.io.IOException
import java.io.InterruptedIOException
import kotlin.math.min

/**
 * Runs on ExoPlayer's loader threads, where blocking is allowed. It blocks in [open], not [read], because the
 * WAV header needs the final length; the effect is the same: the player waits in BUFFERING, keeps its wake
 * lock and foreground service, and never reaches ENDED while text remains.
 *
 * A "more is coming" placeholder blocks until the player cancels it (the next page arrived and the playlist
 * moved past it) or the queue closes.
 *
 * Cancellation: ExoPlayer interrupts the loader thread; the queue's wait throws, mapped here to
 * [InterruptedIOException] as the DataSource contract expects.
 */
@OptIn(UnstableApi::class)
class TtsDataSource(private val registry: QueueRegistry) : BaseDataSource(IS_NETWORK) {
    private var uri: Uri? = null
    private var bytes: ByteArray? = null
    private var readPosition = 0
    private var bytesRemaining = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val target = TtsUri.parse(dataSpec.uri.toString()) ?: throw IOException("Not an Audora URI: ${dataSpec.uri}")
        val queue = registry.queueFor(target.epoch) ?: throw IOException("Stale playlist item, epoch ${target.epoch}")
        val wav = try {
            when (target) {
                is TtsUri.Sentence -> WavEncoder.encode(queue.awaitPcm(target.key))
                is TtsUri.More -> queue.awaitClose()
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Cancelled while waiting for $target").apply { initCause(e) }
        }
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

    class Factory(private val registry: QueueRegistry) : DataSource.Factory {
        override fun createDataSource(): DataSource = TtsDataSource(registry)
    }

    private companion object {
        /** Local synthesis, not a network stream; Media3 uses this for bandwidth estimates. */
        const val IS_NETWORK = false
    }
}
