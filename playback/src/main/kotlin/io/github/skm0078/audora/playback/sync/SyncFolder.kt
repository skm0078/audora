// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/sync/SyncFolder.kt
// Purpose: The picked sync folder over the Storage Access Framework: list, read, write and copy into it (CF-16).

package io.github.skm0078.audora.playback.sync

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.IOException
import java.io.InputStream

/** A file or folder in the sync folder: its provider id, name, and size when the provider says it. */
data class SyncEntry(val id: String, val name: String, val isFolder: Boolean, val sizeBytes: Long?)

/** The sync folder as a sync uses it; every call may throw IOException and blocks. */
interface SyncDirectory {
    /** The picked folder's own id. */
    fun rootId(): String

    /** The folder named [name] in [parentId], made when missing. */
    fun folder(parentId: String, name: String): String

    fun list(folderId: String): List<SyncEntry>

    fun readText(id: String): String

    /** Writes [text] as the file [name] in [folderId], replacing it when it is there. */
    fun writeText(folderId: String, name: String, text: String)

    /** Copies [from] into a new file [name] in [folderId]; a copy cut short is deleted. */
    fun copyInto(folderId: String, name: String, mimeType: String, from: InputStream)

    /** The address the library keeps for a book added from the file [id]. */
    fun documentUri(id: String): String
}

/**
 * Every call goes through [tree], the folder the listener picked, whose read and write grant ListenLock keeps.
 * Providers fail through several runtime exceptions (a lost grant, a gone folder, a provider crashing mid-call); each
 * becomes an IOException, so one bad provider fails a sync, never the app. Blocking: call off the main thread.
 */
class SyncFolder(private val resolver: ContentResolver, val tree: Uri) : SyncDirectory {
    override fun rootId(): String = provider { DocumentsContract.getTreeDocumentId(tree) }

    override fun folder(parentId: String, name: String): String =
        list(parentId).firstOrNull { it.isFolder && it.name == name }?.id
            ?: create(parentId, Document.MIME_TYPE_DIR, name)

    override fun list(folderId: String): List<SyncEntry> = provider {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, folderId)
        val cursor = resolver.query(children, COLUMNS, null, null, null) ?: throw IOException("no listing")
        cursor.use {
            buildList {
                while (it.moveToNext()) {
                    val id = it.getString(ID_INDEX) ?: continue
                    val size = if (it.isNull(SIZE_INDEX)) null else it.getLong(SIZE_INDEX)
                    add(SyncEntry(id, it.getString(NAME_INDEX).orEmpty(), it.getString(MIME_INDEX) == DIR, size))
                }
            }
        }
    }

    override fun readText(id: String): String = provider {
        resolver.openInputStream(uriOf(id))?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw IOException("no stream")
    }

    override fun writeText(folderId: String, name: String, text: String) {
        val id = list(folderId).firstOrNull { !it.isFolder && it.name == name }?.id
            ?: create(folderId, TEXT_MIME, name)
        provider {
            resolver.openOutputStream(uriOf(id), TRUNCATE)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                ?: throw IOException("no stream")
        }
    }

    /** A copy cut short is deleted, so another phone never adds half a book. */
    override fun copyInto(folderId: String, name: String, mimeType: String, from: InputStream) {
        val id = create(folderId, mimeType, name)
        try {
            provider {
                val out = resolver.openOutputStream(uriOf(id), TRUNCATE) ?: throw IOException("no stream")
                out.use { from.copyTo(it) }
            }
        } catch (e: IOException) {
            runCatching { DocumentsContract.deleteDocument(resolver, uriOf(id)) }
            throw e
        }
    }

    /** The document's address through the folder's grant: books added from here read through it. */
    override fun documentUri(id: String): String = uriOf(id).toString()

    private fun uriOf(id: String): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)

    /** The display name the provider gave the picked folder, for the screen. */
    fun name(): String = provider {
        resolver.query(uriOf(rootId()), arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { cursor -> cursor.takeIf { it.moveToFirst() }?.getString(0) }.orEmpty()
    }

    private fun create(parentId: String, mimeType: String, name: String): String = provider {
        val made = DocumentsContract.createDocument(resolver, uriOf(parentId), mimeType, name)
            ?: throw IOException("provider made nothing")
        DocumentsContract.getDocumentId(made)
    }

    private companion object {
        const val DIR = Document.MIME_TYPE_DIR
        const val TEXT_MIME = "text/plain"
        const val TRUNCATE = "wt"
        val COLUMNS = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
        )
        const val ID_INDEX = 0
        const val NAME_INDEX = 1
        const val MIME_INDEX = 2
        const val SIZE_INDEX = 3
    }
}

/**
 * Providers fail through several runtime exceptions (a lost grant, a gone folder, a provider crashing mid-call); each
 * becomes an IOException, so one bad provider fails a sync, never the app.
 */
private inline fun <T> provider(block: () -> T): T = try {
    block()
} catch (e: SecurityException) {
    throw IOException("folder grant lost", e)
} catch (e: IllegalArgumentException) {
    throw IOException("folder gone", e)
} catch (e: IllegalStateException) {
    throw IOException("provider failed", e)
} catch (e: UnsupportedOperationException) {
    throw IOException("provider cannot do this", e)
}
