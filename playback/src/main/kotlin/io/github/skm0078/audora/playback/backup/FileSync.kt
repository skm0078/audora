// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/backup/FileSync.kt
// Purpose: Flushes written files and renamed folders to storage, so a power cut cannot undo a restore's steps.

package io.github.skm0078.audora.playback.backup

import io.github.skm0078.audora.core.files.TextFiles
import java.io.File

/** Writes [bytes] to [target] durably ([TextFiles.writeDurably]: synced file, rename, synced folder). */
internal fun writeDurably(target: File, bytes: ByteArray) = TextFiles.writeDurably(target, bytes)

/** Flushes a folder's entries (the renames in it), best effort ([TextFiles.syncFolder]). */
internal fun syncFolder(folder: File) = TextFiles.syncFolder(folder)

/** [syncFolder] on each distinct parent of [paths]. */
internal fun syncParents(paths: Iterable<File>) {
    paths.mapNotNull { it.parentFile }.distinct().forEach(::syncFolder)
}
