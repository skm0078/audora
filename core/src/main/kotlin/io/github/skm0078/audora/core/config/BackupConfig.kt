// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/BackupConfig.kt
// Purpose: Tunables for backup and restore: one file that moves the library and settings to a new phone (item 28).

package io.github.skm0078.audora.core.config

/**
 * A restore reads a file the user picked, which may not be one Audora wrote, so every limit counts the bytes
 * actually unpacked, never the sizes an entry claims.
 *
 * @property maxEntryBytes the largest single file a restore unpacks (a book shared into the app can be big).
 * @property maxTotalBytes the most a restore unpacks in all; staging needs this much free space at worst.
 * @property maxEntries the most files a restore unpacks, so a crafted archive of tiny entries stops early.
 * @property bufferBytes the copy buffer for writing and unpacking.
 * @property minFreeBytes free space a restore leaves on the phone; a file that would go below it stops the restore.
 */
data class BackupConfig(
    val maxEntryBytes: Long = 2_147_483_648L, // 2 GiB
    val maxTotalBytes: Long = 8_589_934_592L, // 8 GiB
    val maxEntries: Int = 20_000,
    val bufferBytes: Int = 65_536,
    val minFreeBytes: Long = 134_217_728L, // 128 MiB
) {
    init {
        require(maxEntryBytes > 0) { "maxEntryBytes must be > 0, was $maxEntryBytes" }
        require(maxTotalBytes >= maxEntryBytes) { "maxTotalBytes must be >= maxEntryBytes, was $maxTotalBytes" }
        require(maxEntries > 0) { "maxEntries must be > 0, was $maxEntries" }
        require(bufferBytes > 0) { "bufferBytes must be > 0, was $bufferBytes" }
        require(minFreeBytes >= 0) { "minFreeBytes must be >= 0, was $minFreeBytes" }
    }
}
