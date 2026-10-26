// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/sync/FolderSync.kt
// Purpose: Syncs the library's places, bookmarks, highlights and (optionally) book files through a folder the
//          listener picks, such as a cloud app's folder (CF-16, ADR 2026-10-26-folder-sync). No server, no account.

package io.github.skm0078.audora.playback.sync

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.skm0078.audora.core.config.FolderSyncConfig
import io.github.skm0078.audora.core.library.AddResult
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.sync.SyncBook
import io.github.skm0078.audora.core.sync.SyncFile
import io.github.skm0078.audora.core.sync.SyncMerge
import io.github.skm0078.audora.core.sync.SyncSettings
import io.github.skm0078.audora.core.sync.SyncSnapshot
import io.github.skm0078.audora.playback.documents.WatchedFolders
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** How a sync went. */
sealed interface SyncResult {
    /** [changes] places and marks taken from other phones, [added] books added, [copied] books copied there. */
    data class Synced(val changes: Int, val added: Int, val copied: Int) : SyncResult

    /** Sync is off: no folder picked. */
    data object NoFolder : SyncResult

    /** Synced less than a minute ago; only Sync now runs again at once. */
    data object Skipped : SyncResult

    /** The folder could not be reached (its app removed it, or took the grant back): pick it again. */
    data object FolderLost : SyncResult

    /** This phone's own sync files could not be saved. */
    data object NotSaved : SyncResult
}

/** Picking a folder: kept, or the folder's app gave no lasting read and write grant. */
enum class SyncChoose { CHOSEN, NO_GRANT, NOT_SAVED }

/** What the sync screen shows. */
data class FolderSyncState(val settings: SyncSettings = SyncSettings(), val running: Boolean = false)

/**
 * The folder holds "ListenLock sync": a text file per phone (`device-<random id>.txt`, [SyncFile]) and, when book
 * copies are on, a Books folder. Each sync reads this phone's books ([SyncLocal]), copies new ones in
 * ([SyncBookFiles]), reads the other phones' files, merges ([SyncMerge]), applies the result and writes this phone's
 * file again. Getting files between phones is the folder's app's job (Drive, Dropbox, a USB stick, Syncthing): this
 * only reads and writes the folder. Syncs never overlap; the grant is the listener's to take back.
 */
@Suppress("LongParameterList") // The last two are for tests: a plain folder instead of the picked one.
class FolderSync(
    context: Context,
    private val stores: SyncStores,
    private val config: FolderSyncConfig = FolderSyncConfig(),
    private val clock: () -> Long = System::currentTimeMillis,
    directories: ((String) -> SyncDirectory)? = null,
    stateDir: File? = null,
) {
    private val resolver: ContentResolver by lazy { context.applicationContext.contentResolver }
    private val files = stateDir ?: context.applicationContext.filesDir
    private val directoryOf = directories ?: { tree -> SyncFolder(resolver, Uri.parse(tree)) }
    private val local = SyncLocal(stores)
    private val merge = SyncMerge(config)
    private val lock = Mutex()
    private val mutableState = MutableStateFlow(FolderSyncState())
    private val settingsFile = File(files, SETTINGS_FILE)
    private val memoryFile = File(files, MEMORY_FILE)

    val state: StateFlow<FolderSyncState> = mutableState.asStateFlow()

    /** Loads the settings for the screen; cheap, call when it opens. */
    suspend fun load() = withContext(Dispatchers.IO) {
        mutableState.value = mutableState.value.copy(settings = settings())
    }

    /** Keeps a read and write grant on [tree] and syncs through it from now on. */
    suspend fun choose(tree: Uri): SyncChoose = lock.withLock {
        withContext(Dispatchers.IO) {
            try {
                resolver.takePersistableUriPermission(tree, READ_WRITE)
            } catch (e: SecurityException) {
                AudoraLog.w(TAG, "No lasting folder grant, authority=${tree.authority}: ${e.javaClass.simpleName}")
                return@withContext SyncChoose.NO_GRANT
            }
            val name = runCatching { SyncFolder(resolver, tree).name() }.getOrDefault("")
            val earlier = settings()
            val chosen = earlier.copy(treeUri = tree.toString(), folderName = name, lastSyncMillis = null)
            if (save(chosen.withDeviceId())) SyncChoose.CHOSEN else SyncChoose.NOT_SAVED
        }
    }

    /** Book copies on or off; copies already in the folder stay there. */
    suspend fun setCopyBooks(on: Boolean) = lock.withLock {
        withContext(Dispatchers.IO) { save(settings().copy(copyBooks = on)) }
    }

    /**
     * Stops syncing. The folder and its files stay (they are the listener's); the grant is given back unless a book
     * added from the folder, or a watched folder, still reads through it.
     */
    suspend fun stop() = lock.withLock {
        withContext(Dispatchers.IO) {
            val current = settings()
            save(current.copy(treeUri = null, folderName = "", lastSyncMillis = null, lastFailed = false))
            current.treeUri?.let { giveBack(Uri.parse(it)) }
        }
    }

    /** Syncs now ([force], Sync now) or when the last sync is older than [FolderSyncConfig.minIntervalMs]. */
    suspend fun sync(force: Boolean): SyncResult = lock.withLock {
        withContext(Dispatchers.IO) {
            val settings = settings().withDeviceId()
            val tree = settings.treeUri ?: return@withContext SyncResult.NoFolder
            val last = settings.lastSyncMillis
            if (!force && last != null && clock() - last < config.minIntervalMs) return@withContext SyncResult.Skipped
            mutableState.value = mutableState.value.copy(running = true)
            val result = try {
                syncThrough(directoryOf(tree), settings)
            } catch (e: IOException) {
                AudoraLog.w(TAG, "Sync folder unreachable, authority=${Uri.parse(tree).authority}: ${e.message}")
                SyncResult.FolderLost
            } finally {
                mutableState.value = mutableState.value.copy(running = false)
            }
            val failed = result == SyncResult.FolderLost || result == SyncResult.NotSaved
            save(settings.copy(lastSyncMillis = clock(), lastFailed = failed))
            result
        }
    }

    private suspend fun syncThrough(folder: SyncDirectory, settings: SyncSettings): SyncResult {
        val now = clock()
        val root = folder.folder(folder.rootId(), ROOT_FOLDER)
        val entries = folder.list(root)
        val others = entries
            .filter { it.name.startsWith(DEVICE_PREFIX) && it.name != fileNameOf(settings.deviceId) }
            .filter { (it.sizeBytes ?: 0L) <= config.maxRemoteFileBytes }
            .mapNotNull { entry -> SyncFile.decode(folder.readText(entry.id)) }
            .filter { it.deviceId != settings.deviceId }
        val memory = SyncFile.decodeMemory(memoryFile.takeIf(File::isFile)?.readText())
        val books = SyncBookFiles(folder, folder.folder(root, BOOKS_FOLDER), stores.documents, config)
        var read = local.read()
        val added = addBooks(merge.merge(read.books, memory, others, now).booksToAdd, books, folder)
        if (added > 0) read = local.read()
        val plan = merge.merge(read.books, memory, others, now)
        val applied = local.apply(plan, read)
        val copied = if (settings.copyBooks) books.copyMissing(read) else 0
        val after = local.read()
        val names = books.names(after.books.map { it.fingerprint })
        val own = SyncSnapshot(
            deviceId = settings.deviceId,
            writtenAtMillis = now,
            books = after.books.map { it.copy(fileName = names[it.fingerprint]) },
            removals = plan.memory.removals,
        )
        folder.writeText(root, fileNameOf(settings.deviceId), SyncFile.encode(own))
        val remembered = plan.memory.copy(
            knownMarks = after.books.flatMapTo(HashSet(), SyncBook::markKeys),
            knownBooks = after.books.mapTo(HashSet()) { it.fingerprint },
        )
        if (!write(memoryFile, SyncFile.encode(remembered))) return SyncResult.NotSaved
        AudoraLog.i(
            TAG,
            "Synced: ${others.size} other phones, ${applied.changes} changes, ${applied.failed} failed, " +
                "$added books added, $copied copied",
        )
        return SyncResult.Synced(applied.changes, added, copied)
    }

    /**
     * Adds the books other phones copied in. A copy whose content is not the book it names (still arriving from the
     * cloud, cut short) is taken out again and tried next sync.
     */
    private suspend fun addBooks(wanted: List<SyncBook>, books: SyncBookFiles, folder: SyncDirectory): Int =
        wanted.count { book ->
            val id = book.fileName?.let(books::idOf) ?: return@count false
            when (val result = stores.library.add(folder.documentUri(id))) {
                is Outcome.Failure -> false.also { AudoraLog.w(TAG, "Book from the folder not added: ${result.error}") }

                is Outcome.Success -> {
                    val same = result.value.book.fingerprint == book.fingerprint
                    if (!same && result.value is AddResult.Added) stores.library.remove(result.value.book.id)
                    same && result.value is AddResult.Added
                }
            }
        }

    private suspend fun giveBack(tree: Uri) {
        val inUse = stores.library.books().first().any { WatchedFolders.folderOf(Uri.parse(it.documentUri)) == tree }
        val watched = WatchedFolders.isWatched(files, tree.toString())
        val flags = if (inUse || watched) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else READ_WRITE
        try {
            resolver.releasePersistableUriPermission(tree, flags)
            AudoraLog.i(TAG, "Sync folder grant given back, kept read=${inUse || watched}")
        } catch (e: SecurityException) {
            AudoraLog.w(TAG, "Sync folder grant not given back: ${e.javaClass.simpleName}")
        }
    }

    private fun settings(): SyncSettings = SyncSettings.decode(settingsFile.takeIf(File::isFile)?.readText())

    private fun save(settings: SyncSettings): Boolean = write(settingsFile, settings.encode()).also {
        if (it) mutableState.value = mutableState.value.copy(settings = settings)
    }

    companion object {
        private const val TAG = "Audora.Sync"
        private const val READ_WRITE = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        private const val SETTINGS_FILE = "folder-sync.txt"
        private const val MEMORY_FILE = "folder-sync-memory.txt"
        const val ROOT_FOLDER = "ListenLock sync"
        const val BOOKS_FOLDER = "Books"
        private const val DEVICE_PREFIX = "device-"

        fun fileNameOf(deviceId: String) = "$DEVICE_PREFIX$deviceId.txt"
    }
}

private const val WRITE_TAG = "Audora.Sync"

/** Written beside, then renamed over: a crash mid-write never leaves half a file. */
private fun write(file: File, text: String): Boolean = try {
    val next = File(file.parentFile, "${file.name}.new")
    next.writeText(text)
    next.renameTo(file) || throw IOException("rename failed")
} catch (e: IOException) {
    AudoraLog.e(WRITE_TAG, "Sync file not saved: ${e.javaClass.simpleName}")
    false
}

private fun SyncSettings.withDeviceId() =
    if (deviceId.isEmpty()) copy(deviceId = UUID.randomUUID().toString()) else this
