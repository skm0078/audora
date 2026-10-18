// File: core/src/main/kotlin/io/github/skm0078/audora/core/library/DocumentAccess.kt
// Purpose: The seam between library logic and Android's Storage Access Framework (fakes in tests).

package io.github.skm0078.audora.core.library

import io.github.skm0078.audora.core.result.Outcome
import java.io.InputStream

/** Every call may do blocking binder or file I/O: call from a background thread. */
interface DocumentAccess {
    /** Keeps the picker's read grant across restarts. Fails when the provider does not offer lasting grants. */
    fun keepAccess(documentUri: String): Outcome<Unit, DocumentError>

    /** Gives the grant back, once no book uses [documentUri]. Never fails: a missing grant is already released. */
    fun releaseAccess(documentUri: String)

    fun describe(documentUri: String): Outcome<DocumentInfo, DocumentError>

    /** A fresh stream from the start of the document; the caller closes it. */
    fun openStream(documentUri: String): Outcome<InputStream, DocumentError>

    fun availability(documentUri: String): BookAvailability

    /** Deletes Audora's own copies that no book uses; [inUse] is every book's URI. A grant has nothing to clean. */
    fun pruneUnused(inUse: Set<String>) = Unit
}

/**
 * @property displayName the file name the provider shows, if it has one.
 * @property sizeBytes null when the provider does not know it (rare for local files and SAF documents).
 */
data class DocumentInfo(val displayName: String?, val sizeBytes: Long?)

sealed interface DocumentError {
    /** The provider gives only a one-time grant (some cloud and messaging apps), so Audora cannot keep the book. */
    data class CannotKeepAccess(val detail: String) : DocumentError

    /** Moved, deleted, or the providing app is gone. */
    data class NotFound(val detail: String) : DocumentError

    /** The grant is gone or was never given. */
    data class AccessDenied(val detail: String) : DocumentError

    data class ReadFailed(val detail: String) : DocumentError
}
