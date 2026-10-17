// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/documents/WatchedFolders.kt
// Purpose: Watched folders over the Storage Access Framework: new books there join the library (item 26, ADR-044).

package io.github.skm0078.audora.playback.documents

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import io.github.skm0078.audora.core.config.WatchedFolderConfig
import io.github.skm0078.audora.core.library.AddResult
import io.github.skm0078.audora.core.library.FolderEntry
import io.github.skm0078.audora.core.library.FolderLister
import io.github.skm0078.audora.core.library.FolderWalker
import io.github.skm0078.audora.core.library.Library
import io.github.skm0078.audora.core.library.LibraryError
import io.github.skm0078.audora.core.library.WatchedFolder
import io.github.skm0078.audora.core.library.WatchedFolderStore
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The listener picks a folder (OpenDocumentTree); Audora keeps a lasting read grant on it and, each time the library
 * opens, lists it ([FolderWalker]) and adds the books it has not seen before. No background job: nothing runs while
 * the app is closed. Books in the folder are read through the folder's grant ([SafDocumentAccess] accepts it), so
 * they need no grant of their own, and a folder's grant is kept while any of its books is in the library, even after
 * the folder stops being watched; the scan gives back grants nothing uses any more. Main-safe; scans never overlap.
 */
class WatchedFolders(
    context: Context,
    private val library: Library,
    private val config: WatchedFolderConfig = WatchedFolderConfig(),
) {
    private val resolver: ContentResolver = context.applicationContext.contentResolver
    private val store = WatchedFolderStore(File(context.applicationContext.filesDir, FILE_NAME))
    private val lock = Mutex()

    suspend fun folders(): List<WatchedFolder> = lock.withLock { withContext(Dispatchers.IO) { store.load() } }

    /** Keeps a grant on [treeUri] and watches it; false (logged) when the folder's app gives no lasting grant. */
    suspend fun watch(treeUri: Uri): Boolean = lock.withLock {
        withContext(Dispatchers.IO) {
            try {
                resolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                AudoraLog.w(
                    TAG,
                    "No lasting grant on the folder, authority=${treeUri.authority}: ${e.javaClass.simpleName}",
                )
                return@withContext false
            }
            val folders = store.load().filter { it.treeUri != treeUri.toString() }
            AudoraLog.i(TAG, "Folder watched, authority=${treeUri.authority}, ${folders.size + 1} in all")
            store.save(folders + WatchedFolder(treeUri.toString(), nameOf(treeUri)))
        }
    }

    /** Stops watching [folder]; its books stay in the library and readable, its grant goes when they do. */
    suspend fun unwatch(folder: WatchedFolder) = lock.withLock {
        withContext(Dispatchers.IO) {
            val folders = store.load().filter { it.treeUri != folder.treeUri }
            store.save(folders)
            releaseUnusedGrants(folders, libraryUris())
            AudoraLog.i(TAG, "Folder no longer watched, ${folders.size} left")
        }
    }

    /** Adds every book not seen before in the watched folders; returns how many joined the library. */
    suspend fun scan(): Int = lock.withLock {
        withContext(Dispatchers.IO) {
            val folders = store.load()
            val inLibrary = libraryUris()
            releaseUnusedGrants(folders, inLibrary)
            if (folders.isEmpty()) return@withContext 0
            var added = 0
            val scanned = folders.map { folder ->
                val result = scanFolder(folder, inLibrary, config.maxAddsPerScan - added)
                added += result.added
                folder.copy(seen = folder.seen + result.seen)
            }
            store.save(scanned)
            AudoraLog.i(TAG, "Watched folders scanned: ${folders.size} folders, $added books added")
            added
        }
    }

    private suspend fun libraryUris(): Set<String> = library.books().first().mapTo(HashSet()) { it.documentUri }

    /**
     * Gives back folder grants that no watched folder and no library book needs. Every lasting folder grant Audora
     * holds is a watched folder's (the voice import reads its folder once, without one).
     */
    private fun releaseUnusedGrants(folders: List<WatchedFolder>, inLibrary: Set<String>) {
        val needed = folders.mapTo(HashSet()) { it.treeUri } +
            inLibrary.mapNotNull { uri -> folderOf(Uri.parse(uri))?.toString() }
        resolver.persistedUriPermissions
            .filter { DocumentsContract.isTreeUri(it.uri) && it.uri.toString() !in needed }
            .forEach { grant ->
                try {
                    resolver.releasePersistableUriPermission(grant.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    AudoraLog.i(TAG, "Unused folder grant given back, authority=${grant.uri.authority}")
                } catch (e: SecurityException) {
                    AudoraLog.w(TAG, "Folder grant not given back: ${e.javaClass.simpleName}")
                }
            }
    }

    private class FolderScan(val seen: Set<String>, val added: Int)

    private suspend fun scanFolder(folder: WatchedFolder, inLibrary: Set<String>, allowed: Int): FolderScan {
        val tree = Uri.parse(folder.treeUri)
        val found = try {
            FolderWalker(lister(tree), config).books(DocumentsContract.getTreeDocumentId(tree))
                .map { DocumentsContract.buildDocumentUriUsingTree(tree, it).toString() }
        } catch (e: IllegalArgumentException) {
            AudoraLog.w(TAG, "Watched folder unreadable: ${e.javaClass.simpleName}")
            emptyList()
        }
        val (known, unknown) = found.filter { it !in folder.seen }.partition { it in inLibrary }
        val seen = HashSet(known)
        var added = 0
        for (uri in unknown) {
            if (added >= allowed) break
            when (val result = library.add(uri)) {
                is Outcome.Success -> {
                    seen += uri
                    if (result.value is AddResult.Added) added++
                }

                // A file that is not a book stays seen; one unreadable now (a cloud file offline) is tried next time.
                is Outcome.Failure -> if (result.error == LibraryError.NotAPdf) seen += uri
            }
        }
        return FolderScan(seen, added)
    }

    private fun lister(tree: Uri) = FolderLister { folderId ->
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, folderId)
        val cursor = try {
            resolver.query(children, COLUMNS, null, null, null)
        } catch (e: SecurityException) {
            throw IOException("folder grant lost", e)
        } catch (e: IllegalArgumentException) {
            throw IOException("folder gone", e)
        } ?: throw IOException("provider returned no listing")
        cursor.use {
            buildList {
                while (it.moveToNext()) {
                    val mime = it.getString(MIME_INDEX)
                    add(FolderEntry(it.getString(ID_INDEX), it.getString(NAME_INDEX).orEmpty(), mime, mime == DIR_MIME))
                }
            }
        }
    }

    private fun nameOf(tree: Uri): String {
        val document = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        return try {
            resolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { cursor -> cursor.takeIf { it.moveToFirst() }?.getString(0) }
        } catch (e: SecurityException) {
            AudoraLog.w(TAG, "Folder name unreadable: ${e.javaClass.simpleName}")
            null
        }.orEmpty()
    }

    companion object {
        private const val TAG = "Audora.Watched"
        private const val FILE_NAME = "watched-folders.txt"
        private const val DIR_MIME = DocumentsContract.Document.MIME_TYPE_DIR
        private val COLUMNS = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        private const val ID_INDEX = 0
        private const val NAME_INDEX = 1
        private const val MIME_INDEX = 2

        /** The folder (tree) URI a document was opened through, or null for a document opened on its own. */
        fun folderOf(document: Uri): Uri? = if (DocumentsContract.isTreeUri(document)) {
            DocumentsContract.buildTreeDocumentUri(document.authority, DocumentsContract.getTreeDocumentId(document))
        } else {
            null
        }
    }
}
