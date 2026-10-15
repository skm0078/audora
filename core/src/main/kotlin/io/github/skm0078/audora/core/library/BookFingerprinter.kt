// File: core/src/main/kotlin/io/github/skm0078/audora/core/library/BookFingerprinter.kt
// Purpose: Recognizes a PDF by its content, so a moved or re-picked file is matched to the same book.

package io.github.skm0078.audora.core.library

import io.github.skm0078.audora.core.config.LibraryConfig
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.flatMap
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/**
 * Fingerprint = SHA-256 over the file size and the first [LibraryConfig.fingerprintHeadBytes] bytes, so taking
 * one costs the same for a 10-page and a 1,300-page book. Two different PDFs would need the same size and the
 * same first megabyte to collide; PDFs put the per-file document ID and page objects there, so it does not happen
 * in practice. A PDF edited in place gets a new fingerprint, which is right: its pages may have changed.
 *
 * Also rejects files that are not PDFs, by the `%PDF-` header.
 */
class BookFingerprinter(private val config: LibraryConfig = LibraryConfig()) {
    /** Reads up to the configured head of [stream]; does not close it. [sizeBytes] null when unknown. */
    fun fingerprint(stream: InputStream, sizeBytes: Long?): Outcome<String, FingerprintError> =
        readHead(stream).flatMap { head ->
            if (hasPdfHeader(
                    head,
                )
            ) {
                Outcome.Success(digest(head, sizeBytes))
            } else {
                Outcome.Failure(FingerprintError.NotAPdf)
            }
        }

    private fun readHead(stream: InputStream): Outcome<ByteArray, FingerprintError> = try {
        Outcome.Success(stream.readNBytes(config.fingerprintHeadBytes))
    } catch (e: IOException) {
        Outcome.Failure(FingerprintError.ReadFailed(e.toString()))
    }

    private fun digest(head: ByteArray, sizeBytes: Long?): String {
        val digest = MessageDigest.getInstance(ALGORITHM)
        digest.update((sizeBytes ?: UNKNOWN_SIZE).toString().toByteArray(Charsets.US_ASCII))
        digest.update(SEPARATOR)
        digest.update(head)
        return VERSION_PREFIX + digest.digest().toHex()
    }

    private fun hasPdfHeader(head: ByteArray): Boolean {
        val window = minOf(head.size, config.pdfHeaderWindowBytes)
        val last = window - PDF_MAGIC.size
        return (0..last).any { start -> PDF_MAGIC.indices.all { head[start + it] == PDF_MAGIC[it] } }
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { "%02x".format(it) }

    private companion object {
        const val ALGORITHM = "SHA-256"
        const val UNKNOWN_SIZE = -1L

        /** Bump if the recipe changes, so old and new fingerprints never compare equal by accident. */
        const val VERSION_PREFIX = "v1:"
        val SEPARATOR = byteArrayOf(0)
        val PDF_MAGIC = "%PDF-".toByteArray(Charsets.US_ASCII)
    }
}

sealed interface FingerprintError {
    data object NotAPdf : FingerprintError

    data class ReadFailed(val detail: String) : FingerprintError
}
