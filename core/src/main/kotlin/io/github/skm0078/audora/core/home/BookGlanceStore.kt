// File: core/src/main/kotlin/io/github/skm0078/audora/core/home/BookGlanceStore.kt
// Purpose: Keeps the Continue card's glances (last line heard, time left) in one small file on the phone.

package io.github.skm0078.audora.core.home

import io.github.skm0078.audora.core.files.TextFiles
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.File
import java.io.IOException

/**
 * One line per book: its id, when it changed, the time left, the pages left then and the last line heard, tab
 * separated. Written to a synced temp file and renamed, so a crash never leaves half a file. It is only a convenience:
 * an unreadable or foreign file reads as no glances and is replaced on the next save. Not in the backup: the next
 * listen fills it again.
 */
class BookGlanceStore(private val file: File) {
    fun load(): Map<BookId, BookGlance> {
        val lines = try {
            if (file.isFile) file.readLines() else emptyList()
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Book glances unreadable, starting empty: ${e.javaClass.simpleName}")
            emptyList()
        }
        if (lines.firstOrNull() != HEADER) return emptyMap()
        return lines.drop(1).mapNotNull(::entryOf).toMap()
    }

    /** Replaces the file with [glances]; false (logged) if it could not be written. */
    fun save(glances: Map<BookId, BookGlance>): Boolean = try {
        val lines = glances.map { (id, glance) -> lineOf(id, glance) }
        TextFiles.writeDurably(file, (listOf(HEADER) + lines).joinToString("\n", postfix = "\n"))
        true
    } catch (e: IOException) {
        AudoraLog.w(TAG, "Book glances not saved: ${e.javaClass.simpleName}")
        false
    }

    private fun lineOf(id: BookId, glance: BookGlance): String = listOf(
        id.value,
        glance.savedAtMillis.toString(),
        glance.msLeft?.toString() ?: NONE,
        glance.pagesLeft?.toString() ?: NONE,
        glance.line?.let(::oneField) ?: NONE,
    ).joinToString(SEPARATOR)

    /** Null for a line that is not one of ours: a missing field, a blank id or a time that is not a number. */
    private fun entryOf(line: String): Pair<BookId, BookGlance>? {
        val parts = line.split(SEPARATOR, limit = FIELDS)
        val saved = parts.getOrNull(SAVED_AT)?.toLongOrNull()
        if (parts.size != FIELDS || parts[ID].isBlank() || saved == null) return null
        return BookId(parts[ID]) to BookGlance(
            line = parts[LINE].takeUnless { it == NONE || it.isBlank() },
            msLeft = parts[MS_LEFT].toLongOrNull()?.takeIf { it >= 0 },
            pagesLeft = parts[PAGES_LEFT].toIntOrNull()?.takeIf { it >= 0 },
            savedAtMillis = saved,
        )
    }

    /** A tab or a line break inside the sentence would split the record. */
    private fun oneField(text: String): String = text.replace(BREAKS, " ")

    companion object {
        /** Where the app keeps it, under `filesDir`. */
        const val FILE_NAME = "book-glances.txt"
        private const val TAG = "Audora.BookGlances"
        private const val HEADER = "audora-book-glances 1"
        private const val SEPARATOR = "\t"
        private const val NONE = "-"
        private const val FIELDS = 5
        private const val ID = 0
        private const val SAVED_AT = 1
        private const val MS_LEFT = 2
        private const val PAGES_LEFT = 3
        private const val LINE = 4
        private val BREAKS = Regex("[\\t\\r\\n]+")
    }
}
