// File: core/src/main/kotlin/io/github/skm0078/audora/core/guide/CharacterGuideStore.kt
// Purpose: Keeps each book's character guide as one small text file, replaced whole on save (plan item 80).

package io.github.skm0078.audora.core.guide

import io.github.skm0078.audora.core.files.TextFiles
import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * One file per book in [folder], named by a hash of the book id (so file names carry no title), with one line per
 * character: id, page, place on the page, name and note, tabs and line breaks escaped. Written to a synced temp file
 * and renamed. A file that cannot be read, or one from another format (a newer app's backup), reads as an empty guide
 * and is set aside first, never overwritten by the next save (logged: counts only, never names).
 */
class CharacterGuideStore(private val folder: File) {
    fun load(bookId: String): List<GuideEntry> {
        val file = fileOf(bookId)
        if (!file.isFile) return emptyList()
        val lines = try {
            file.readLines()
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Character guide unreadable: ${e.javaClass.simpleName}")
            null
        }
        val version = lines?.firstOrNull()?.let(HEADERS::get)
        return if (lines == null || version == null) {
            if (lines?.isEmpty() != true) AudoraLog.w(TAG, "Character guide in another format set aside")
            TextFiles.setAside(file)
            emptyList()
        } else {
            lines.drop(1).filter { it.isNotEmpty() }.mapNotNull { entryOf(it, version) }
        }
    }

    /**
     * False (logged) if it could not be written, or if the book's file is one [load] could not read and it would not
     * move aside. An empty guide deletes the book's file.
     */
    fun save(bookId: String, entries: List<GuideEntry>): Boolean {
        val file = fileOf(bookId)
        return try {
            if (isForeign(file) && !TextFiles.setAside(file)) throw IOException("an unreadable guide is in the way")
            if (entries.isEmpty()) {
                if (file.exists() && !file.delete()) throw IOException("not deleted")
            } else {
                val lines = entries.sortedBy { it.id }.map { entry ->
                    listOf(entry.id, entry.page, entry.charStart).joinToString(SEPARATOR) +
                        SEPARATOR + escape(entry.name) + SEPARATOR + escape(entry.note)
                }
                TextFiles.writeDurably(file, (listOf(HEADER) + lines).joinToString("\n", postfix = "\n"))
            }
            true
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Character guide not saved, ${entries.size} entries: ${e.javaClass.simpleName}")
            false
        }
    }

    private fun fileOf(bookId: String) = File(folder, hash(bookId) + SUFFIX)

    private fun isForeign(file: File): Boolean = file.isFile &&
        file.bufferedReader().use { it.readLine() }.let { it == null || it !in HEADERS }

    /** Version 1 lines lack the place on the page (read as the page's start). */
    private fun entryOf(line: String, version: Int): GuideEntry? {
        // Version 2 puts the place on the page after the page; version 1 has no such field.
        val parts = line.split(SEPARATOR).let { if (version == 1) it.take(2) + "0" + it.drop(2) else it }
        if (parts.size != FIELDS) return null
        val id = parts[ID_FIELD].toLongOrNull()
        val page = parts[PAGE_FIELD].toIntOrNull()?.takeIf { it >= 0 }
        val charStart = parts[CHAR_FIELD].toIntOrNull()?.takeIf { it >= 0 }
        return if (id != null && page != null && charStart != null) {
            GuideEntry(id, unescape(parts[NAME_FIELD]), unescape(parts[NOTE_FIELD]), page, charStart)
        } else {
            null
        }
    }

    companion object {
        /** The folder under `filesDir`; the backup lists it by this name. */
        const val FOLDER_NAME = "character-guide"
        private const val TAG = "Guide.Store"
        private const val HEADER = "audora-character-guide 2"
        private val HEADERS = mapOf("audora-character-guide 1" to 1, HEADER to 2)
        private const val SEPARATOR = "\t"
        private const val FIELDS = 5
        private const val ID_FIELD = 0
        private const val PAGE_FIELD = 1
        private const val CHAR_FIELD = 2
        private const val NAME_FIELD = 3
        private const val NOTE_FIELD = 4
        private const val SUFFIX = ".txt"
        private const val HASH_HEX_CHARS = 16

        private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(HASH_HEX_CHARS)

        internal fun escape(text: String): String =
            text.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "")

        internal fun unescape(text: String): String = buildString {
            var i = 0
            while (i < text.length) {
                val c = text[i]
                if (c == '\\' && i + 1 < text.length) {
                    append(
                        when (text[i + 1]) {
                            't' -> '\t'
                            'n' -> '\n'
                            else -> text[i + 1]
                        },
                    )
                    i += 2
                } else {
                    append(c)
                    i++
                }
            }
        }
    }
}
