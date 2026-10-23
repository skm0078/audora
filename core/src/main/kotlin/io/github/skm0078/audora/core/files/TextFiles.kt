// File: core/src/main/kotlin/io/github/skm0078/audora/core/files/TextFiles.kt
// Purpose: Small listener files written so a crash or power cut never tears them, and bad ones kept, not overwritten.

package io.github.skm0078.audora.core.files

import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

object TextFiles {
    private const val TAG = "Audora.Files"
    private const val TEMP = ".tmp"
    private const val ASIDE = ".unreadable-"

    /** Writes [text] to [file] by a synced temporary file and a rename; throws IOException if it can't. */
    fun writeDurably(file: File, text: String) = writeDurably(file, text.toByteArray(Charsets.UTF_8))

    /**
     * Writes [bytes] to [file] by a synced temporary file and a rename, then syncs the folder so the rename itself
     * survives a power cut; throws IOException if it can't (the temporary file is removed).
     */
    fun writeDurably(file: File, bytes: ByteArray) {
        val temp = File(file.path + TEMP)
        try {
            file.parentFile?.mkdirs()
            FileOutputStream(temp).use { out ->
                out.write(bytes)
                out.fd.sync()
            }
            if (!temp.renameTo(file)) throw IOException("rename failed")
        } catch (e: IOException) {
            temp.delete()
            throw e
        }
        file.parentFile?.let(::syncFolder)
    }

    /**
     * Flushes a folder's entries (the renames in it). Best effort: a file system that refuses is logged, not fatal,
     * since what was written is still correct without a power cut.
     */
    fun syncFolder(folder: File) {
        if (!folder.isDirectory) return
        try {
            FileChannel.open(folder.toPath(), StandardOpenOption.READ).use { it.force(true) }
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Folder not synced: ${e.javaClass.simpleName}")
        } catch (e: UnsupportedOperationException) {
            AudoraLog.w(TAG, "Folder not synced: ${e.javaClass.simpleName}")
        }
    }

    /**
     * Renames a file the app cannot read (damaged, or written by a newer version) beside itself with a timestamp, so
     * the next save starts fresh without destroying what was there. False (logged) if it would not move.
     */
    fun setAside(file: File, nowMs: Long = System.currentTimeMillis()): Boolean {
        val aside = File(file.path + ASIDE + nowMs)
        val moved = file.renameTo(aside)
        if (moved) {
            AudoraLog.w(
                TAG,
                "An unreadable file was set aside",
            )
        } else {
            AudoraLog.w(TAG, "An unreadable file stayed")
        }
        return moved
    }
}
