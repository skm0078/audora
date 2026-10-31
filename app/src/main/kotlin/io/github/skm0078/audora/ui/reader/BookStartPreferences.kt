// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/BookStartPreferences.kt
// Purpose: Keeps the "Start new books at the first chapter" switch and each book's found start on the phone.

package io.github.skm0078.audora.ui.reader

import android.content.Context
import androidx.core.content.edit
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.pdf.start.FrontMatterKind

/**
 * SharedPreferences: a switch and one short line per book ("14|CONTENTS,PREFACE|Chapter 1"), read once when a book
 * opens. The chapter title stays on the phone like a bookmark's text; it is never logged. A line that cannot be read
 * (an older shape) counts as never searched, so the book is searched again.
 */
class BookStartPreferences(context: Context) : BookStartStore {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun skipFrontMatter(): Boolean = prefs.getBoolean(KEY_ON, true)

    override fun setSkipFrontMatter(on: Boolean) = prefs.edit { putBoolean(KEY_ON, on) }

    override fun saved(id: BookId): SkippedFrontMatter? = prefs.getString(key(id), null)?.let(::decode)

    override fun save(id: BookId, start: SkippedFrontMatter) = prefs.edit { putString(key(id), encode(start)) }

    private fun key(id: BookId) = "$KEY_BOOK${id.value}"

    internal companion object {
        const val FILE = "book_start"
        const val KEY_ON = "skip_front_matter"
        const val KEY_BOOK = "start_"
        private const val FIELDS = 3

        fun encode(start: SkippedFrontMatter): String =
            listOf(start.page.toString(), start.skipped.joinToString(",") { it.name }, start.title.orEmpty())
                .joinToString("|")

        fun decode(line: String): SkippedFrontMatter? {
            val parts = line.split('|', limit = FIELDS)
            val page = parts.firstOrNull()?.toIntOrNull()?.takeIf { it >= 0 }
            if (page == null || parts.size != FIELDS) return null
            val kinds = parts[1].split(',').mapNotNullTo(LinkedHashSet()) { name ->
                FrontMatterKind.entries.firstOrNull { it.name == name }
            }
            return SkippedFrontMatter(page, parts[2].ifEmpty { null }, kinds)
        }
    }
}
