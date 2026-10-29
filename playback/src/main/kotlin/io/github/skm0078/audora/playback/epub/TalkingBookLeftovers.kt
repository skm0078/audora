// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/epub/TalkingBookLeftovers.kt
// Purpose: Deletes the staged chapters of talking books that stopped and were not continued (ADR
//          2026-10-29-jobs-card-and-leftovers).

package io.github.skm0078.audora.playback.epub

import io.github.skm0078.audora.core.files.LeftoverKind
import io.github.skm0078.audora.core.files.LeftoverPolicy
import io.github.skm0078.audora.core.files.LeftoverTally
import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.File

/**
 * A talking book that failed or met Android's time limit keeps its finished chapters in [root] (one folder per book
 * and rendering signature, [StagedChapters]) so Continue renders only the rest; about 30 MB an hour of book. Cancel
 * already deletes them, and starting another book replaces them, but a failed book nobody starts again kept them for
 * good. A folder is deleted once [LeftoverPolicy] lets it go, counted from its newest file. The saved talking book
 * itself is in Documents, never here. Call with no copy running ([io.github.skm0078.audora.playback.export.JobSlots]
 * COPY held).
 */
class TalkingBookLeftovers(private val root: File) {
    fun measure(): LeftoverTally {
        val folders = folders()
        return LeftoverTally.of(LeftoverKind.TALKING_BOOK, folders.size, folders.sumOf(::sizeOf))
    }

    fun bytesFor(bookId: String): Long = foldersOf(bookId).sumOf(::sizeOf)

    /**
     * Deletes what [policy] lets go. [library] holds the books still in the library, by [StagedChapters.bookPrefix]:
     * a book that is gone can never be continued.
     */
    fun clean(policy: LeftoverPolicy, library: Set<String>, asked: Boolean): LeftoverTally = delete(
        folders().filter { folder ->
            val orphan = library.none { folder.name.startsWith(it) }
            policy.mayDelete(newestWrite(folder), orphan, asked)
        },
    )

    /** The listener's "Delete unfinished files" on [bookId]'s stopped or failed talking-book card. */
    fun discard(bookId: String): LeftoverTally = delete(foldersOf(bookId))

    private fun delete(folders: List<File>): LeftoverTally {
        var jobs = 0
        var bytes = 0L
        folders.forEach { folder ->
            val size = sizeOf(folder)
            if (folder.deleteRecursively()) {
                jobs++
                bytes += size
            } else {
                AudoraLog.w(TAG, "Staged talking book not fully deleted")
            }
        }
        if (jobs > 0) AudoraLog.i(TAG, "Staged talking books deleted=$jobs bytes=$bytes")
        return LeftoverTally.of(LeftoverKind.TALKING_BOOK, jobs, bytes)
    }

    private fun folders(): List<File> = root.listFiles { file -> file.isDirectory }.orEmpty().asList()

    private fun foldersOf(bookId: String): List<File> {
        val prefix = StagedChapters.bookPrefix(bookId)
        return folders().filter { it.name.startsWith(prefix) }
    }

    private companion object {
        const val TAG = "Copy.Leftovers"

        fun sizeOf(folder: File): Long = folder.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

        /** A folder's newest write: a chapter staged yesterday keeps the whole book. */
        fun newestWrite(folder: File): Long = folder.walkBottomUp().maxOf { it.lastModified() }
    }
}
