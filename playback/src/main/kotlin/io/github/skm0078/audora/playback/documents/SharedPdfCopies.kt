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
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Open with Audora" from WhatsApp, Gmail or Downloads hands Audora a read grant that ends with the activity, so the
 * book could not be opened again later. Such a PDF is copied into the app's own files (`<dir>/<random>/<name>`,
 * keeping the file's name for the title) and the library keeps the copy. A copy is deleted when its book is removed
 * ([SafDocumentAccess.releaseAccess]), and [prune] deletes copies no book uses (a crash between copying and adding).
 * Errors name the provider only, never the file (it may be in the URI).
 *
 * A copy's URI is a `file://` path, which a backup restored to another phone or user (a different files folder) still
 * carries, so a copy is found by its last three segments, `shared-pdfs/<folder>/<name>`, in today's folder.
 */
class SharedPdfCopies(context: Context, private val maxBytes: Long = DEFAULT_MAX_BYTES) {
    private val resolver: ContentResolver = context.applicationContext.contentResolver
    private val dir = File(context.applicationContext.filesDir, SHARED_COPIES_DIR)

    /** True for a URI of a copy made here, on this phone or (restored from a backup) another. */
    fun owns(uri: Uri): Boolean = uri.scheme == ContentResolver.SCHEME_FILE && uri.path?.let(::copyPathOf) != null

    /** The copy's file in today's copies folder, for a URI [owns] accepts. */
    fun fileOf(uri: Uri): File {
        val (folder, name) = requireNotNull(uri.path?.let(::copyPathOf)) { "not a shared copy's URI" }
        return File(File(dir, folder), name)
    }

    /**
     * The URI to read a book from: for a copy, its file in today's folder (a restored URI names another phone's or
     * user's path); any other URI as it is.
     */
    fun readable(uri: Uri): Uri = if (owns(uri)) Uri.fromFile(fileOf(uri)) else uri

    /** Copies [documentUri] and returns the copy's URI, or null (logged) if it could not be read or is too big. */
    suspend fun copy(documentUri: String): String? = withContext(Dispatchers.IO) {
        val source = Uri.parse(documentUri)
        val folder = UUID.randomUUID().toString()
        MADE_HERE += folder // before the folder exists, so a prune running meanwhile never takes it
        val target = File(File(dir, folder), safeName(resolver.displayName(source)))
        try {
            target.parentFile?.mkdirs()
            val input = resolver.openInputStream(source) ?: throw IOException("provider returned no stream")
            val copied = input.use { from -> target.outputStream().use { to -> copyAtMost(from, to, maxBytes) } }
            AudoraLog.i(TAG, "Copied a shared PDF from authority=${source.authority} bytes=$copied")
            Uri.fromFile(target).toString()
        } catch (e: IOException) {
            failed(target, source, e)
        } catch (e: SecurityException) {
            failed(target, source, e)
        }
    }

    /**
     * Deletes every copy folder no book in [inUse] (every book's URI) points at, when nothing in it was written since
     * this process started and this process did not make it: a copy being added right now is never taken, even after
     * a clock change. Call under the library's lock (`Library.pruneDocuments`). Never throws: a folder that cannot be
     * read or deleted is logged and kept.
     */
    fun prune(inUse: Set<String>) {
        val used = inUse.mapNotNullTo(HashSet()) { folderOf(Uri.parse(it)) }
        val stray = try {
            // MADE_HERE is read live: a copy started while this walks is kept too.
            strayCopyFolders(dir, { it in used || it in MADE_HERE }, cutoffMillis = STARTED_AT_MILLIS - PRUNE_GRACE_MS)
        } catch (e: SecurityException) {
            AudoraLog.w(TAG, "Shared copies could not be listed; none pruned", e)
            emptyList()
        }
        val failed = stray.count { !it.deleteRecursively() }
        if (stray.isNotEmpty()) AudoraLog.i(TAG, "Pruned unused shared copies: ${stray.size - failed} of ${stray.size}")
        if (failed > 0) AudoraLog.w(TAG, "$failed unused shared copies could not be deleted")
    }

    /**
     * Bytes of every copy folder, for the Storage screen. Size only: a copy is the book's only file, so it goes when
     * its book is removed ([delete], through [SafDocumentAccess.releaseAccess]) or, unused, by [prune].
     */
    suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) {
        dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
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
    fun newCopy(fileName: String): File {
        val folder = UUID.randomUUID().toString()
        MADE_HERE += folder // before the folder exists, so a prune running meanwhile never takes it
        return File(File(dir, folder), safeName(fileName)).also { it.parentFile?.mkdirs() }
    }

    /** Deletes a copy and its folder. */
    fun delete(uri: Uri) {
        val file = fileOf(uri)
        val deleted = file.delete() && file.parentFile?.delete() != false
        if (!deleted) AudoraLog.w(TAG, "A removed book's copy could not be deleted")
    }

    private fun folderOf(uri: Uri): String? = uri.takeIf(::owns)?.path?.let(::copyPathOf)?.first

    companion object {
        /**
         * The URI every opener reads [documentUri] from (PDF engine, stream, cover): a shared copy mapped into today's
         * folder ([readable]).
         */
        fun readableUri(context: Context, documentUri: String): Uri =
            SharedPdfCopies(context).readable(Uri.parse(documentUri))

        private const val DEFAULT_NAME = "Shared PDF.pdf"
        private const val SHARED_TEXT_NAME = "Shared text"
        private const val TITLE_FROM_TEXT_CHARS = 60
        private const val MAX_NAME_CHARS = 120

        /** Larger than any book in the test corpus (41 MB) by far; guards the phone's storage against a bad share. */
        private const val DEFAULT_MAX_BYTES = 512L * 1024 * 1024

        /** File times may be kept to the second; a copy written in the second the process started is still new. */
        private const val PRUNE_GRACE_MS = 2_000L

        /** Set when the class is first used, before any copy of this process is made. */
        private val STARTED_AT_MILLIS = System.currentTimeMillis()

        /** Copy folders this process made: never pruned, whatever their file times say. */
        private val MADE_HERE: MutableSet<String> = ConcurrentHashMap.newKeySet()

        private val UNSAFE = Regex("""[/\\:*?"<>|\u0000-\u001f]""")

        private fun safeName(name: String?): String {
            val cleaned = name?.replace(UNSAFE, "_")?.trim()?.take(MAX_NAME_CHARS)
            return if (cleaned.isNullOrBlank() || cleaned.startsWith(".")) DEFAULT_NAME else cleaned
        }
    }
}

private const val TAG = "Audora.SharedPdf"

/** A copy that failed: logged without the source's name, and its half-written folder deleted. */
private fun failed(target: File, source: Uri, e: Exception): String? {
    AudoraLog.e(TAG, "Could not copy a shared PDF from authority=${source.authority}: ${e.javaClass.simpleName}")
    target.parentFile?.deleteRecursively()
    return null
}

/** The name the providing app gives [uri], or null; never throws for a provider that cannot answer. */
fun ContentResolver.displayName(uri: Uri): String? = try {
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
    }
} catch (e: IllegalArgumentException) {
    noName(uri, e)
} catch (e: IllegalStateException) {
    noName(uri, e)
} catch (e: SecurityException) {
    // A share without a read grant: the copy below fails the same way and is reported there; this must not crash.
    noName(uri, e)
}

private fun noName(uri: Uri, e: Exception): String? {
    AudoraLog.d(TAG, "No name from authority=${uri.authority}: ${e.javaClass.simpleName}")
    return null
}

/**
 * Copies [from] into [to] and returns the byte count, stopping as soon as it passes [limit]: a huge file shared by
 * mistake never fills the phone before the size check.
 */
private fun copyAtMost(from: InputStream, to: OutputStream, limit: Long): Long {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var copied = 0L
    while (true) {
        val read = from.read(buffer)
        if (read < 0) return copied
        copied += read
        if (copied > limit) throw IOException("over the $limit-byte limit")
        to.write(buffer, 0, read)
    }
}
