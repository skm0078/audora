// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/ExportRecords.kt
// Purpose: Remembers which audiobook files an export of a book wrote, so a stopped export goes on (ADR-034).

package io.github.skm0078.audora.playback.export

import android.content.Context
import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

/** A finished chapter file of an earlier run: [location] is where it lives (a MediaStore URI), never logged. */
data class SavedChapter(val number: Int, val name: String, val durationMs: Long, val location: String)

/**
 * What one book's last export wrote. [key] says what it was rendered with (voices, style, folder, chapter plan, app
 * build); [complete] is set once every chapter is written. [pending] are files created but not yet finished (hidden
 * MediaStore entries); a run that stops without finishing one leaves it here for the next run to delete.
 */
data class ExportRecord(
    val key: String,
    val chapters: List<SavedChapter>,
    val complete: Boolean = false,
    val pending: List<String> = emptyList(),
) {
    fun with(chapter: SavedChapter) = copy(
        chapters = chapters.filterNot { it.number == chapter.number } + chapter,
        pending = pending - chapter.location,
    )
}

/**
 * One book's record as it is stored: [hash] names its file (the book id is never stored), [touchedAtMs] is when it
 * was last written, [record] what it says (null when it cannot be read).
 */
data class StoredExportRecord(val hash: String, val touchedAtMs: Long, val record: ExportRecord?)

/**
 * One small text file per book in [folder], named by a hash of the book id and replaced whole (temp file synced
 * to disk, then rename), so a record never describes a file that was not finished, even after a power cut. It
 * holds chapter numbers, durations, file names built from page numbers and the files' MediaStore URIs: never the
 * book's text or title.
 */
class ExportRecords(private val folder: File) {
    fun load(bookId: String): ExportRecord? = read(fileOf(hashOf(bookId)))

    /** Every book's record, for cleaning up what unfinished exports left ([AudiobookLeftovers]). */
    fun stored(): List<StoredExportRecord> = folder.listFiles { file ->
        file.isFile && file.name.endsWith(RECORD_SUFFIX)
    }.orEmpty().map { file ->
        StoredExportRecord(file.name.removeSuffix(RECORD_SUFFIX), file.lastModified(), read(file))
    }

    /** False (logged) if the record could not be written; the next export then starts over. */
    fun save(bookId: String, record: ExportRecord): Boolean = saveStored(hashOf(bookId), record)

    /** Replaces the record stored as [hash]; as [save]. */
    fun saveStored(hash: String, record: ExportRecord): Boolean {
        val file = fileOf(hash)
        val partial = File(folder, file.name + PARTIAL_SUFFIX)
        return try {
            if (!folder.isDirectory && !folder.mkdirs()) throw IOException("no folder")
            FileOutputStream(partial).use { out ->
                out.write(encode(record).toByteArray())
                out.fd.sync()
            }
            if (!partial.renameTo(file)) throw IOException("rename failed")
            true
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Export record not saved: ${e.javaClass.simpleName}")
            partial.delete()
            false
        }
    }

    /** Deletes the record stored as [hash]: the next export of that book starts over. */
    fun deleteStored(hash: String) {
        val file = fileOf(hash)
        if (file.exists() && !file.delete()) AudoraLog.w(TAG, "Export record not deleted")
    }

    /** Temp files a kill left between writing a record and renaming it into place; none while no export runs. */
    fun strayTemps(): List<File> =
        folder.listFiles { file -> file.isFile && file.name.endsWith(PARTIAL_SUFFIX) }.orEmpty().asList()

    private fun read(file: File): ExportRecord? {
        if (!file.isFile) return null
        return try {
            decode(file.readLines())
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Export record unreadable: ${e.javaClass.simpleName}")
            null
        }
    }

    private fun fileOf(hash: String) = File(folder, hash + RECORD_SUFFIX)

    private fun encode(record: ExportRecord): String = buildString {
        append(VERSION).append('\n')
        append(KEY).append(' ').append(record.key).append('\n')
        append(COMPLETE).append(' ').append(if (record.complete) 1 else 0).append('\n')
        record.pending.forEach { append(PENDING).append(' ').append(it).append('\n') }
        record.chapters.sortedBy { it.number }.forEach { chapter ->
            append(CHAPTER).append(' ')
            append(listOf(chapter.number, chapter.durationMs, chapter.location, chapter.name).joinToString(SEPARATOR))
            append('\n')
        }
    }

    /** Null for another version, a missing key or any damaged chapter line: the export then starts over. */
    private fun decode(lines: List<String>): ExportRecord? {
        val fields = lines.drop(1).filter { it.isNotBlank() }.map { it.substringBefore(' ') to it.substringAfter(' ') }
        val key = fields.firstOrNull { it.first == KEY }?.second
        val chapterLines = fields.filter { it.first == CHAPTER }
        val chapters = chapterLines.mapNotNull { chapterOf(it.second) }
        val readable = lines.firstOrNull() == VERSION && key != null && chapters.size == chapterLines.size
        return if (readable) {
            ExportRecord(
                key,
                chapters,
                complete = fields.any { it.first == COMPLETE && it.second == "1" },
                pending = fields.filter { it.first == PENDING && it.second.isNotEmpty() }.map { it.second },
            )
        } else {
            null
        }
    }

    companion object {
        private const val FOLDER = "audiobook-exports"
        private const val TAG = "Export.Records"
        private const val VERSION = "audora-audiobook-export 1"
        private const val KEY = "key"
        private const val COMPLETE = "complete"
        private const val CHAPTER = "chapter"
        private const val PENDING = "pending"
        private const val SEPARATOR = "\t"
        private const val CHAPTER_FIELDS = 4
        private const val LOCATION_FIELD = 2
        private const val NAME_FIELD = 3
        private const val RECORD_SUFFIX = ".txt"
        private const val PARTIAL_SUFFIX = ".partial"
        private const val HASH_HEX_CHARS = 16

        private fun chapterOf(line: String): SavedChapter? {
            val parts = line.split(SEPARATOR, limit = CHAPTER_FIELDS)
            val number = parts.getOrNull(0)?.toIntOrNull()
            val durationMs = parts.getOrNull(1)?.toLongOrNull()
            if (parts.size != CHAPTER_FIELDS || number == null || durationMs == null) return null
            return SavedChapter(number, name = parts[NAME_FIELD], durationMs, location = parts[LOCATION_FIELD])
        }

        /** The app's own records, in its files folder. */
        fun of(context: Context) = ExportRecords(File(context.filesDir, FOLDER))

        /** The name a book's record is stored under ([StoredExportRecord.hash]). */
        fun hashOf(bookId: String): String = MessageDigest.getInstance("SHA-256")
            .digest(bookId.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(HASH_HEX_CHARS)
    }
}
