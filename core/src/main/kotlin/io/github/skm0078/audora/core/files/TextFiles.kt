// File: core/src/main/kotlin/io/github/skm0078/audora/core/files/TextFiles.kt
// Purpose: Small listener files written so a crash or power cut never tears them, and bad ones kept, not overwritten.

package io.github.skm0078.audora.core.files

import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object TextFiles {
    private const val TAG = "Audora.Files"
    private const val TEMP = ".tmp"
    private const val ASIDE = ".unreadable-"

    /** Writes [text] to [file] by a synced temporary file and a rename; throws IOException if it can't. */
    fun writeDurably(file: File, text: String) {
        val temp = File(file.path + TEMP)
        try {
            file.parentFile?.mkdirs()
            FileOutputStream(temp).use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
                out.fd.sync()
            }
            if (!temp.renameTo(file)) throw IOException("rename failed")
        } catch (e: IOException) {
            temp.delete()
            throw e
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
