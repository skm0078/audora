// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/documents/SharedPdfCopies.kt
// Purpose: Keeps Audora's own copy of a PDF opened or shared from another app whose grant cannot be kept.

package io.github.skm0078.audora.playback.documents

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Open with Audora" from WhatsApp, Gmail or Downloads hands Audora a read grant that ends with the activity, so the
 * book could not be opened again later. Such a PDF is copied into the app's own files (`<dir>/<random>/<name>`,
 * keeping the file's name for the title) and the library keeps the copy. A copy is deleted when its book is removed
 * ([SafDocumentAccess.releaseAccess]). Errors name the provider only, never the file (it may be in the URI).
 */
class SharedPdfCopies(context: Context, private val maxBytes: Long = DEFAULT_MAX_BYTES) {
    private val resolver: ContentResolver = context.applicationContext.contentResolver
    private val dir = File(context.applicationContext.filesDir, DIR_NAME)

    /** True for a URI of a copy made here. */
    fun owns(uri: Uri): Boolean {
        val path = uri.path
        if (uri.scheme != ContentResolver.SCHEME_FILE || path == null) return false
        return File(path).canonicalPath.startsWith(dir.canonicalPath + File.separator)
    }

    /** The copy's file, for a URI [owns] accepts. */
    fun fileOf(uri: Uri): File = File(requireNotNull(uri.path) { "a copy's URI has a path" })

    /** Copies [documentUri] and returns the copy's URI, or null (logged) if it could not be read or is too big. */
    suspend fun copy(documentUri: String): String? = withContext(Dispatchers.IO) {
        val source = Uri.parse(documentUri)
        val target = File(File(dir, UUID.randomUUID().toString()), safeName(displayName(source)))
        try {
            target.parentFile?.mkdirs()
            val input = resolver.openInputStream(source) ?: throw IOException("provider returned no stream")
            val copied = input.use { from -> target.outputStream().use { to -> from.copyTo(to) } }
            if (copied > maxBytes) throw IOException("$copied bytes is over the $maxBytes limit")
            AudoraLog.i(TAG, "Copied a shared PDF from authority=${source.authority} bytes=$copied")
            Uri.fromFile(target).toString()
        } catch (e: IOException) {
            failed(target, source, e)
        } catch (e: SecurityException) {
            failed(target, source, e)
        }
    }

    /**
     * Saves text shared from another app (plan item 42) as `<title>.txt` beside the copies, so the library keeps it
     * like any shared book; returns its URI, or null (logged) if it could not be written. [maxChars] caps a share.
     */
    suspend fun saveText(text: String, title: String?, maxChars: Int): String? = withContext(Dispatchers.IO) {
        val name = safeName(title ?: text.lineSequence().firstOrNull { it.isNotBlank() }?.take(TITLE_FROM_TEXT_CHARS))
            .removeSuffix(".pdf").let { if (it == DEFAULT_NAME.removeSuffix(".pdf")) SHARED_TEXT_NAME else it }
        val target = newCopy("$name.txt")
        try {
            target.writeText(text.take(maxChars))
            AudoraLog.i(TAG, "Saved shared text, chars=${minOf(text.length, maxChars)}")
            Uri.fromFile(target).toString()
        } catch (e: IOException) {
            AudoraLog.e(TAG, "Could not save shared text: ${e.javaClass.simpleName}")
            target.parentFile?.deleteRecursively()
            null
        }
    }

    /**
     * A new, empty place for a book Audora makes itself (shared text, a camera photo, plan items 42 and 62): its own
     * folder beside the copies, so [owns] accepts it and removing the book deletes it. [fileName] is made safe.
     */
    fun newCopy(fileName: String): File =
        File(File(dir, UUID.randomUUID().toString()), safeName(fileName)).also { it.parentFile?.mkdirs() }

    /** Deletes a copy and its folder. */
    fun delete(uri: Uri) {
        val file = fileOf(uri)
        val deleted = file.delete() && file.parentFile?.delete() != false
        if (!deleted) AudoraLog.w(TAG, "A removed book's copy could not be deleted")
    }

    private fun failed(target: File, source: Uri, e: Exception): String? {
        AudoraLog.e(TAG, "Could not copy a shared PDF from authority=${source.authority}: ${e.javaClass.simpleName}")
        target.parentFile?.deleteRecursively()
        return null
    }

    private fun displayName(uri: Uri): String? = try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }
    } catch (e: IllegalArgumentException) {
        AudoraLog.d(TAG, "No name from authority=${uri.authority}: ${e.javaClass.simpleName}")
        null
    }

    private companion object {
        const val TAG = "Audora.SharedPdf"
        const val DIR_NAME = "shared-pdfs"
        const val DEFAULT_NAME = "Shared PDF.pdf"
        const val SHARED_TEXT_NAME = "Shared text"
        const val TITLE_FROM_TEXT_CHARS = 60
        const val MAX_NAME_CHARS = 120

        /** Larger than any book in the test corpus (41 MB) by far; guards the phone's storage against a bad share. */
        const val DEFAULT_MAX_BYTES = 512L * 1024 * 1024

        val UNSAFE = Regex("""[/\\:*?"<>|\u0000-\u001f]""")

        fun safeName(name: String?): String {
            val cleaned = name?.replace(UNSAFE, "_")?.trim()?.take(MAX_NAME_CHARS)
            return if (cleaned.isNullOrBlank() || cleaned.startsWith(".")) DEFAULT_NAME else cleaned
        }
    }
}
