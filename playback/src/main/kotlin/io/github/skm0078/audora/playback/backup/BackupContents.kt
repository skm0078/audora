// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/backup/BackupContents.kt
// Purpose: The allow-list of what a backup holds, as paths under the app's data folder (plan item 28, ADR-046).

package io.github.skm0078.audora.playback.backup

import io.github.skm0078.audora.core.quiz.QuizProgressStore
import java.io.File

/**
 * What a backup carries, as `/`-separated paths relative to the app's data folder (the parent of `files/`,
 * `databases/` and `no_backup/`), so an entry's name is also where it is restored. Only these paths are written,
 * unpacked or replaced: an entry outside them (a newer app's file, or a crafted `../` name) is skipped.
 *
 * Left out on purpose: voices, OCR and dictionary data (large, downloaded again), which voice each language uses
 * (it may name a voice the new phone lacks), covers and search indexes (rebuilt), logs, export records and studio
 * audio (they point at files on the old phone), the widget, and the first-run flag, so a restored phone still goes
 * through first run. Watched folders are kept: their grants do not move, so they ask to be picked again (ADR-044).
 *
 * @property files single files.
 * @property folders folders taken whole, every file below them.
 */
data class BackupContents(val files: List<String>, val folders: List<String>) {
    /** True if [path] (an entry name) is one of [files] or lies below one of [folders], with no `.` or `..` part. */
    fun allows(path: String): Boolean {
        val parts = path.split('/')
        val plain = parts.none { it.isEmpty() || it == "." || it == ".." } && '\\' !in path
        return plain && (path in files || folders.any { path.startsWith("$it/") })
    }

    /** Each allowed file that exists under [root], as (entry name, file), in a stable order. */
    fun existing(root: File): List<Pair<String, File>> {
        val single = files.filter { it != DATABASE }.map { it to File(root, it) }.filter { it.second.isFile }
        val below = folders.flatMap { folder ->
            File(root, folder).walkTopDown().filter { it.isFile }
                .map { "$folder/" + it.relativeTo(File(root, folder)).invariantSeparatorsPath to it }
                .sortedBy { it.first }
                .toList()
        }
        return (single + below).filter { allows(it.first) }
    }

    /** What a restore replaces, each whole: the listed files and folders (the database's journals go with it). */
    fun units(): List<String> = files + folders

    companion object {
        /** The database's entry: written from a snapshot, never copied from the live file. */
        const val DATABASE = "databases/audora.db"

        /** SQLite's journal files beside the database; stale ones would be replayed over the restored database. */
        val DATABASE_SIDE_FILES = listOf("-wal", "-shm", "-journal")

        /**
         * The app's files today. The names repeat each owner's private constant; a renamed file silently drops out
         * of backups, so a rename updates this list too.
         */
        val DEFAULT = BackupContents(
            files = listOf(
                DATABASE,
                "files/reading-text.properties",
                "files/up-next.txt",
                "files/speech-style.properties",
                "files/sleep-options.txt",
                "files/watched-folders.txt",
                "files/focus-sound.txt",
                "files/${QuizProgressStore.FILE_NAME}",
                "files/datastore/reader.preferences_pb",
                "files/datastore/reading_look.preferences_pb",
                "files/datastore/appearance.preferences_pb",
                "no_backup/voices/managed/voice-speeds.properties",
            ),
            // characters/: per-book character names for character voices (item 63), the listener's own content.
            folders = listOf("files/voice-notes", "files/shared-pdfs", "files/characters"),
        )
    }
}
