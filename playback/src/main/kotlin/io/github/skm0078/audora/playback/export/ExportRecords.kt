// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/ExportRecords.kt
// Purpose: Remembers which audiobook files an export of a book wrote, so a stopped export goes on (ADR-034).

package io.github.skm0078.audora.playback.export

import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** A finished chapter file of an earlier run: [location] is where it lives (a MediaStore URI), never logged. */
data class SavedChapter(val number: Int, val name: String, val durationMs: Long, val location: String)

/**
 * What one book's last export wrote. [key] says what it was rendered with (voices, style, folder, chapter plan, app
 * build); [complete] is set once every chapter is written.
 */
data class ExportRecord(val key: String, val chapters: List<SavedChapter>, val complete: Boolean = false) {
    fun with(chapter: SavedChapter) = copy(chapters = chapters.filterNot { it.number == chapter.number } + chapter)
}

/**
 * One small text file per book in [folder], named by a hash of the book id and replaced whole (temp file, then
 * rename), so a record never describes a file that was not finished. It holds chapter numbers, durations, file names
 * built from page numbers and the files' MediaStore URIs: never the book's text or title.
 */
class ExportRecords(private val folder: File) {
    fun load(bookId: String): ExportRecord? {
        val file = fileOf(bookId)
        if (!file.isFile) return null
        return try {
            decode(file.readLines())
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Export record unreadable: ${e.javaClass.simpleName}")
            null
        }
    }

    /** False (logged) if the record could not be written; the next export then starts over. */
    fun save(bookId: String, record: ExportRecord): Boolean {
        val file = fileOf(bookId)
        val partial = File(folder, file.name + PARTIAL_SUFFIX)
        return try {
            if (!folder.isDirectory && !folder.mkdirs()) throw IOException("no folder")
            partial.writeText(encode(record))
            if (!partial.renameTo(file)) throw IOException("rename failed")
            true
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Export record not saved: ${e.javaClass.simpleName}")
            partial.delete()
            false
        }
    }

    fun delete(bookId: String) {
        val file = fileOf(bookId)
        if (file.exists() && !file.delete()) AudoraLog.w(TAG, "Export record not deleted")
    }

    private fun fileOf(bookId: String) = File(folder, hash(bookId) + RECORD_SUFFIX)

    private fun encode(record: ExportRecord): String = buildString {
        append(VERSION).append('\n')
        append(KEY).append(' ').append(record.key).append('\n')
        append(COMPLETE).append(' ').append(if (record.complete) 1 else 0).append('\n')
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
            ExportRecord(key, chapters, complete = fields.any { it.first == COMPLETE && it.second == "1" })
        } else {
            null
        }
    }

    private fun chapterOf(line: String): SavedChapter? {
        val parts = line.split(SEPARATOR, limit = CHAPTER_FIELDS)
        val number = parts.getOrNull(0)?.toIntOrNull()
        val durationMs = parts.getOrNull(1)?.toLongOrNull()
        if (parts.size != CHAPTER_FIELDS || number == null || durationMs == null) return null
        return SavedChapter(number, name = parts[NAME_FIELD], durationMs, location = parts[LOCATION_FIELD])
    }

    private companion object {
        const val TAG = "Export.Records"
        const val VERSION = "audora-audiobook-export 1"
        const val KEY = "key"
        const val COMPLETE = "complete"
        const val CHAPTER = "chapter"
        const val SEPARATOR = "\t"
        const val CHAPTER_FIELDS = 4
        const val LOCATION_FIELD = 2
        const val NAME_FIELD = 3
        const val RECORD_SUFFIX = ".txt"
        const val PARTIAL_SUFFIX = ".partial"
        const val HASH_HEX_CHARS = 16

        fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(HASH_HEX_CHARS)
    }
}
