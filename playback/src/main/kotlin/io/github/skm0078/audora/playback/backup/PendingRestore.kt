// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/backup/PendingRestore.kt
// Purpose: Puts a staged backup in place at the next start, before anything opens the database (item 28, ADR-046).

package io.github.skm0078.audora.playback.backup

import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.File
import java.io.IOException

/**
 * A restore is staged while the app runs and applied by [applyIfReady] at the next process start, before Room,
 * DataStore or any store has opened a file it replaces; applying it under a live database would corrupt it.
 *
 * Applying copies rather than moves, and the ready marker is removed only after every file is in place, so a crash
 * part-way applies the whole backup again at the next start instead of leaving half of it.
 *
 * @param root the app's data folder (the parent of `files/` and `databases/`).
 */
class PendingRestore(private val root: File, private val contents: BackupContents = BackupContents.DEFAULT) {
    /** Where [BackupArchive.stage] unpacks; inside `no_backup/` so Android's own backup never copies it. */
    val staging: File = File(root, STAGING)

    private val marker = File(root, MARKER)

    /** Drops any staged restore, marker first, so a new staging can never be applied half-written. */
    fun clear() {
        if (marker.exists() && !marker.delete()) throw IOException("marker not removed")
        staging.deleteRecursively()
    }

    /** Marks the staged backup complete; false if the marker could not be written (the restore is then dropped). */
    fun markReady(): Boolean = try {
        marker.writeText(READY)
        true
    } catch (e: IOException) {
        AudoraLog.w(TAG, "Restore not marked ready: ${e.javaClass.simpleName}")
        false
    }

    /**
     * Applies a staged backup if one is marked ready, replacing every allowed file; returns true if it did. Without
     * a marker, or without a staged database (never replace a library with nothing), the staging is dropped.
     */
    fun applyIfReady(): Boolean {
        val staged = contents.existingStaged(staging)
        if (!marker.isFile || staged.none { it.first == BackupContents.DATABASE }) {
            clear()
            return false
        }
        contents.targets(root).forEach { if (it.exists() && !it.deleteRecursively()) throw IOException("not cleared") }
        staged.forEach { (name, file) ->
            val target = File(root, name)
            target.parentFile?.mkdirs()
            file.copyTo(target, overwrite = true)
        }
        if (!marker.delete()) throw IOException("marker not removed")
        staging.deleteRecursively()
        AudoraLog.i(TAG, "Restore applied: ${staged.size} files")
        return true
    }

    /** What [Backups.applyPending] found at start: nothing staged, a restore put in place, or one that failed. */
    enum class Result { NONE, APPLIED, FAILED }

    private fun BackupContents.existingStaged(folder: File): List<Pair<String, File>> {
        val database = File(folder, BackupContents.DATABASE)
        return listOfNotNull((BackupContents.DATABASE to database).takeIf { database.isFile }) + existing(folder)
    }

    private companion object {
        const val TAG = "Backup.Restore"
        const val STAGING = "no_backup/restore-pending"
        const val MARKER = "no_backup/restore-pending.ready"
        const val READY = "ready\n"
    }
}
