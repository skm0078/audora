// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/backup/Backups.kt
// Purpose: Saves a backup to a file the user picked and stages a picked backup for the next start (item 28).

package io.github.skm0078.audora.playback.backup

import android.content.Context
import android.database.SQLException
import android.net.Uri
import android.provider.DocumentsContract
import io.github.skm0078.audora.core.config.BackupConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.storage.AudoraStorage
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One zip holds the library, reading positions, bookmarks, highlights, voice notes, pronunciations, per-book
 * settings, the Up next queue, the reading settings and books shared into the app ([BackupContents]). Restoring
 * replaces all of them: it is meant for a new phone, not for merging two libraries.
 *
 * Books opened from the phone's storage come back with their positions and notes but need their file again
 * (folder access does not move between phones): adding the same file relinks the book by its fingerprint.
 */
class Backups(context: Context, private val storage: AudoraStorage, config: BackupConfig = BackupConfig()) {
    private val appContext = context.applicationContext
    private val archive = BackupArchive(BackupContents.DEFAULT, config)
    private val restore = PendingRestore(dataFolder(appContext))

    /** Writes a backup to [target] (a document the user created); on failure the half-written file is deleted. */
    suspend fun save(target: Uri): Outcome<BackupSummary, BackupError> = withContext(Dispatchers.IO) {
        val snapshot = File(appContext.cacheDir, SNAPSHOT)
        try {
            snapshot.delete()
            storage.snapshotTo(snapshot)
            val manifest = BackupManifest(BackupManifest.CURRENT_FORMAT, AudoraStorage.SCHEMA_VERSION, now())
            val out = appContext.contentResolver.openOutputStream(target, WRITE_TRUNCATE)
                ?: throw IOException("no output stream")
            val summary = out.use { archive.write(it, manifest, snapshot, dataFolder(appContext)) }
            AudoraLog.i(TAG, "Backup saved: ${summary.files} files, ${summary.bytes} bytes")
            Outcome.Success(summary)
        } catch (e: IOException) {
            failed(target, e)
        } catch (e: SQLException) {
            failed(target, e)
        } finally {
            snapshot.delete()
        }
    }

    /**
     * Unpacks the backup at [source] for the next start and marks it ready; the caller then restarts the app, and
     * [applyPending] puts it in place before anything opens the database.
     */
    suspend fun stage(source: Uri): Outcome<BackupManifest, BackupError> = withContext(Dispatchers.IO) {
        val result = try {
            restore.clear()
            appContext.contentResolver.openInputStream(source)?.use { input ->
                archive.stage(input, restore.staging, AudoraStorage.SCHEMA_VERSION)
            } ?: Outcome.Failure(BackupError.IO_FAILED)
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Backup not opened: ${e.javaClass.simpleName}")
            Outcome.Failure(BackupError.IO_FAILED)
        }
        if (result is Outcome.Success && !restore.markReady()) {
            restore.staging.deleteRecursively()
            Outcome.Failure(BackupError.IO_FAILED)
        } else {
            result
        }
    }

    private fun failed(target: Uri, e: Exception): Outcome<BackupSummary, BackupError> {
        // The class name only: exception messages can carry the document's URI.
        AudoraLog.e(TAG, "Backup not saved: ${e.javaClass.simpleName}")
        runCatching { DocumentsContract.deleteDocument(appContext.contentResolver, target) }
            .onFailure { AudoraLog.w(TAG, "Half-written backup not deleted: ${it.javaClass.simpleName}") }
        return Outcome.Failure(BackupError.IO_FAILED)
    }

    /** What [applyPending] did; [error] names the exception's class when it failed. */
    data class StartRestore(val result: PendingRestore.Result, val error: String? = null)

    companion object {
        private const val TAG = "Backup"
        private const val SNAPSHOT = "backup-snapshot.db"
        private const val WRITE_TRUNCATE = "wt"

        /**
         * Applies a staged restore, if any. The application calls this first thing at process start, before Hilt
         * builds anything that opens the database or preferences, and logs the result once logging has started
         * (this runs before it). A failed restore stays staged and is tried again at the next start.
         */
        fun applyPending(context: Context): StartRestore = try {
            val applied = PendingRestore(dataFolder(context)).applyIfReady()
            StartRestore(if (applied) PendingRestore.Result.APPLIED else PendingRestore.Result.NONE)
        } catch (e: IOException) {
            StartRestore(PendingRestore.Result.FAILED, e.javaClass.simpleName)
        }

        private fun dataFolder(context: Context): File = context.dataDir

        private fun now() = System.currentTimeMillis()
    }
}
