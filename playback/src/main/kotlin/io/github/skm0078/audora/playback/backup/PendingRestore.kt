// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/backup/PendingRestore.kt
// Purpose: Puts a staged backup in place at the next start by renames only, rolling back on failure (item 28).

package io.github.skm0078.audora.playback.backup

import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * A restore is staged while the app runs and applied by [applyIfReady] at the next process start, before Room,
 * DataStore or any store has opened a file it replaces; applying it under a live database would corrupt it.
 *
 * Applying is renames only, all inside the app's data folder (one volume), so it needs no free space and takes
 * milliseconds. Each replaced path (a unit: a file, the database's journal files, or a whole folder) first moves
 * aside into this attempt's own folder, then the staged one moves in. The marker file records each step and is
 * written durably (synced, then renamed), and every rename phase is synced too:
 * - `ready`: staged and complete; the next start applies it.
 * - `applying`: renames under way. A start that finds this (a crash, or a roll-back that could not finish) rolls
 *   back again; while it stays, nothing new can be staged ([unfinished]).
 * - `committed`: everything moved in; the restore stands. A start that finds this only tidies up.
 * Rolling back tries every unit on its own, the database with its journals as one group, and keeps what it could not
 * put back (and the marker) for the next start, so a unit is never dropped. What was moved aside or replaced goes to
 * a trash folder that [cleanUp] empties at a later start, once the restored library has opened.
 *
 * [applyIfReady] cannot log: it runs before logging starts. What happened comes back as [Run.notes].
 *
 * @param root the app's data folder (the parent of `files/` and `databases/`).
 */
class PendingRestore(private val root: File, private val contents: BackupContents = BackupContents.DEFAULT) {
    /** Where [BackupArchive.stage] unpacks; inside `no_backup/` so Android's own backup never copies it. */
    val staging: File = File(root, STAGING)

    private val marker = File(root, MARKER)
    private val asides = File(root, ASIDE)
    private val trash = File(root, TRASH)

    /** An earlier restore is half applied or half undone: the next start finishes undoing it first. */
    val unfinished: Boolean get() = readMarker()?.let { it.state != READY } ?: false

    /**
     * Drops any staged restore, marker first, so a new staging can never be applied half-written. The staging folder
     * is renamed into the trash (instant), not deleted. False, and nothing else touched, while [unfinished] or if
     * the marker would not go.
     */
    fun clear(): Boolean {
        val cleared = !unfinished && (!marker.exists() || marker.delete())
        if (cleared) moveToTrash(staging, trash)
        return cleared
    }

    /**
     * Marks the staged backup complete. [known] is the allow-list of the app that wrote it (null if it did not say):
     * a unit it knew but did not hold goes back to its default, while a unit it did not know (a file newer than the
     * backup) is left as it is. False if the marker could not be written (the restore is then dropped).
     */
    fun markReady(known: List<String>?): Boolean {
        if (unfinished) return false
        val knownNow = known?.map(contents::current)
        val replaced = contents.units().filter { unit ->
            unit == BackupContents.DATABASE || knownNow?.contains(unit) ?: File(staging, unit).exists()
        }
        return try {
            staging.walkBottomUp().filter { it.isDirectory }.forEach(::syncFolder)
            writeMarker(Marker(READY, UUID.randomUUID().toString(), orderedUnits(replaced)))
            true
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Restore not marked ready: ${e.javaClass.simpleName}")
            false
        }
    }

    /** Applies, finishes or rolls back whatever the marker says; called once, first thing at process start. */
    fun applyIfReady(): Run {
        val found = readMarker()
        return when {
            found == null -> Run(Result.NONE).also { moveToTrash(staging, trash) }

            found.state == COMMITTED -> finish(found)

            found.state == READY && File(staging, BackupContents.DATABASE).isFile -> apply(found)

            found.state == READY -> Run(Result.NONE, listOf("ready restore without a staged database dropped")).also {
                marker.delete()
                moveToTrash(staging, trash)
            }

            else -> rollBack(found, cause = "an earlier restore was cut short")
        }
    }

    /** Empties the trash and any aside folder no marker owns; call it off the main thread, at a later start. */
    fun cleanUp() {
        if (marker.exists()) return
        trash.deleteRecursively()
        asides.deleteRecursively()
    }

    private fun apply(ready: Marker): Run {
        val aside = File(asides, ready.attempt)
        try {
            writeMarker(ready.copy(state = APPLYING))
            ready.units.forEach { moveAside(root, it, aside) }
            syncParents(ready.units.flatMap { listOf(File(root, it), File(aside, it)) })
            ready.units.forEach { unit -> move(File(staging, unit), File(root, unit)) }
            syncParents(ready.units.map { File(root, it) })
            writeMarker(ready.copy(state = COMMITTED))
        } catch (e: IOException) {
            return rollBack(ready.copy(state = APPLYING), cause = e.javaClass.simpleName)
        }
        return finish(ready)
    }

    /** After the commit point: the old units and the empty staging go to the trash, then the marker. */
    private fun finish(committed: Marker): Run {
        moveToTrash(File(asides, committed.attempt), trash)
        moveToTrash(staging, trash)
        marker.delete()
        marker.parentFile?.let(::syncFolder)
        return Run(Result.APPLIED, listOf("restore applied, units replaced: ${committed.units.joinToString()}"))
    }

    /**
     * Puts back every unit that was moved aside, each on its own; a unit that was never moved aside was never touched.
     * The database's journals go back only if the database did. If everything went back the restore is dropped
     * (FAILED); otherwise the marker stays, so the next start tries again (INCOMPLETE), and nothing is deleted.
     */
    private fun rollBack(applying: Marker, cause: String): Run {
        val folders = if (applying.attempt.isEmpty()) {
            asides.listFiles().orEmpty().filter { it.isDirectory }
        } else {
            listOf(File(asides, applying.attempt))
        }
        val all = orderedUnits(contents.units())
        val moved = folders.flatMap { aside -> all.filter { wasMovedAside(aside, it) } }
        val stuck = folders.flatMap(::putBackAll)
        if (stuck.isNotEmpty()) {
            if (applying.state != APPLYING) writeMarker(applying.copy(state = APPLYING))
            return Run(Result.INCOMPLETE, listOf("restore failed ($cause); still to put back: ${stuck.joinToString()}"))
        }
        folders.forEach { moveToTrash(it, trash) }
        moveToTrash(staging, trash)
        marker.delete()
        marker.parentFile?.let(::syncFolder)
        return Run(Result.FAILED, listOf("restore failed ($cause), rolled back: ${moved.distinct().joinToString()}"))
    }

    /** The units of [aside] that could not be put back. */
    private fun putBackAll(aside: File): List<String> {
        val stuck = mutableListOf<String>()
        orderedUnits(contents.units()).forEach { unit ->
            val databaseStuck = BackupContents.DATABASE in stuck && unit in JOURNALS
            if (databaseStuck || !putBack(root, trash, unit, aside)) {
                if (wasMovedAside(aside, unit)) stuck += unit
            }
        }
        syncParents(orderedUnits(contents.units()).map { File(root, it) })
        return stuck
    }

    private fun writeMarker(state: Marker) {
        val lines = listOf(state.state, state.attempt) + state.units
        writeDurably(marker, lines.joinToString("\n", postfix = "\n").toByteArray())
    }

    /** Null without a marker; a marker that cannot be read as written is read as `applying`, attempt unknown. */
    @Suppress("SwallowedException") // An unreadable marker is handled as a torn one, which is what it is.
    private fun readMarker(): Marker? {
        if (!marker.isFile) return null
        val lines = try {
            marker.readLines()
        } catch (e: IOException) {
            emptyList()
        }
        val state = lines.firstOrNull()
        val known = contents.units().toSet() + JOURNALS
        return if (state in STATES && lines.size >= 2) {
            Marker(state.orEmpty(), lines[1], lines.drop(2).filter { it in known })
        } else {
            Marker(APPLYING, attempt = "", units = emptyList())
        }
    }

    /** [state] is one of [STATES]; [attempt] names this attempt's aside folder (empty when unknown: all of them). */
    private data class Marker(val state: String, val attempt: String, val units: List<String>)

    /** What [Backups.applyPending] found at start, and what to log once logging has started. */
    data class Run(val result: Result, val notes: List<String> = emptyList())

    /**
     * NONE: nothing staged. APPLIED: the restore is in place. FAILED: it failed and was rolled back fully.
     * INCOMPLETE: it failed and part of the old library is still aside; the next start puts it back.
     */
    enum class Result { NONE, APPLIED, FAILED, INCOMPLETE }

    internal companion object {
        private const val TAG = "Backup.Restore"
        private const val STAGING = "no_backup/restore-pending"
        private const val MARKER = "no_backup/restore-pending.ready"
        private const val ASIDE = "no_backup/restore-aside"
        private const val TRASH = "no_backup/restore-trash"
        private const val READY = "ready"
        private const val APPLYING = "applying"
        private const val COMMITTED = "committed"
        private val STATES = setOf(READY, APPLYING, COMMITTED)
        const val NONE = ".none"

        /** The database's journal files, replaced (dropped) whenever the database is. */
        val JOURNALS = BackupContents.DATABASE_SIDE_FILES.map { BackupContents.DATABASE + it }
    }
}

/** The database first, then its journals, then the rest: aside before anything moves in, back as a group. */
private fun orderedUnits(units: List<String>): List<String> {
    val database = units.filter { it == BackupContents.DATABASE }
    val journals = if (database.isEmpty()) emptyList() else PendingRestore.JOURNALS
    return database + journals + units.filter { it !in database }
}

/** Whether [aside] holds [unit] or the record that it was absent. */
private fun wasMovedAside(aside: File, unit: String) =
    File(aside, unit).exists() || File(aside, unit + PendingRestore.NONE).exists()

/** Moves the live unit aside, or records that there was none, so a roll-back knows what to put back. */
private fun moveAside(root: File, unit: String, aside: File) {
    val live = File(root, unit)
    if (live.exists()) {
        move(live, File(aside, unit))
    } else {
        File(aside, unit + PendingRestore.NONE).apply { parentFile?.mkdirs() }.createNewFile()
    }
}

/** One unit back from [aside]; true if done or nothing to do. The restored copy goes to [trash]. */
@Suppress("SwallowedException") // False is the report: the caller lists the unit as still to put back.
private fun putBack(root: File, trash: File, unit: String, aside: File): Boolean {
    val saved = File(aside, unit)
    val none = File(aside, unit + PendingRestore.NONE)
    if (!saved.exists() && !none.exists()) return true
    val live = File(root, unit)
    return try {
        if (live.exists()) move(live, File(File(trash, "replaced-" + UUID.randomUUID()), unit))
        if (saved.exists()) move(saved, live)
        !none.exists() || none.delete()
    } catch (e: IOException) {
        false
    }
}

/** One rename into [trash]; if that fails the file or folder is deleted instead. */
private fun moveToTrash(file: File, trash: File) {
    if (!file.exists()) return
    trash.mkdirs()
    if (!file.renameTo(File(trash, file.name + "-" + UUID.randomUUID()))) file.deleteRecursively()
}

/** Renames [from] to [to] (nothing if [from] is absent), replacing what is there; throws IOException if it can't. */
private fun move(from: File, to: File) {
    if (!from.exists()) return
    to.parentFile?.mkdirs()
    if (to.exists() && !to.deleteRecursively()) throw IOException("target not cleared")
    if (!from.renameTo(to)) throw IOException("not renamed")
}
