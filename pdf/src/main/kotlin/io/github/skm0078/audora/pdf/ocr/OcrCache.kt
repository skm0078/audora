// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/ocr/OcrCache.kt
// Purpose: Keeps OCR text on disk so a page is recognised once, however often the reader, player or resume read it.

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.Lang
import java.io.File
import java.io.IOException

/**
 * What a cached text depends on: the book, the page, the languages, the resolution and the engine with its models
 * ([tag]). A new model pin or engine version changes [tag], so stale text is never read back.
 */
data class OcrKey(val book: String, val page: Int, val langs: Set<Lang>, val dpi: Int, val tag: String) {
    /** A file name of letters, digits, '-' and '.': book keys and tags are hashes or plain words. */
    internal fun fileName(): String {
        val codes = langs.map { it.bcp47Tag }.sorted().joinToString("+")
        return "${safe(book)}-p$page-$codes-${dpi}dpi-${safe(tag)}$SUFFIX"
    }

    internal companion object {
        const val SUFFIX = ".txt"
        private val UNSAFE = Regex("[^A-Za-z0-9.]")

        fun safe(part: String): String = part.replace(UNSAFE, "_")
    }
}

/**
 * One UTF-8 file per page under [dir] (the app's cache folder, which Android may clear: a cleared page is simply
 * recognised again). Written through a temporary file and a rename, so a kill never leaves half a page. Reading a
 * page refreshes its date; once the folder passes [maxBytes] the least recently used pages are deleted.
 *
 * Holds the user's text, so it stays in app-private storage and is never logged. Thread-safe for different keys;
 * the caller serializes work on the same key (`RenderedPageOcr`).
 */
class OcrCache(private val dir: File, private val maxBytes: Long) {
    fun get(key: OcrKey): String? {
        val file = File(dir, key.fileName())
        if (!file.isFile) return null
        return try {
            file.readText(Charsets.UTF_8).also { file.setLastModified(System.currentTimeMillis()) }
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Cached OCR page unreadable, page=${key.page}; recognising it again", e)
            null
        }
    }

    fun put(key: OcrKey, text: String) {
        val file = File(dir, key.fileName())
        val temp = File(dir, file.name + TEMP_SUFFIX)
        try {
            dir.mkdirs()
            temp.writeText(text, Charsets.UTF_8)
            if (!temp.renameTo(file)) throw IOException("cannot rename ${temp.name}")
        } catch (e: IOException) {
            temp.delete()
            AudoraLog.w(TAG, "Could not cache OCR page=${key.page}; it will be recognised again next time", e)
            return
        }
        trim()
    }

    /** Deletes the least recently used pages until the folder fits [maxBytes] again. */
    private fun trim() {
        val files = dir.listFiles { file -> file.isFile && file.name.endsWith(OcrKey.SUFFIX) }.orEmpty()
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= maxBytes) break
            val size = file.length()
            if (file.delete()) total -= size
        }
    }

    private companion object {
        const val TAG = "Audora.OcrCache"
        const val TEMP_SUFFIX = ".tmp"
    }
}
