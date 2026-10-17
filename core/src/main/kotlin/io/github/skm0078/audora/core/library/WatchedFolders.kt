// File: core/src/main/kotlin/io/github/skm0078/audora/core/library/WatchedFolders.kt
// Purpose: Watched folders' records, the walk that finds their books and one folder's scan, pure Kotlin (item 26).

package io.github.skm0078.audora.core.library

import io.github.skm0078.audora.core.config.WatchedFolderConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.File
import java.io.IOException

/** A file that could not be added: tried [failures] times, next no earlier than [nextAtMs]. */
data class RetryAt(val failures: Int, val nextAtMs: Long)

/**
 * A folder the listener chose (a SAF tree URI). Kept after it stops being watched, so watching it again does not
 * bring back books the listener removed, and so Audora knows which folder grants are its own to give back.
 *
 * @property seen book files found in it and handled (added, or already in the library), as document URIs. A book the
 *   listener removes from the library stays seen and is not added back.
 * @property watched false once the listener removed the folder from the list.
 * @property granted Audora holds a lasting grant on the folder that it took itself; only such grants are given back.
 * @property reachable false when the last scan found no grant or could not list the folder (a restore on a new
 *   phone, a revoked grant, the folder gone): the list then asks for it to be picked again.
 * @property retries files that failed to add, to try again later.
 */
data class WatchedFolder(
    val treeUri: String,
    val name: String,
    val seen: Set<String> = emptySet(),
    val watched: Boolean = true,
    val granted: Boolean = true,
    val reachable: Boolean = true,
    val retries: Map<String, RetryAt> = emptyMap(),
)

/** One file or folder in a listing: [id] is the provider's document id. */
data class FolderEntry(val id: String, val name: String, val mimeType: String?, val isFolder: Boolean)

/** Lists a folder's direct children by document id; throws [IOException] when the provider cannot. */
fun interface FolderLister {
    fun children(folderId: String): List<FolderEntry>
}

/**
 * What a walk found: [books] (document ids, or URIs once mapped); [complete] when every folder was listed within the
 * entry limit, so files missing from [books] are really gone; [rootListed] false when the folder itself could not be
 * listed.
 */
data class FolderWalk(val books: List<String>, val complete: Boolean, val rootListed: Boolean)

/**
 * Walks a folder tree breadth first, down to [WatchedFolderConfig.maxDepth] and at most
 * [WatchedFolderConfig.maxEntriesPerScan] entries, and returns the files that are books. Hidden entries (a leading
 * "."), such as MediaStore's `.trashed-` and `.pending-` copies, are skipped. A sub-folder that cannot be listed is
 * skipped (logged by its depth, never its name).
 */
class FolderWalker(private val lister: FolderLister, private val config: WatchedFolderConfig = WatchedFolderConfig()) {
    fun walk(rootId: String): FolderWalk {
        val walk = Walk()
        var level = listOf(rootId)
        for (depth in 0..config.maxDepth) level = walk.level(level, depth)
        if (walk.capped) AudoraLog.w(TAG, "Watched folder walk stopped at ${config.maxEntriesPerScan} entries")
        // Depth is not part of it: files below the depth limit are never found, so forgetting them changes nothing.
        val complete = !walk.capped && walk.failures == 0
        return FolderWalk(walk.found, complete, rootListed = walk.rootListed)
    }

    fun books(rootId: String): List<String> = walk(rootId).books

    /** One walk's books found and entries listed so far. */
    private inner class Walk {
        val found = ArrayList<String>()
        var failures = 0
        var capped = false
        var rootListed = true
        private var listed = 0

        /** Lists [folders] (one depth), keeps their books and returns their sub-folders. */
        fun level(folders: List<String>, depth: Int): List<String> {
            val next = ArrayList<String>()
            for (folder in folders) {
                capped = capped || listed >= config.maxEntriesPerScan
                if (capped) break
                val all = listOrNull(folder, depth)
                if (all == null) {
                    failures++
                    rootListed = rootListed && depth > 0
                }
                val children = all.orEmpty().filterNot { it.name.startsWith(HIDDEN_PREFIX) }
                val taken = children.take(config.maxEntriesPerScan - listed)
                capped = capped || taken.size < children.size
                listed += taken.size
                next += taken.filter { it.isFolder }.map { it.id }
                found += taken.filter { !it.isFolder && isBook(it) }.map { it.id }
            }
            return next
        }
    }

    private fun listOrNull(folder: String, depth: Int): List<FolderEntry>? = try {
        lister.children(folder)
    } catch (e: IOException) {
        AudoraLog.w(TAG, "A folder at depth $depth could not be listed: ${e.javaClass.simpleName}")
        null
    }

    private fun isBook(entry: FolderEntry): Boolean =
        entry.mimeType in config.mimeTypes || entry.name.substringAfterLast('.', "").lowercase() in config.extensions

    private companion object {
        const val TAG = "Audora.Watched"
        const val HIDDEN_PREFIX = "."
    }
}

/** How adding one found file went. */
enum class FolderAdd {
    /** It joined the library. */
    ADDED,

    /** It is a book the library already has (the same file or the same content). */
    KNOWN,

    /** It could not be added now (unreadable, offline, empty, not a book): tried again later. */
    FAILED,
}

/** A folder after a scan, and how many books joined and files were tried. */
data class FolderScanResult(val folder: WatchedFolder, val added: Int, val tried: Int)

/**
 * One scan's pass over each folder's [FolderWalk], at [nowMs] with the library's document URIs [inLibrary]: books
 * already in the library become seen, others are tried (at most `budget` per folder, failures included), and a
 * failure waits before the next try. After a complete walk, seen and retried files no longer in the folder are
 * forgotten, so a new book saved under a removed file's name joins.
 */
class FolderScan(
    private val inLibrary: Set<String>,
    private val nowMs: Long,
    private val config: WatchedFolderConfig = WatchedFolderConfig(),
) {
    suspend fun scan(
        folder: WatchedFolder,
        walk: FolderWalk,
        budget: Int,
        add: suspend (String) -> FolderAdd,
    ): FolderScanResult {
        val (known, unknown) = walk.books.filter { it !in folder.seen }.partition { it in inLibrary }
        val seen = HashSet(folder.seen).apply { addAll(known) }
        val retries = HashMap(folder.retries)
        var added = 0
        var tried = 0
        for (uri in unknown.filter { (retries[it]?.nextAtMs ?: 0L) <= nowMs }) {
            if (tried >= budget) break
            tried++
            val result = add(uri)
            if (result == FolderAdd.FAILED) {
                retries[uri] = nextTry(retries[uri])
            } else {
                seen += uri
                retries -= uri
                if (result == FolderAdd.ADDED) added++
            }
        }
        if (walk.complete) {
            val present = walk.books.toHashSet()
            seen.retainAll(present)
            retries.keys.retainAll(present)
        }
        val scanned = folder.copy(seen = seen, retries = retries, reachable = walk.rootListed)
        return FolderScanResult(scanned, added, tried)
    }

    private fun nextTry(previous: RetryAt?): RetryAt {
        val failures = (previous?.failures ?: 0) + 1
        val shift = (failures - 1).coerceAtMost(MAX_DOUBLINGS)
        val delay = (config.retryBaseMs shl shift).coerceAtMost(config.retryMaxMs)
        return RetryAt(failures, nowMs + delay)
    }

    private companion object {
        // Past this many doublings the delay is capped anyway; the bound keeps the shift from overflowing.
        const val MAX_DOUBLINGS = 20
    }
}

/**
 * The watched folders, kept in one small text file (written whole: temp file, then rename). URIs are stored, never
 * logged: they spell out folder and file names.
 */
class WatchedFolderStore(private val file: File) {
    /** No file yet: no folders. Null when the file exists but cannot be read or is not ours: never save over it. */
    fun load(): List<WatchedFolder>? {
        if (!file.exists()) return emptyList()
        val lines = try {
            file.readLines()
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Watched folders unreadable: ${e.javaClass.simpleName}")
            null
        }
        return if (lines?.firstOrNull() == VERSION) decode(lines.drop(1)) else null
    }

    /** False (logged) when the file could not be written; the folders then stay as they were on disk. */
    fun save(folders: List<WatchedFolder>): Boolean {
        val partial = File(file.path + PARTIAL_SUFFIX)
        return try {
            file.parentFile?.mkdirs()
            partial.writeText(encode(folders))
            if (!partial.renameTo(file)) throw IOException("rename failed")
            true
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Watched folders not saved: ${e.javaClass.simpleName}")
            partial.delete()
            false
        }
    }

    private fun decode(lines: List<String>): List<WatchedFolder> {
        val folders = ArrayList<WatchedFolder>()
        for (line in lines) {
            val value = line.substringAfter(' ', "")
            val key = line.substringBefore(' ')
            if (key == FOLDER) {
                folders += WatchedFolder(value, "")
            } else {
                folders.lastOrNull()?.let { folders[folders.lastIndex] = it.with(key, value) }
            }
        }
        return folders
    }

    private fun WatchedFolder.with(key: String, value: String): WatchedFolder = when (key) {
        NAME -> copy(name = value)
        SEEN -> copy(seen = seen + value)
        WATCHED -> copy(watched = value == ON)
        GRANTED -> copy(granted = value == ON)
        REACHABLE -> copy(reachable = value == ON)
        RETRY -> retryOf(value)?.let { (uri, at) -> copy(retries = retries + (uri to at)) } ?: this
        else -> this
    }

    private fun retryOf(value: String): Pair<String, RetryAt>? {
        val parts = value.split(' ', limit = RETRY_FIELDS)
        val failures = parts.getOrNull(0)?.toIntOrNull()
        val nextAt = parts.getOrNull(1)?.toLongOrNull()
        val uri = parts.getOrNull(2)
        return if (failures != null && nextAt != null && uri != null) uri to RetryAt(failures, nextAt) else null
    }

    private fun encode(folders: List<WatchedFolder>): String = buildString {
        append(VERSION).append('\n')
        folders.forEach { folder ->
            append(FOLDER).append(' ').append(folder.treeUri).append('\n')
            append(NAME).append(' ').append(folder.name.replace('\n', ' ')).append('\n')
            append(WATCHED).append(' ').append(flag(folder.watched)).append('\n')
            append(GRANTED).append(' ').append(flag(folder.granted)).append('\n')
            append(REACHABLE).append(' ').append(flag(folder.reachable)).append('\n')
            folder.seen.sorted().forEach { append(SEEN).append(' ').append(it).append('\n') }
            folder.retries.toSortedMap().forEach { (uri, at) ->
                append(RETRY).append(' ').append(at.failures).append(' ').append(at.nextAtMs).append(' ').append(uri)
                append('\n')
            }
        }
    }

    private fun flag(on: Boolean) = if (on) ON else OFF

    private companion object {
        const val TAG = "Audora.Watched"
        const val VERSION = "audora-watched-folders 1"
        const val FOLDER = "folder"
        const val NAME = "name"
        const val SEEN = "seen"
        const val WATCHED = "watched"
        const val GRANTED = "granted"
        const val REACHABLE = "reachable"
        const val RETRY = "retry"
        const val ON = "1"
        const val OFF = "0"
        const val RETRY_FIELDS = 3
        const val PARTIAL_SUFFIX = ".partial"
    }
}
