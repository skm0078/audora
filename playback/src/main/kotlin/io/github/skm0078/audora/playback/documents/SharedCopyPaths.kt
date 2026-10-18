// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/documents/SharedCopyPaths.kt
// Purpose: Finds a shared PDF copy by its path's last segments (restored backups) and picks unused copies to prune.

package io.github.skm0078.audora.playback.documents

import java.io.File

/** The folder in the app's files that holds [SharedPdfCopies]' copies, one random folder per copy. */
internal const val SHARED_COPIES_DIR = "shared-pdfs"

private const val COPY_SEGMENTS = 3

/**
 * The (folder, name) of a copy's file path, from its last three segments `shared-pdfs/<folder>/<name>`, so a path
 * saved under another files folder (a backup restored to another phone or user) still finds its copy; null for any
 * other path, or one whose folder or name is not a plain file name ("..", empty), so it can never leave the folder.
 */
internal fun copyPathOf(path: String): Pair<String, String>? {
    val parts = path.split('/')
    if (parts.size < COPY_SEGMENTS) return null
    val (root, folder, name) = parts.takeLast(COPY_SEGMENTS)
    return (folder to name).takeIf { root == SHARED_COPIES_DIR && isPlainName(folder) && isPlainName(name) }
}

/**
 * The entries of [dir] (copy folders, or stray files) not named in [kept] whose newest write, anything inside them
 * included, is before [cutoffMillis]. An entry removed while it is looked at is left out.
 */
internal fun strayCopyFolders(dir: File, kept: Set<String>, cutoffMillis: Long): List<File> =
    dir.listFiles().orEmpty().filter { entry ->
        val newest = if (entry.name in kept) null else entry.walk().maxOfOrNull(File::lastModified)
        newest != null && newest > 0L && newest < cutoffMillis
    }

private fun isPlainName(name: String): Boolean =
    name.isNotEmpty() && name != "." && name != ".." && name.none { it == '\\' || it == '\u0000' }
