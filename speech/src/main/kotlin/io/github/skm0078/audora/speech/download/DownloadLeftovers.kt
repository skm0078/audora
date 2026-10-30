// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/download/DownloadLeftovers.kt
// Purpose: Finds and deletes half-finished model downloads that nothing resumed (ADR
//          2026-10-29-jobs-card-and-leftovers).

package io.github.skm0078.audora.speech.download

import io.github.skm0078.audora.core.files.LeftoverKind
import io.github.skm0078.audora.core.files.LeftoverPolicy
import io.github.skm0078.audora.core.files.LeftoverTally
import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.File

/**
 * Every model download (voices, acted voices, the story brain, dictionaries, tutor, text recognition, punctuation,
 * voice commands) writes into `<its folder>/downloads/<name>.partial` under the app's no-backup folder [root], and
 * resumes from those bytes on its next attempt. A download that was cancelled and never started again leaves that
 * file for good, hundreds of MB for a voice. This finds them by that one layout, so a new kind of download is
 * covered as long as it keeps it (`DownloadLeftoversTest` checks each owner's path does).
 *
 * Nothing else is touched: installed models, `.partial` folders of an install (their owners tidy those at start)
 * and any file outside a `downloads` folder. While any download is queued or running ([downloading]) none is touched
 * or counted: the one that runs may be appending to its file, and a queued retry would resume from it.
 */
class DownloadLeftovers(private val root: File, private val downloading: () -> Boolean) {
    /** Every half-finished download, wherever its owner keeps it. */
    fun partials(): List<File> = downloadFolders().flatMap { folder ->
        folder.listFiles { file -> file.isFile && file.name.endsWith(PARTIAL_SUFFIX) }.orEmpty().asList()
    }

    /**
     * `<root>/<owner>/downloads` and `<root>/<owner>/<part>/downloads` (acted voices, a dictionary per language).
     * Only folders are listed on the way, so a big cache beside them (studio audio) costs one listing, not a walk.
     */
    private fun downloadFolders(): List<File> {
        val owners = root.subfolders()
        return (owners + owners.flatMap { it.subfolders() })
            .map { File(it, DOWNLOADS) }
            .filter { it.isDirectory }
    }

    /** The half downloads, or none while a download is queued or running (asked only when there are some). */
    private fun idle(): List<File> {
        val found = partials()
        val busy = found.isNotEmpty() && downloading()
        if (busy) AudoraLog.i(TAG, "Half downloads kept: a download is queued or running, found=${found.size}")
        return if (busy) emptyList() else found
    }

    private fun File.subfolders(): List<File> = listFiles { file -> file.isDirectory }.orEmpty().asList()

    /** What is waiting: one per half-finished download; nothing while a download is queued or running. */
    fun measure(): LeftoverTally {
        val files = idle()
        return LeftoverTally.of(LeftoverKind.DOWNLOAD, files.size, files.sumOf { it.length() })
    }

    /**
     * Deletes the half-finished downloads [policy] lets go ([asked]: the listener tapped Clean up now). Returns what
     * was deleted.
     */
    fun clean(policy: LeftoverPolicy, asked: Boolean): LeftoverTally {
        val found = idle()
        var jobs = 0
        var bytes = 0L
        found.filter { policy.mayDelete(it.lastModified(), asked = asked) }.forEach { file ->
            val size = file.length()
            if (file.delete()) {
                jobs++
                bytes += size
            } else {
                AudoraLog.w(TAG, "Half download not deleted: ${file.parentFile?.parentFile?.name}")
            }
        }
        if (jobs > 0) AudoraLog.i(TAG, "Half downloads deleted=$jobs bytes=$bytes of found=${found.size}")
        return LeftoverTally.of(LeftoverKind.DOWNLOAD, jobs, bytes)
    }

    companion object {
        private const val TAG = "Speech.DownloadLeftovers"

        /** The folder every owner keeps its downloads in, and the end of a download's name until it is checked. */
        const val DOWNLOADS = "downloads"
        const val PARTIAL_SUFFIX = ".partial"
    }
}
