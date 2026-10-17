// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/documents/SafDocumentAccess.kt
// Purpose: DocumentAccess over the Storage Access Framework (lasting grants, name, size, availability) and own copies.

package io.github.skm0078.audora.playback.documents

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import io.github.skm0078.audora.core.library.BookAvailability
import io.github.skm0078.audora.core.library.DocumentAccess
import io.github.skm0078.audora.core.library.DocumentError
import io.github.skm0078.audora.core.library.DocumentInfo
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/**
 * Error details name the exception type and the provider's authority only: a document URI usually spells out
 * the file's path and name, which must not reach the log (AGENTS.md: no PDF text, no titles).
 */
class SafDocumentAccess(context: Context, private val copies: SharedPdfCopies = SharedPdfCopies(context)) :
    DocumentAccess {
    private val resolver: ContentResolver = context.applicationContext.contentResolver

    override fun keepAccess(documentUri: String): Outcome<Unit, DocumentError> {
        val uri = Uri.parse(documentUri)
        // Audora's own file, or a file in a watched folder (item 26), read through the folder's grant: nothing to keep.
        if (copies.owns(uri) || resolver.inGrantedFolder(uri)) return Outcome.Success(Unit)
        return try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            Outcome.Success(Unit)
        } catch (e: SecurityException) {
            Outcome.Failure(DocumentError.CannotKeepAccess(detail(e, uri)))
        }
    }

    override fun releaseAccess(documentUri: String) {
        val uri = Uri.parse(documentUri)
        if (copies.owns(uri)) return copies.delete(uri)
        if (resolver.inGrantedFolder(uri)) return // The folder grant goes on the next scan once nothing needs it.
        try {
            resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            // Nothing to give back: the grant was already gone (revoked, or the provider was uninstalled).
            AudoraLog.d(TAG, "No grant to release: ${detail(e, uri)}")
        }
    }

    override fun describe(documentUri: String): Outcome<DocumentInfo, DocumentError> {
        val uri = Uri.parse(documentUri)
        if (copies.owns(uri)) {
            val file = copies.fileOf(uri)
            return guarded(uri) {
                if (!file.isFile) throw FileNotFoundException("copy is gone")
                DocumentInfo(displayName = file.name, sizeBytes = file.length())
            }
        }
        return guarded(uri) {
            val queried = queryNameAndSize(uri)
            DocumentInfo(displayName = queried.displayName, sizeBytes = queried.sizeBytes ?: statSize(uri))
        }
    }

    override fun openStream(documentUri: String): Outcome<InputStream, DocumentError> {
        val uri = Uri.parse(documentUri)
        return guarded(uri) { resolver.openInputStream(uri) ?: throw FileNotFoundException("provider returned null") }
    }

    override fun availability(documentUri: String): BookAvailability {
        val uri = Uri.parse(documentUri)
        return when {
            copies.owns(uri) -> if (copies.fileOf(uri).isFile) BookAvailability.AVAILABLE else BookAvailability.MISSING

            resolver.persistedUriPermissions.none { it.uri == uri && it.isReadPermission } &&
                !resolver.inGrantedFolder(uri) ->
                BookAvailability.ACCESS_LOST

            else -> providerAvailability(uri)
        }
    }

    private fun providerAvailability(uri: Uri): BookAvailability =
        when (val opened = guarded(uri) { resolver.openFileDescriptor(uri, READ_MODE)?.close() != null }) {
            is Outcome.Success -> if (opened.value) BookAvailability.AVAILABLE else BookAvailability.MISSING

            is Outcome.Failure -> if (opened.error is DocumentError.AccessDenied) {
                BookAvailability.ACCESS_LOST
            } else {
                BookAvailability.MISSING
            }
        }

    private fun queryNameAndSize(uri: Uri): DocumentInfo {
        val columns = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        resolver.query(uri, columns, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIndex >= 0 && !cursor.isNull(nameIndex)) cursor.getString(nameIndex) else null
                val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
                return DocumentInfo(name, size?.takeIf { it >= 0 })
            }
        }
        return DocumentInfo(displayName = null, sizeBytes = null)
    }

    /** Fallback when the provider has no SIZE column; -1 from Android means unknown. */
    private fun statSize(uri: Uri): Long? = resolver.openFileDescriptor(uri, READ_MODE)?.use { descriptor ->
        descriptor.statSize.takeIf { it >= 0 }
    }

    /** Providers report a missing file, a dead provider and a lost grant through different exceptions. */
    private inline fun <T> guarded(uri: Uri, block: () -> T): Outcome<T, DocumentError> = try {
        Outcome.Success(block())
    } catch (e: FileNotFoundException) {
        Outcome.Failure(DocumentError.NotFound(detail(e, uri)))
    } catch (e: IllegalArgumentException) {
        // Unknown URI, typically because the providing app was uninstalled or its document is gone.
        Outcome.Failure(DocumentError.NotFound(detail(e, uri)))
    } catch (e: SecurityException) {
        Outcome.Failure(DocumentError.AccessDenied(detail(e, uri)))
    } catch (e: IOException) {
        Outcome.Failure(DocumentError.ReadFailed(detail(e, uri)))
    } catch (e: IllegalStateException) {
        // A provider crashing mid-call reaches the caller as IllegalStateException on some Android versions.
        Outcome.Failure(DocumentError.ReadFailed(detail(e, uri)))
    }

    private fun detail(e: Exception, uri: Uri): String = "${e.javaClass.simpleName} authority=${uri.authority}"

    private companion object {
        const val TAG = "Audora.Documents"
        const val READ_MODE = "r"
    }
}

/** A document opened through a folder (tree) URI whose folder Audora holds a lasting read grant on (item 26). */
private fun ContentResolver.inGrantedFolder(uri: Uri): Boolean {
    val folder = WatchedFolders.folderOf(uri) ?: return false
    return persistedUriPermissions.any { it.uri == folder && it.isReadPermission }
}
