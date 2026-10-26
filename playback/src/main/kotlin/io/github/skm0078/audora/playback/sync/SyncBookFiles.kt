// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/sync/SyncBookFiles.kt
// Purpose: The sync folder's Books folder (CF-16): which books are there, copying this phone's books in, and
//          finding another phone's copy to add.

package io.github.skm0078.audora.playback.sync

import android.webkit.MimeTypeMap
import io.github.skm0078.audora.core.config.FolderSyncConfig
import io.github.skm0078.audora.core.library.DocumentAccess
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.getOrNull
import java.io.IOException
import java.security.MessageDigest

/**
 * A copy is named "<title> [<tag>].<ext>", the tag cut from the book's fingerprint: a person sees which book it is,
 * and every phone finds the same book's copy by its tag, whatever title each phone gave it. A book already there
 * (copied by another phone, or added from here) is not copied again.
 */
internal class SyncBookFiles(
    private val folder: SyncDirectory,
    private val booksFolderId: String,
    private val documents: DocumentAccess,
    private val config: FolderSyncConfig,
) {
    private val entries = folder.list(booksFolderId).filter { !it.isFolder }.toMutableList()

    /** The copy's file name for each book of [fingerprints] that has one in the folder. */
    fun names(fingerprints: Collection<String>): Map<String, String> =
        fingerprints.mapNotNull { fp -> entryOf(fp)?.let { fp to it.name } }.toMap()

    /** The copy's document id for a book another phone named [fileName]. */
    fun idOf(fileName: String): String? = entries.firstOrNull { it.name == fileName }?.id

    /** Copies each of [local]'s books not in the folder yet; returns how many were copied. Never throws. */
    fun copyMissing(local: LocalBooks): Int = local.books.count { book ->
        val fp = book.fingerprint
        val size = local.sizes[fp] ?: 0L
        val uri = local.documentUris[fp]
        if (entryOf(fp) != null || uri == null || size > config.maxBookCopyBytes) return@count false
        copy(fp, book.title, uri)
    }

    private fun copy(fingerprint: String, title: String, documentUri: String): Boolean {
        val sourceName = documents.describe(documentUri).getOrNull()?.displayName.orEmpty()
        val extension = sourceName.substringAfterLast('.', "").lowercase().takeIf { it.length in 1..MAX_EXT_CHARS }
        val name = nameFor(title, fingerprint, extension)
        val stream = when (val opened = documents.openStream(documentUri)) {
            is Outcome.Success -> opened.value
            is Outcome.Failure -> return false.also { AudoraLog.w(TAG, "Book not copied: ${opened.error}") }
        }
        val mime = extension?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) } ?: BINARY
        return try {
            stream.use { folder.copyInto(booksFolderId, name, mime, it) }
            // The provider may rename a clash ("name (1).pdf"); the next listing has the real name.
            entries += folder.list(booksFolderId).filter { !it.isFolder && tagOf(fingerprint) in it.name }
            true
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Book not copied to the sync folder: ${e.javaClass.simpleName}")
            false
        }
    }

    private fun entryOf(fingerprint: String): SyncEntry? {
        val tag = "[${tagOf(fingerprint)}]"
        return entries.firstOrNull { tag in it.name && it.sizeBytes != 0L }
    }

    companion object {
        private const val TAG = "Audora.Sync"
        private const val BINARY = "application/octet-stream"
        private const val MAX_EXT_CHARS = 5
        private const val TAG_CHARS = 10
        private const val MAX_TITLE_CHARS = 80
        private val UNSAFE = Regex("""[/\\:*?"<>|\[\]\u0000-\u001f]""")

        /** Ten hex digits of the fingerprint's SHA-256: short, and the same on every phone. */
        fun tagOf(fingerprint: String): String = MessageDigest.getInstance("SHA-256")
            .digest(fingerprint.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(TAG_CHARS)

        fun nameFor(title: String, fingerprint: String, extension: String?): String {
            val safe = title.replace(UNSAFE, "_").trim().take(MAX_TITLE_CHARS).ifBlank { "Book" }
            return "$safe [${tagOf(fingerprint)}]" + extension?.let { ".$it" }.orEmpty()
        }
    }
}
