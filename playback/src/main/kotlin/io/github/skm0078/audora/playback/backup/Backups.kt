// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/backup/Backups.kt
// Purpose: Saves a backup to a file the user picked and stages a picked backup for the next start (item 28).

package io.github.skm0078.audora.playback.backup

import android.app.ActivityManager
import android.content.Context
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.DocumentsContract
import io.github.skm0078.audora.core.config.BackupConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.epub.BookCopyService
import io.github.skm0078.audora.playback.export.AudiobookExportService
import io.github.skm0078.audora.playback.export.ClipService
import io.github.skm0078.audora.playback.export.JobSlots
import io.github.skm0078.audora.playback.storage.AudoraStorage
import io.github.skm0078.audora.playback.studio.StudioService
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How a backup job ended, kept until the screen has shown it ([Backups.consume]). */
sealed interface BackupEvent {
    data class Saved(val ok: Boolean) : BackupEvent

    data class Refused(val error: BackupError) : BackupEvent

    /** A restore is staged; the app restarts to put it in place. */
    data object RestartNeeded : BackupEvent
}

/** [working] while a file is written or read; [event] once it ended, until shown. */
data class BackupState(val working: Boolean = false, val event: BackupEvent? = null)

/**
 * One zip holds the library, reading positions, bookmarks, highlights, voice notes, pronunciations, per-book
 * settings, the Up next queue, the reading settings and books shared into the app ([BackupContents]). Restoring
 * replaces all of them: it is meant for a new phone, not for merging two libraries.
 *
 * One per process: jobs run in its own scope, so turning the phone or leaving the screen never loses one, and the
 * screen reads [state] to show progress and the outcome.
 *
 * Books opened from the phone's storage come back with their positions and notes but need their file again
 * (folder access does not move between phones): adding the same file relinks the book by its fingerprint.
 */
class Backups(context: Context, private val storage: AudoraStorage, config: BackupConfig = BackupConfig()) {
    private val appContext = context.applicationContext
    private val contents = BackupContents.DEFAULT
    private val archive = BackupArchive(contents, config)
    private val restore = PendingRestore(dataFolder(appContext), contents)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow(BackupState())

    val state: StateFlow<BackupState> = mutableState.asStateFlow()

    init {
        // What an earlier restore moved aside or replaced; kept until a later start has opened the library it put in
        // place, then deleted here, off the start path (ADR-046).
        if (!restoredThisStart) scope.launch { cleanUpOnceOpened() }
    }

    /** Starts writing a backup to [target] (a document the user created); ignored while another job runs. */
    fun startSave(target: Uri) = start { BackupEvent.Saved(save(target) is Outcome.Success) }

    /**
     * Starts unpacking the backup at [source] for the next start; ignored while another job runs. Refused while
     * an export, copy, clip or studio run is going, since the restart would cut it off.
     */
    fun startRestore(source: Uri) = start {
        if (busy(appContext)) {
            AudoraLog.i(TAG, "Restore refused: a long job is running")
            BackupEvent.Refused(BackupError.BUSY)
        } else {
            restoreUnderWay = true
            when (val staged = stage(source)) {
                is Outcome.Success -> BackupEvent.RestartNeeded

                is Outcome.Failure -> {
                    restoreUnderWay = false
                    BackupEvent.Refused(staged.error)
                }
            }
        }
    }

    /**
     * Called just before the restart that applies a staged restore. False if a long job started meanwhile (from a
     * notification, say): the staged restore is then dropped, rather than cutting the job off; the caller says why.
     */
    fun confirmRestart(): Boolean {
        if (!busy(appContext)) return true
        AudoraLog.i(TAG, "Restart for restore refused: a long job is running")
        scope.launch {
            restore.clear()
            restoreUnderWay = false
        }
        return false
    }

    /** Clears the outcome once the screen has shown it. */
    fun consume() = mutableState.update { it.copy(event = null) }

    /** About how many bytes a backup made now would hold, before compression: the database and every listed file. */
    suspend fun sizeEstimate(): Long = withContext(Dispatchers.IO) {
        val root = dataFolder(appContext)
        File(root, BackupContents.DATABASE).length() + contents.existing(root).sumOf { it.second.length() }
    }

    private fun start(job: suspend () -> BackupEvent) {
        if (mutableState.value.working) return
        mutableState.value = BackupState(working = true)
        scope.launch { mutableState.value = BackupState(event = job()) }
    }

    private suspend fun save(target: Uri): Outcome<BackupSummary, BackupError> = withContext(Dispatchers.IO) {
        val snapshot = File(appContext.cacheDir, SNAPSHOT)
        try {
            snapshot.delete()
            storage.snapshotTo(snapshot)
            val manifest =
                BackupManifest(BackupManifest.CURRENT_FORMAT, AudoraStorage.SCHEMA_VERSION, now(), contents.units())
            val out = appContext.contentResolver.openOutputStream(target, WRITE)
                ?: throw IOException("no output stream")
            val root = dataFolder(appContext)
            val summary = out.use { archive.write(it, manifest, snapshot, root) }
            val absent = contents.files.count { it != BackupContents.DATABASE && !File(root, it).isFile }
            AudoraLog.i(TAG, "Backup saved: ${summary.files} files, ${summary.bytes} bytes, $absent listed not present")
            Outcome.Success(summary)
        } catch (e: IOException) {
            failed(appContext, target, e)
        } catch (e: SQLException) {
            failed(appContext, target, e)
        } catch (e: SecurityException) {
            failed(appContext, target, e)
        } finally {
            snapshot.delete()
        }
    }

    /**
     * Unpacks the backup at [source], checks its database and marks it ready; the screen then restarts the app,
     * and [applyPending] puts it in place before anything opens the database.
     */
    private suspend fun stage(source: Uri): Outcome<BackupManifest, BackupError> = withContext(Dispatchers.IO) {
        if (!restore.clear()) {
            AudoraLog.w(TAG, "Restore refused: an earlier restore is still being undone, or its marker stuck")
            return@withContext Outcome.Failure(BackupError.UNFINISHED)
        }
        val unpacked = unpack(source)
        val checked = if (unpacked is Outcome.Success) {
            checkDatabase(File(restore.staging, BackupContents.DATABASE))?.let { Outcome.Failure(it) } ?: unpacked
        } else {
            unpacked
        }
        val ready = when (checked) {
            is Outcome.Failure -> checked

            is Outcome.Success -> when {
                // Staging can take minutes: a job started meanwhile would be cut off by the restart.
                busy(appContext) -> Outcome.Failure(BackupError.BUSY)

                restore.markReady(checked.value.units) -> checked

                else -> Outcome.Failure(BackupError.IO_FAILED)
            }
        }
        if (ready is Outcome.Failure) restore.clear()
        ready
    }

    private fun unpack(source: Uri): Outcome<BackupManifest, BackupError> = try {
        appContext.contentResolver.openInputStream(source)?.use { input ->
            archive.stage(input, restore.staging, AudoraStorage.SCHEMA_VERSION)
        } ?: Outcome.Failure(BackupError.IO_FAILED)
    } catch (e: IOException) {
        AudoraLog.w(TAG, "Backup not opened: ${e.javaClass.simpleName}")
        Outcome.Failure(BackupError.IO_FAILED)
    } catch (e: SecurityException) {
        AudoraLog.w(TAG, "Backup not opened: ${e.javaClass.simpleName}")
        Outcome.Failure(BackupError.IO_FAILED)
    }

    /** Deletes what earlier restores left, once the library opens: a broken restored database keeps the old one. */
    @Suppress("TooGenericExceptionCaught") // Whatever stops the library opening, nothing is deleted; logged.
    private fun cleanUpOnceOpened() {
        try {
            if (restore.unfinished) return
            storage.identityHash()
            restore.cleanUp()
        } catch (e: Exception) {
            AudoraLog.w(TAG, "Restore leftovers kept: ${e.javaClass.simpleName}")
        }
    }

    /** What [applyPending] did; [error] names the exception's class when it failed; [notes] what to log. */
    data class StartRestore(
        val result: PendingRestore.Result,
        val error: String? = null,
        val notes: List<String> = emptyList(),
    )

    companion object {
        private const val TAG = "Backup"
        private const val SNAPSHOT = "backup-snapshot.db"

        // "w" truncates on every provider that supports writing; some reject "wt".
        private const val WRITE = "w"
        private const val QUICK_CHECK = "PRAGMA quick_check"
        private const val QUICK_CHECK_OK = "ok"
        private val LONG_JOBS = setOf(
            AudiobookExportService::class.java.name,
            ClipService::class.java.name,
            StudioService::class.java.name,
            BookCopyService::class.java.name,
        )

        @Volatile
        private var startResult: PendingRestore.Result? = null

        @Volatile
        private var restoredThisStart = false

        /**
         * True from the moment a restore starts staging until it is refused or the app restarts to apply it: the
         * long-job services refuse to start meanwhile, since the restart would cut them off.
         */
        @Volatile
        var restoreUnderWay = false
            private set

        /**
         * Applies a staged restore, if any. The application calls this first thing at process start, before Hilt
         * builds anything that opens the database or preferences, and logs the result once logging has started
         * (this runs before it). A failed restore has been rolled back and dropped, or, if part of it could not be
         * put back, is rolled back again at the next start; the library says which once. An unexpected exception
         * counts as the latter: the marker is left as it was, so the next start sees it again.
         */
        @Suppress("TooGenericExceptionCaught") // Nothing may stop the app from starting; the class name is kept.
        fun applyPending(context: Context): StartRestore {
            val restored = try {
                PendingRestore(dataFolder(context)).applyIfReady().let { StartRestore(it.result, notes = it.notes) }
            } catch (e: Exception) {
                StartRestore(PendingRestore.Result.INCOMPLETE, e.javaClass.simpleName)
            }
            if (restored.result != PendingRestore.Result.NONE) {
                startResult = restored.result
                restoredThisStart = true
            }
            return restored
        }

        /** The restore this process started with (applied or failed), once; null after the first call or if none. */
        fun takeStartResult(): PendingRestore.Result? = startResult.also { startResult = null }

        /**
         * True while one of the app's long jobs runs: an export, a copy, a clip or studio mode. The job slots also see
         * a worker its service left running after Android's time limit stopped the service.
         */
        @Suppress("DEPRECATION") // getRunningServices still lists the caller's own services, which is all this needs.
        private fun busy(context: Context): Boolean = JobSlots.anyBusy ||
            context.getSystemService(ActivityManager::class.java)?.getRunningServices(Int.MAX_VALUE).orEmpty()
                .any { it.started && it.service.className in LONG_JOBS }

        private fun failed(context: Context, target: Uri, e: Exception): Outcome<BackupSummary, BackupError> {
            // The class name only: exception messages can carry the document's URI.
            AudoraLog.e(TAG, "Backup not saved: ${e.javaClass.simpleName}")
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, target) }
                .onFailure { AudoraLog.w(TAG, "Half-written backup not deleted: ${it.javaClass.simpleName}") }
            return Outcome.Failure(BackupError.IO_FAILED)
        }

        /**
         * Opens the staged database read-only before anything is replaced: a newer schema than the manifest claimed,
         * a damaged file, or one whose schema is not the one this app's Room wrote at that version (a foreign or
         * hand-made database, which Room's migration check would refuse at every start) is refused. Null when it can be
         * restored.
         */
        private fun checkDatabase(file: File): BackupError? = try {
            val error = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val version = db.version
                when {
                    version > AudoraStorage.SCHEMA_VERSION -> BackupError.FROM_NEWER_APP

                    db.firstString(QUICK_CHECK) != QUICK_CHECK_OK -> BackupError.NOT_A_BACKUP

                    db.firstString(AudoraStorage.IDENTITY_HASH_QUERY) != SchemaHashes.of(version) ->
                        BackupError.NOT_A_BACKUP

                    else -> null
                }
            }
            // Opening may leave journal files beside it; they must not be restored over the live database's.
            BackupContents.DATABASE_SIDE_FILES.forEach { File(file.path + it).delete() }
            if (error != null) AudoraLog.w(TAG, "Staged database refused: $error")
            error
        } catch (e: SQLException) {
            AudoraLog.w(TAG, "Staged database unreadable: ${e.javaClass.simpleName}")
            BackupError.NOT_A_BACKUP
        }

        private fun dataFolder(context: Context): File = context.dataDir

        private fun now() = System.currentTimeMillis()

        private fun SQLiteDatabase.firstString(sql: String): String? =
            rawQuery(sql, null).use { if (it.moveToFirst()) it.getString(0) else null }
    }
}
