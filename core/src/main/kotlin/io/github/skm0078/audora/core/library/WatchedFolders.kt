// File: core/src/main/kotlin/io/github/skm0078/audora/core/library/WatchedFolders.kt
// Purpose: Watched folders' records and the walk that finds their books, pure Kotlin (plan item 26, ADR-044).

package io.github.skm0078.audora.core.library

import io.github.skm0078.audora.core.config.WatchedFolderConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.File
import java.io.IOException

/**
 * A folder the listener chose (a SAF tree URI) and every book file found in it so far ([seen], document URIs). A
 * book is added once, when first seen; one the listener removes from the library stays seen, so it is not added back.
 */
data class WatchedFolder(val treeUri: String, val name: String, val seen: Set<String> = emptySet())

/** One file or folder in a listing: [id] is the provider's document id. */
data class FolderEntry(val id: String, val name: String, val mimeType: String?, val isFolder: Boolean)

/** Lists a folder's direct children by document id; throws [IOException] when the provider cannot. */
fun interface FolderLister {
    fun children(folderId: String): List<FolderEntry>
}

/**
 * Walks a folder tree breadth first, down to [WatchedFolderConfig.maxDepth] and at most
 * [WatchedFolderConfig.maxEntriesPerScan] entries, and returns the ids of the files that are books. A sub-folder that
 * cannot be listed is skipped (logged by its depth, never its name).
 */
class FolderWalker(private val lister: FolderLister, private val config: WatchedFolderConfig = WatchedFolderConfig()) {
    fun books(rootId: String): List<String> {
        val walk = Walk()
        var level = listOf(rootId)
        for (depth in 0..config.maxDepth) level = walk.level(level, depth)
        return walk.found
    }

    /** One walk's books found and entries listed so far. */
    private inner class Walk {
        val found = ArrayList<String>()
        private var listed = 0

        /** Lists [folders] (one depth), keeps their books and returns their sub-folders. */
        fun level(folders: List<String>, depth: Int): List<String> {
            val next = ArrayList<String>()
            for (folder in folders) {
                if (listed >= config.maxEntriesPerScan) break
                val children = listOrSkip(folder, depth).take(config.maxEntriesPerScan - listed)
                listed += children.size
                next += children.filter { it.isFolder }.map { it.id }
                found += children.filter { !it.isFolder && isBook(it) }.map { it.id }
            }
            return next
        }
    }

    private fun listOrSkip(folder: String, depth: Int): List<FolderEntry> = try {
        lister.children(folder)
    } catch (e: IOException) {
        AudoraLog.w(TAG, "A folder at depth $depth could not be listed: ${e.javaClass.simpleName}")
        emptyList()
    }

    private fun isBook(entry: FolderEntry): Boolean =
        entry.mimeType in config.mimeTypes || entry.name.substringAfterLast('.', "").lowercase() in config.extensions

    private companion object {
        const val TAG = "Audora.Watched"
    }
}

/**
 * The watched folders, kept in one small text file (written whole: temp file, then rename). URIs are stored, never
 * logged: they spell out folder and file names.
 */
class WatchedFolderStore(private val file: File) {
    fun load(): List<WatchedFolder> {
        val lines = try {
            file.takeIf { it.isFile }?.readLines().orEmpty()
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Watched folders unreadable: ${e.javaClass.simpleName}")
            emptyList()
        }
        if (lines.firstOrNull() != VERSION) return emptyList()
        val folders = ArrayList<Triple<String, StringBuilder, MutableSet<String>>>()
        for (line in lines.drop(1)) {
            val value = line.substringAfter(' ', "")
            val last = folders.lastOrNull()
            when (line.substringBefore(' ')) {
                FOLDER -> folders += Triple(value, StringBuilder(), HashSet())
                NAME -> last?.second?.append(value)
                SEEN -> last?.third?.add(value)
            }
        }
        return folders.map { (uri, name, seen) -> WatchedFolder(uri, name.toString(), seen) }
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

    private fun encode(folders: List<WatchedFolder>): String = buildString {
        append(VERSION).append('\n')
        folders.forEach { folder ->
            append(FOLDER).append(' ').append(folder.treeUri).append('\n')
            append(NAME).append(' ').append(folder.name.replace('\n', ' ')).append('\n')
            folder.seen.sorted().forEach { append(SEEN).append(' ').append(it).append('\n') }
        }
    }

    private companion object {
        const val TAG = "Audora.Watched"
        const val VERSION = "audora-watched-folders 1"
        const val FOLDER = "folder"
        const val NAME = "name"
        const val SEEN = "seen"
        const val PARTIAL_SUFFIX = ".partial"
    }
}
