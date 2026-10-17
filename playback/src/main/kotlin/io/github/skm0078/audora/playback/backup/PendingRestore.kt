// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/backup/PendingRestore.kt
// Purpose: Puts a staged backup in place at the next start by renames only, rolling back on failure (item 28).

package io.github.skm0078.audora.playback.backup

import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.File
import java.io.IOException

/**
 * A restore is staged while the app runs and applied by [applyIfReady] at the next process start, before Room,
 * DataStore or any store has opened a file it replaces; applying it under a live database would corrupt it.
 *
 * Applying is renames only, all inside the app's data folder (one volume), so it needs no free space and takes
 * milliseconds. Each replaced path (a unit: a file, the database's journal files, or a whole folder) first moves
 * aside, then the staged one moves in. On any failure, or at a start that finds an apply cut short, every unit moves
 * back and the restore is dropped: one failed attempt never repeats at every start. The marker is the commit
 * point: once it is gone, the restore stands. What was moved aside is deleted later by [cleanUp], off the start path.
 *
 * @param root the app's data folder (the parent of `files/` and `databases/`).
 */
class PendingRestore(private val root: File, private val contents: BackupContents = BackupContents.DEFAULT) {
    /** Where [BackupArchive.stage] unpacks; inside `no_backup/` so Android's own backup never copies it. */
    val staging: File = File(root, STAGING)

    private val marker = File(root, MARKER)
    private val aside = File(root, ASIDE)
    private val trash = File(root, TRASH)

    /** Drops any staged restore, marker first, so a new staging can never be applied half-written. */
    fun clear() {
        if (marker.exists() && !marker.delete()) throw IOException("marker not removed")
        staging.deleteRecursively()
    }

    /**
     * Marks the staged backup complete. [known] is the allow-list of the app that wrote it (null if it did not say):
     * a unit it knew but did not hold goes back to its default, while a unit it did not know (a file newer than the
     * backup) is left as it is. False if the marker could not be written (the restore is then dropped).
     */
    fun markReady(known: List<String>?): Boolean {
        val replaced = contents.units().filter { unit ->
            unit == BackupContents.DATABASE || known?.contains(unit) ?: File(staging, unit).exists()
        }
        return try {
            writeMarker(READY, replaced + JOURNALS)
            true
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Restore not marked ready: ${e.javaClass.simpleName}")
            false
        }
    }

    /**
     * Applies a staged backup if one is marked ready. Without a marker, or without a staged database (never replace
     * a library with nothing), the staging is dropped. Throws IOException when the restore failed; it has then been
     * rolled back and dropped.
     */
    fun applyIfReady(): Result {
        val state = marker.takeIf { it.isFile }?.readLines()
        val units = state.orEmpty().drop(1).filter { it in contents.unitsWithJournals() }
        return when {
            state?.firstOrNull() == APPLYING -> {
                rollBack(units)
                throw IOException("an earlier restore was cut short")
            }

            state?.firstOrNull() != READY || !File(staging, BackupContents.DATABASE).isFile -> {
                clear()
                Result.NONE
            }

            else -> apply(units)
        }
    }

    /** Deletes what a finished or rolled-back restore left behind; call it off the main thread. */
    fun cleanUp() {
        if (!marker.exists()) trash.deleteRecursively()
    }

    private fun apply(units: List<String>): Result {
        try {
            writeMarker(APPLYING, units)
            units.forEach(::moveAside)
            units.forEach { unit -> move(File(staging, unit), File(root, unit)) }
            if (!marker.delete()) throw IOException("marker not removed")
        } catch (e: IOException) {
            rollBack(units)
            throw e
        }
        moveToTrash(aside)
        moveToTrash(staging)
        AudoraLog.i(TAG, "Restore applied: ${units.size} units")
        return Result.APPLIED
    }

    /** Moves the live unit aside, or records that there was none, so a roll-back knows what to put back. */
    private fun moveAside(unit: String) {
        val live = File(root, unit)
        if (live.exists()) {
            move(live, File(aside, unit))
        } else {
            File(aside, unit + NONE).apply { parentFile?.mkdirs() }.createNewFile()
        }
    }

    /**
     * Puts every unit that was moved aside back, then drops the restore. A unit not yet moved aside was never
     * touched. If a unit cannot be put back, what was moved aside is kept (not lost) and the error is logged.
     */
    private fun rollBack(units: List<String>) {
        var restored = true
        units.forEach { unit ->
            val saved = File(aside, unit)
            val none = File(aside, unit + NONE)
            if (saved.exists() || none.exists()) {
                val live = File(root, unit)
                restored = restored && (!live.exists() || live.deleteRecursively()) &&
                    (!saved.exists() || saved.renameTo(live)) && (!none.exists() || none.delete())
            }
        }
        marker.delete()
        moveToTrash(staging)
        if (restored) moveToTrash(aside) else AudoraLog.e(TAG, "Restore roll-back incomplete; kept in no_backup")
    }

    /** One rename; the folder is emptied later by [cleanUp]. */
    private fun moveToTrash(folder: File) {
        if (!folder.exists()) return
        trash.mkdirs()
        if (!folder.renameTo(File(trash, folder.name + System.nanoTime()))) folder.deleteRecursively()
    }

    private fun writeMarker(state: String, units: List<String>) {
        val temp = File(marker.path + TEMP)
        marker.parentFile?.mkdirs()
        temp.writeText((listOf(state) + units).joinToString("\n", postfix = "\n"))
        if (!temp.renameTo(marker)) throw IOException("marker not written")
    }

    /** What [Backups.applyPending] found at start: nothing staged, a restore put in place, or one that failed. */
    enum class Result { NONE, APPLIED, FAILED }

    private companion object {
        const val TAG = "Backup.Restore"
        const val STAGING = "no_backup/restore-pending"
        const val MARKER = "no_backup/restore-pending.ready"
        const val ASIDE = "no_backup/restore-aside"
        const val TRASH = "no_backup/restore-trash"
        const val READY = "ready"
        const val APPLYING = "applying"
        const val NONE = ".none"
        const val TEMP = ".tmp"
    }
}

/** The database's journal files, replaced (dropped) whenever the database is. */
private val JOURNALS = BackupContents.DATABASE_SIDE_FILES.map { BackupContents.DATABASE + it }

private fun BackupContents.unitsWithJournals(): Set<String> = (units() + JOURNALS).toSet()

/** Renames [from] to [to] (nothing if [from] is absent), replacing what is there; throws IOException if it can't. */
private fun move(from: File, to: File) {
    if (!from.exists()) return
    to.parentFile?.mkdirs()
    if (to.exists() && !to.deleteRecursively()) throw IOException("target not cleared")
    if (!from.renameTo(to)) throw IOException("not renamed")
}
