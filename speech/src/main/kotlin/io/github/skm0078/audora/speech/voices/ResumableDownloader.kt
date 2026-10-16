// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/voices/ResumableDownloader.kt
// Purpose: Downloads a pinned model file into a partial file that later attempts resume, then checks its SHA-256.

package io.github.skm0078.audora.speech.voices

import io.github.skm0078.audora.core.config.VoiceDownloadConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/** Why a download did not produce a verified file. [retryable] errors keep the partial file for the next attempt. */
sealed interface DownloadError {
    val message: String
    val retryable: Boolean

    /** Connection failed or dropped. Bytes received so far are kept. */
    data class Network(val detail: String, val cause: Throwable? = null) : DownloadError {
        override val message: String get() = "network error: $detail"
        override val retryable: Boolean get() = true
    }

    data class Http(val code: Int) : DownloadError {
        override val message: String get() = "the server answered HTTP $code"

        // 408, 429 and 5xx are the server's way of saying "later"; anything else will not get better by asking again.
        override val retryable: Boolean get() = code == TIMEOUT || code == TOO_MANY || code >= SERVER_ERRORS

        private companion object {
            const val TIMEOUT = 408
            const val TOO_MANY = 429
            const val SERVER_ERRORS = 500
        }
    }

    /** The file is not what the catalog pins (more bytes than pinned, or a different hash). Nothing is kept. */
    data class Corrupt(val detail: String) : DownloadError {
        override val message: String get() = "the download is corrupt and was deleted: $detail"
        override val retryable: Boolean get() = false
    }

    /** The phone is full, or the file cannot be written. */
    data class Storage(val detail: String, val cause: Throwable? = null) : DownloadError {
        override val message: String get() = "cannot save the file: $detail"
        override val retryable: Boolean get() = false
    }

    /** Asked to stop (the user cancelled, or Android stopped the job). Bytes received so far are kept. */
    data object Stopped : DownloadError {
        override val message: String get() = "stopped"
        override val retryable: Boolean get() = true
    }
}

/**
 * A file the app may download: a catalog voice zip (P4-01) or an OCR model (P3-05). The pins travel inside the APK.
 */
interface PinnedFile {
    val id: String
    val url: String
    val sha256: String
    val sizeBytes: Long
}

/**
 * One attempt per call: resumes from the bytes already in the partial file with an HTTP `Range` request, appends,
 * and once all [PinnedFile.sizeBytes] are there hashes the whole file against [PinnedFile.sha256]. The hash
 * covers every byte from every attempt, so a resume that stitched in the wrong bytes (the server changed the file
 * between attempts) is caught as [DownloadError.Corrupt] rather than installed.
 *
 * @param freeBytes usable space where the partial file lives (a seam for tests).
 */
class ResumableDownloader(
    private val http: HttpSource,
    private val config: VoiceDownloadConfig,
    private val freeBytes: (File) -> Long = { it.usableSpace },
) {
    /**
     * @param onProgress bytes on disk and the total, at most every [VoiceDownloadConfig.progressStepBytes].
     * @param keepGoing polled between buffers; false stops with [DownloadError.Stopped].
     */
    fun download(
        pinned: PinnedFile,
        partial: File,
        onProgress: (Long, Long) -> Unit,
        keepGoing: () -> Boolean,
    ): Outcome<File, DownloadError> {
        val problem = prepare(pinned, partial) ?: if (partial.length() < pinned.sizeBytes) {
            fetch(Transfer(pinned, partial, partial.length(), onProgress, keepGoing))
        } else {
            null
        }
        return if (problem == null) verify(pinned, partial) else Outcome.Failure(problem)
    }

    /** Drops a partial file longer than the pin and checks there is room for the rest plus the unpacked copy. */
    private fun prepare(pinned: PinnedFile, partial: File): DownloadError? {
        val dir = partial.absoluteFile.parentFile
        dir.mkdirs()
        if (partial.length() > pinned.sizeBytes) partial.delete()
        val missing = pinned.sizeBytes - partial.length()
        // A voice zip is unpacked next to itself before it is deleted, so both must fit (an OCR model is only
        // renamed, so this over-asks by one file, which keeps the rule simple).
        val needed = missing + pinned.sizeBytes + config.installHeadroomBytes
        val free = freeBytes(dir)
        return when {
            !dir.isDirectory -> DownloadError.Storage("cannot create ${dir.name}")

            missing > 0 && free < needed ->
                DownloadError.Storage("needs ${needed.toMb()} MB free, has ${free.toMb()} MB")

            else -> null
        }
    }

    /** One request: returns null once the partial file holds every byte, else why it does not. */
    private fun fetch(transfer: Transfer): DownloadError? {
        val have = transfer.written
        val response = try {
            http.open(transfer.pinned.url, have)
        } catch (e: IOException) {
            return DownloadError.Network(e.message ?: e.javaClass.simpleName, e)
        }
        return response.use {
            when (val start = writeOffset(response, have, transfer.partial)) {
                is Outcome.Failure -> start.error
                is Outcome.Success -> transfer.receive(response.body, append = start.value > 0)
            }
        }
    }

    /** Where the response's bytes go in the partial file, from its status and `Content-Range`. */
    private fun writeOffset(response: HttpResponse, have: Long, partial: File): Outcome<Long, DownloadError> =
        when (response.code) {
            HttpResponse.PARTIAL_CONTENT -> {
                val start = HttpResponse.rangeStart(response.contentRange)
                if (start == have) {
                    AudoraLog.i(TAG, "Resuming ${partial.name} at byte $have")
                    Outcome.Success(have)
                } else {
                    partial.delete()
                    Outcome.Failure(DownloadError.Network("server resumed at byte $start instead of $have; restarting"))
                }
            }

            HttpResponse.OK -> {
                if (have > 0) AudoraLog.i(TAG, "Server ignored the resume of ${partial.name}; restarting")
                Outcome.Success(0L)
            }

            HttpResponse.RANGE_NOT_SATISFIABLE -> {
                // Our offset is past what the server has, so the partial file is not from this file.
                partial.delete()
                Outcome.Failure(DownloadError.Network("server refused to resume at byte $have; restarting"))
            }

            else -> Outcome.Failure(DownloadError.Http(response.code))
        }

    private fun verify(pinned: PinnedFile, partial: File): Outcome<File, DownloadError> {
        val actual = try {
            sha256(partial, config.bufferBytes)
        } catch (e: IOException) {
            return Outcome.Failure(DownloadError.Storage("cannot read ${partial.name}: ${e.message}", e))
        }
        if (actual != pinned.sha256) {
            partial.delete()
            AudoraLog.e(TAG, "file=${pinned.id} SHA-256 $actual does not match the pin ${pinned.sha256}; deleted")
        }
        return if (actual == pinned.sha256) {
            Outcome.Success(partial)
        } else {
            Outcome.Failure(DownloadError.Corrupt("SHA-256 does not match the catalog"))
        }
    }

    private fun Long.toMb(): Long = this / BYTES_PER_MB

    /** The bytes of one attempt flowing into the partial file. [written] counts what is on disk. */
    private inner class Transfer(
        val pinned: PinnedFile,
        val partial: File,
        var written: Long,
        private val onProgress: (Long, Long) -> Unit,
        private val keepGoing: () -> Boolean,
    ) {
        private var reported = written

        fun receive(body: InputStream, append: Boolean): DownloadError? {
            if (!append) written = 0
            reported = written
            val out = try {
                FileOutputStream(partial, append)
            } catch (e: IOException) {
                return DownloadError.Storage("cannot open ${partial.name}: ${e.message}", e)
            }
            val problem = out.use { pump(body, it) }
            onProgress(written, pinned.sizeBytes)
            return problem ?: if (written < pinned.sizeBytes) {
                DownloadError.Network("connection closed at byte $written of ${pinned.sizeBytes}")
            } else {
                null
            }
        }

        private fun pump(body: InputStream, out: OutputStream): DownloadError? {
            val buffer = ByteArray(config.bufferBytes)
            var problem: DownloadError? = null
            var ended = false
            while (problem == null && !ended) {
                val read = if (keepGoing()) read(body, buffer) else Outcome.Failure(DownloadError.Stopped)
                when (read) {
                    is Outcome.Failure -> problem = read.error
                    is Outcome.Success -> if (read.value < 0) ended = true else problem = write(out, buffer, read.value)
                }
            }
            return problem
        }

        private fun read(body: InputStream, buffer: ByteArray): Outcome<Int, DownloadError> = try {
            Outcome.Success(body.read(buffer))
        } catch (e: IOException) {
            Outcome.Failure(DownloadError.Network("${e.message ?: e.javaClass.simpleName} at byte $written", e))
        }

        private fun write(out: OutputStream, buffer: ByteArray, count: Int): DownloadError? =
            if (written + count > pinned.sizeBytes) {
                partial.delete()
                DownloadError.Corrupt("more than the pinned ${pinned.sizeBytes} bytes")
            } else {
                try {
                    out.write(buffer, 0, count)
                    advance(count)
                    null
                } catch (e: IOException) {
                    DownloadError.Storage(e.message ?: e.javaClass.simpleName, e)
                }
            }

        private fun advance(count: Int) {
            written += count
            if (written - reported >= config.progressStepBytes) {
                reported = written
                onProgress(written, pinned.sizeBytes)
            }
        }
    }

    private companion object {
        const val TAG = "Speech.Download"
        const val BYTES_PER_MB = 1024L * 1024
    }
}

/** Lower-case hex SHA-256 of [file]. */
@Throws(IOException::class)
internal fun sha256(file: File, bufferBytes: Int): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(bufferBytes)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
