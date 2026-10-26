// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/FolderSyncConfig.kt
// Purpose: Tunables for syncing through a folder the listener picks (CF-16, ADR 2026-10-26-folder-sync).

package io.github.skm0078.audora.core.config

/**
 * @property minIntervalMs a sync started by the app itself (the library opening, the app going to the background)
 *   within this long of the last one is skipped; Sync now always runs.
 * @property removalKeepMs a removed bookmark or highlight is remembered this long, so a phone that syncs within that
 *   time removes it too; after it, an old copy on a phone that never synced could bring it back.
 * @property maxBookCopyBytes a book larger than this is not copied into the folder (a 1,000-page scan would fill a
 *   cloud folder); its place and notes still sync.
 * @property maxAddsPerSync at most this many books are added from the folder per sync; the rest come next time.
 * @property maxRemoteFileBytes another phone's sync file larger than this is not read.
 */
data class FolderSyncConfig(
    val minIntervalMs: Long = 60_000L,
    val removalKeepMs: Long = DEFAULT_REMOVAL_KEEP_MS,
    val maxBookCopyBytes: Long = DEFAULT_MAX_BOOK_COPY_BYTES,
    val maxAddsPerSync: Int = 20,
    val maxRemoteFileBytes: Long = DEFAULT_MAX_REMOTE_FILE_BYTES,
) {
    init {
        require(minIntervalMs >= 0) { "minIntervalMs must be >= 0, was $minIntervalMs" }
        require(removalKeepMs > 0) { "removalKeepMs must be > 0, was $removalKeepMs" }
        require(maxBookCopyBytes > 0) { "maxBookCopyBytes must be > 0, was $maxBookCopyBytes" }
        require(maxAddsPerSync > 0) { "maxAddsPerSync must be > 0, was $maxAddsPerSync" }
        require(maxRemoteFileBytes > 0) { "maxRemoteFileBytes must be > 0, was $maxRemoteFileBytes" }
    }

    private companion object {
        const val DAY_MS = 86_400_000L
        const val DEFAULT_REMOVAL_KEEP_MS = 180 * DAY_MS
        const val MIB = 1024L * 1024L
        const val DEFAULT_MAX_BOOK_COPY_BYTES = 200 * MIB
        const val DEFAULT_MAX_REMOTE_FILE_BYTES = 32 * MIB
    }
}
