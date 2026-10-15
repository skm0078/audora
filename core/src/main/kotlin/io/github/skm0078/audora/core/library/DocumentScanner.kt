// File: core/src/main/kotlin/io/github/skm0078/audora/core/library/DocumentScanner.kt
// Purpose: Reads what identifies a picked document: its name, size and content fingerprint.

package io.github.skm0078.audora.core.library

import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.flatMap
import io.github.skm0078.audora.core.result.map

internal class DocumentScanner(private val documents: DocumentAccess, private val fingerprinter: BookFingerprinter) {
    /** Blocking I/O. Needs read access to [documentUri] already. */
    fun scan(documentUri: String): Outcome<ScannedDocument, LibraryError> =
        documents.describe(documentUri).mapDocumentError().flatMap { info ->
            documents.openStream(documentUri).mapDocumentError().flatMap { stream ->
                stream.use { fingerprinter.fingerprint(it, info.sizeBytes) }
                    .mapFingerprintError()
                    .map { ScannedDocument(info.displayName, info.sizeBytes ?: UNKNOWN_SIZE, it) }
            }
        }

    private fun <T> Outcome<T, DocumentError>.mapDocumentError(): Outcome<T, LibraryError> = when (this) {
        is Outcome.Success -> this
        is Outcome.Failure -> Outcome.Failure(error.toLibraryError())
    }

    private fun <T> Outcome<T, FingerprintError>.mapFingerprintError(): Outcome<T, LibraryError> = when (this) {
        is Outcome.Success -> this

        is Outcome.Failure -> Outcome.Failure(
            when (error) {
                FingerprintError.NotAPdf -> LibraryError.NotAPdf
                is FingerprintError.ReadFailed -> LibraryError.Unreadable
            },
        )
    }

    private companion object {
        const val UNKNOWN_SIZE = -1L
    }
}

/** @property sizeBytes -1 when the provider does not know it. */
internal data class ScannedDocument(val displayName: String?, val sizeBytes: Long, val fingerprint: String)

internal fun DocumentError.toLibraryError(): LibraryError = when (this) {
    is DocumentError.CannotKeepAccess -> LibraryError.CannotKeepAccess
    is DocumentError.NotFound, is DocumentError.AccessDenied, is DocumentError.ReadFailed -> LibraryError.Unreadable
}
