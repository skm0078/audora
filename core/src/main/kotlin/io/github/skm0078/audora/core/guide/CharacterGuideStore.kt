// File: core/src/main/kotlin/io/github/skm0078/audora/core/guide/CharacterGuideStore.kt
// Purpose: Keeps each book's character guide as one small text file, replaced whole on save (plan item 80).

package io.github.skm0078.audora.core.guide

import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * One file per book in [folder], named by a hash of the book id (so file names carry no title), with one line per
 * character: id, page, name and note, tabs and line breaks escaped. Written to a temp file and renamed; an
 * unreadable or foreign file reads as an empty guide and is logged (counts only, never names).
 */
class CharacterGuideStore(private val folder: File) {
    fun load(bookId: String): List<GuideEntry> {
        val file = fileOf(bookId)
        val lines = try {
            if (file.isFile) file.readLines() else emptyList()
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Character guide unreadable: ${e.javaClass.simpleName}")
            emptyList()
        }
        return when {
            lines.isEmpty() -> emptyList()

            lines.first() != HEADER -> {
                AudoraLog.w(TAG, "Character guide from another format ignored")
                emptyList()
            }

            else -> lines.drop(1).filter { it.isNotEmpty() }.mapNotNull(::entryOf)
        }
    }

    /** False (logged) if it could not be written. An empty guide deletes the book's file. */
    fun save(bookId: String, entries: List<GuideEntry>): Boolean {
        val file = fileOf(bookId)
        val temp = File(file.path + TEMP)
        return try {
            if (entries.isEmpty()) {
                if (file.exists() && !file.delete()) throw IOException("not deleted")
            } else {
                folder.mkdirs()
                val lines = entries.sortedBy { it.id }.map { entry ->
                    listOf(entry.id.toString(), entry.page.toString(), escape(entry.name), escape(entry.note))
                        .joinToString(SEPARATOR)
                }
                temp.writeText((listOf(HEADER) + lines).joinToString("\n", postfix = "\n"))
                if (!temp.renameTo(file)) throw IOException("rename failed")
            }
            true
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Character guide not saved, ${entries.size} entries: ${e.javaClass.simpleName}")
            temp.delete()
            false
        }
    }

    private fun fileOf(bookId: String) = File(folder, hash(bookId) + SUFFIX)

    private fun entryOf(line: String): GuideEntry? {
        val parts = line.split(SEPARATOR).takeIf { it.size == FIELDS } ?: return null
        val id = parts[ID_FIELD].toLongOrNull()
        val page = parts[PAGE_FIELD].toIntOrNull()?.takeIf { it >= 0 }
        return if (id != null && page != null) {
            GuideEntry(id, unescape(parts[NAME_FIELD]), unescape(parts[NOTE_FIELD]), page)
        } else {
            null
        }
    }

    companion object {
        /** The folder under `filesDir`; the backup lists it by this name. */
        const val FOLDER_NAME = "character-guide"
        private const val TAG = "Guide.Store"
        private const val HEADER = "audora-character-guide 1"
        private const val SEPARATOR = "\t"
        private const val FIELDS = 4
        private const val ID_FIELD = 0
        private const val PAGE_FIELD = 1
        private const val NAME_FIELD = 2
        private const val NOTE_FIELD = 3
        private const val SUFFIX = ".txt"
        private const val TEMP = ".tmp"
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
