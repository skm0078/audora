// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/backup/FileSync.kt
// Purpose: Flushes written files and renamed folders to storage, so a power cut cannot undo a restore's steps.

package io.github.skm0078.audora.playback.backup

import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

private const val TAG = "Backup.Sync"

/** Writes [bytes] to [target] by a synced temporary file and a rename; throws IOException if it can't. */
internal fun writeDurably(target: File, bytes: ByteArray) {
    val temp = File(target.path + ".tmp")
    target.parentFile?.mkdirs()
    FileOutputStream(temp).use { out ->
        out.write(bytes)
        out.fd.sync()
    }
    if (!temp.renameTo(target)) throw IOException("not renamed into place")
    target.parentFile?.let(::syncFolder)
}

/**
 * Flushes a folder's entries (the renames in it). Best effort: a file system that refuses is logged, not fatal,
 * since the restore is still correct without a power cut.
 */
internal fun syncFolder(folder: File) {
    if (!folder.isDirectory) return
    try {
        FileChannel.open(folder.toPath(), StandardOpenOption.READ).use { it.force(true) }
    } catch (e: IOException) {
        AudoraLog.w(TAG, "Folder not synced: ${e.javaClass.simpleName}")
    } catch (e: UnsupportedOperationException) {
        AudoraLog.w(TAG, "Folder not synced: ${e.javaClass.simpleName}")
    }
}

/** [syncFolder] on each distinct parent of [paths]. */
internal fun syncParents(paths: Iterable<File>) {
    paths.mapNotNull { it.parentFile }.distinct().forEach(::syncFolder)
}
