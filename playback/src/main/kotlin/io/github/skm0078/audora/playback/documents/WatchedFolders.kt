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
import io.github.skm0078.audora.core.library.FolderAdd
import io.github.skm0078.audora.core.library.FolderEntry
import io.github.skm0078.audora.core.library.FolderLister
import io.github.skm0078.audora.core.library.FolderScan
import io.github.skm0078.audora.core.library.FolderWalk
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

/** How picking a folder went; each has its own message. */
enum class WatchResult { WATCHED, NO_GRANT, NOT_SAVED }

/** A scan's outcome: [added] books joined; [unreachable] watched folders need picking again. */
data class WatchScan(val added: Int, val unreachable: Int)

/**
 * The listener picks a folder (OpenDocumentTree); Audora keeps a lasting read grant on it and, when the library
 * opens, lists it ([FolderWalker]) and adds the books it has not seen before ([FolderScan]). No background job:
 * nothing runs while the app is closed. Books in the folder are read through the folder's grant
 * ([SafDocumentAccess] accepts it), so they need no grant of their own, and a folder's grant is kept while any of its
 * books is in the library, even after the folder stops being watched. Only grants Audora took for a watched folder
 * are ever given back. Main-safe; scans never overlap.
 */
class WatchedFolders(
    context: Context,
    private val library: Library,
    private val config: WatchedFolderConfig = WatchedFolderConfig(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val resolver: ContentResolver = context.applicationContext.contentResolver
    private val store = WatchedFolderStore(File(context.applicationContext.filesDir, FILE_NAME))
    private val lock = Mutex()

    /** The watched folders; read without waiting for a scan (the file is replaced whole, never half-written). */
    suspend fun folders(): List<WatchedFolder> =
        withContext(Dispatchers.IO) { store.load().orEmpty().filter { it.watched } }

    /** Keeps a grant on [treeUri] and watches it; watching a folder again keeps what it had seen. */
    suspend fun watch(treeUri: Uri): WatchResult = lock.withLock {
        withContext(Dispatchers.IO) {
            try {
                resolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                AudoraLog.w(TAG, "No lasting grant, authority=${treeUri.authority}: ${e.javaClass.simpleName}")
                return@withContext WatchResult.NO_GRANT
            }
            val folders = store.load() ?: return@withContext WatchResult.NOT_SAVED
            val uri = treeUri.toString()
            val earlier = folders.firstOrNull { it.treeUri == uri } ?: WatchedFolder(uri, "")
            val watched = earlier.copy(name = nameOf(treeUri), watched = true, granted = true, reachable = true)
            val saved = store.save(folders.filter { it.treeUri != uri } + watched)
            AudoraLog.i(TAG, "Folder watched, authority=${treeUri.authority}, again=${earlier.seen.isNotEmpty()}")
            lastScanMs = null // The next scan runs at once, whatever the interval.
            if (saved) WatchResult.WATCHED else WatchResult.NOT_SAVED
        }
    }

    /** Stops watching [folder]; its books stay in the library and readable, its grant goes when they do. */
    suspend fun unwatch(folder: WatchedFolder) = lock.withLock {
        withContext(Dispatchers.IO) {
            val folders = store.load() ?: return@withContext
            val updated = folders.map { if (it.treeUri == folder.treeUri) it.copy(watched = false) else it }
            store.save(releaseUnusedGrants(updated, libraryUris()))
            AudoraLog.i(TAG, "Folder no longer watched, ${updated.count { it.watched }} left")
        }
    }

    /**
     * Adds the books not seen before in the watched folders. Skipped (nothing added) within
     * [WatchedFolderConfig.minScanIntervalMs] of the last scan, unless a folder was watched since.
     */
    suspend fun scan(): WatchScan = lock.withLock {
        withContext(Dispatchers.IO) {
            val now = clock()
            val last = lastScanMs
            if (last != null && now - last < config.minScanIntervalMs) return@withContext WatchScan(0, 0)
            lastScanMs = now
            // An unreadable record is never saved over and no grant is given back on its account.
            val folders = store.load() ?: return@withContext WatchScan(0, 0)
            if (folders.isEmpty()) return@withContext WatchScan(0, 0)
            val inLibrary = libraryUris()
            val grants = resolver.persistedTrees()
            val folderScan = FolderScan(inLibrary, now, config)
            var added = 0
            var tried = 0
            val scanned = folders.map { folder ->
                when {
                    !folder.watched -> folder

                    folder.treeUri !in grants -> folder.copy(reachable = false)

                    else -> {
                        val result = folderScan.scan(folder, walk(folder), config.maxAddsPerScan - tried) { uri ->
                            library.add(uri).toFolderAdd()
                        }
                        added += result.added
                        tried += result.tried
                        result.folder
                    }
                }
            }
            store.save(releaseUnusedGrants(scanned, inLibrary))
            val unreachable = scanned.count { it.watched && !it.reachable }
            AudoraLog.i(
                TAG,
                "Watched folders scanned: ${scanned.count { it.watched }} folders, $added added, " +
                    "$tried tried, $unreachable unreachable",
            )
            WatchScan(added, unreachable)
        }
    }

    private suspend fun libraryUris(): Set<String> = library.books().first().mapTo(HashSet()) { it.documentUri }

    /**
     * Gives back the grant of each folder Audora took one for that is no longer watched and holds no library book,
     * and marks it not granted. Grants Audora did not take for a watched folder are never touched.
     */
    private fun releaseUnusedGrants(folders: List<WatchedFolder>, inLibrary: Set<String>): List<WatchedFolder> {
        val needed = inLibrary.mapNotNullTo(HashSet()) { uri -> folderOf(Uri.parse(uri))?.toString() }
        val persisted = resolver.persistedTrees()
        return folders.map { folder ->
            val unused = folder.granted && !folder.watched && folder.treeUri !in needed
            if (unused && folder.treeUri in persisted) release(folder) else folder
        }
    }

    private fun release(folder: WatchedFolder): WatchedFolder {
        val tree = Uri.parse(folder.treeUri)
        return try {
            resolver.releasePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            AudoraLog.i(TAG, "Unused folder grant given back, authority=${tree.authority}")
            folder.copy(granted = false)
        } catch (e: SecurityException) {
            AudoraLog.w(TAG, "Folder grant not given back: ${e.javaClass.simpleName}")
            folder
        }
    }

    /** The folder's books as document URIs; a folder that cannot be listed at all comes back with rootListed false. */
    private fun walk(folder: WatchedFolder): FolderWalk {
        val tree = Uri.parse(folder.treeUri)
        return try {
            val rootId = providerCall { DocumentsContract.getTreeDocumentId(tree) }
            val walk = FolderWalker(lister(tree), config).walk(rootId)
            walk.copy(books = walk.books.map { DocumentsContract.buildDocumentUriUsingTree(tree, it).toString() })
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Watched folder unreadable, authority=${tree.authority}: ${e.javaClass.simpleName}")
            FolderWalk(emptyList(), complete = false, rootListed = false)
        }
    }

    private fun lister(tree: Uri) = FolderLister { folderId ->
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, folderId)
        val cursor = providerCall { resolver.query(children, COLUMNS, null, null, null) }
            ?: throw IOException("provider returned no listing")
        providerCall {
            cursor.use {
                buildList {
                    while (it.moveToNext()) {
                        val id = it.getString(ID_INDEX) ?: continue // A provider bug; the entry is skipped.
                        val mime = it.getString(MIME_INDEX)
                        add(FolderEntry(id, it.getString(NAME_INDEX).orEmpty(), mime, mime == DIR_MIME))
                    }
                }
            }
        }
    }

    private fun nameOf(tree: Uri): String = try {
        providerCall {
            val document = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            resolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { cursor -> cursor.takeIf { it.moveToFirst() }?.getString(0) }
        }.orEmpty()
    } catch (e: IOException) {
        AudoraLog.w(TAG, "Folder name unreadable: ${e.javaClass.simpleName}")
        ""
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

        // Process-wide, like the file: the activity (and this object) is recreated on rotation, the throttle is not.
        @Volatile
        private var lastScanMs: Long? = null

        /** Whether [treeUri] is a watched folder: folder sync (CF-16) keeps its read grant then. Blocking. */
        fun isWatched(filesDir: File, treeUri: String): Boolean =
            WatchedFolderStore(File(filesDir, FILE_NAME)).load().orEmpty().any { it.watched && it.treeUri == treeUri }

        /** The folder (tree) URI a document was opened through, or null for a document opened on its own. */
        fun folderOf(document: Uri): Uri? = if (DocumentsContract.isTreeUri(document)) {
            DocumentsContract.buildTreeDocumentUri(document.authority, DocumentsContract.getTreeDocumentId(document))
        } else {
            null
        }

        /**
         * Providers fail through several runtime exceptions (a lost grant, a gone folder, a provider crashing
         * mid-call); each becomes an IOException so one bad provider never crashes the library screen.
         */
        private inline fun <T> providerCall(block: () -> T): T = try {
            block()
        } catch (e: SecurityException) {
            throw IOException("folder grant lost", e)
        } catch (e: IllegalArgumentException) {
            throw IOException("folder gone", e)
        } catch (e: IllegalStateException) {
            throw IOException("provider failed", e)
        } catch (e: UnsupportedOperationException) {
            throw IOException("provider cannot list", e)
        }
    }
}

/** The folders (tree URIs) this app holds a lasting read grant on. */
private fun ContentResolver.persistedTrees(): Set<String> = persistedUriPermissions
    .filter { it.isReadPermission && DocumentsContract.isTreeUri(it.uri) }
    .mapTo(HashSet()) { it.uri.toString() }

private fun Outcome<AddResult, LibraryError>.toFolderAdd(): FolderAdd = when (this) {
    is Outcome.Success -> if (value is AddResult.Added) FolderAdd.ADDED else FolderAdd.KNOWN
    is Outcome.Failure -> FolderAdd.FAILED
}
