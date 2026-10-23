// File: core/src/main/kotlin/io/github/skm0078/audora/core/library/LibraryQuery.kt
// Purpose: Library search, sort and filter (plan item 22), pure so the library screen and tests share it.

package io.github.skm0078.audora.core.library

import java.text.Normalizer
import java.util.Locale

enum class LibrarySort {
    /** Most recently opened or added first: the library's own order. */
    RECENT,
    TITLE,

    /** Newest added first. */
    ADDED,

    /** Furthest read first; books never read last. */
    PROGRESS,
}

enum class LibraryFilter { ALL, IN_PROGRESS, NOT_STARTED, FINISHED }

/**
 * @property text matched against titles: every word must appear, ignoring case, width and accents on Latin letters
 *   (Devanagari and Bengali signs are kept, since they change the word).
 */
data class LibraryQuery(
    val text: String = "",
    val sort: LibrarySort = LibrarySort.RECENT,
    val filter: LibraryFilter = LibraryFilter.ALL,
) {
    val isDefault: Boolean get() = text.isBlank() && sort == LibrarySort.RECENT && filter == LibraryFilter.ALL

    /** [items] (in the library's own recent-first order) searched, filtered and sorted; the sort is stable. */
    fun <T> apply(items: List<T>, book: (T) -> Book, progress: (T) -> BookProgress?): List<T> {
        val words = folded(text).split(WHITESPACE).filter { it.isNotEmpty() }
        val kept = items.filter { item ->
            val title = folded(book(item).title)
            words.all { it in title } && matches(progress(item))
        }
        return when (sort) {
            LibrarySort.RECENT -> kept
            LibrarySort.TITLE -> kept.sortedBy { folded(book(it).title) }
            LibrarySort.ADDED -> kept.sortedByDescending { book(it).addedAtMillis }
            LibrarySort.PROGRESS -> kept.sortedByDescending { progress(it)?.fraction ?: -1f }
        }
    }

    private fun matches(progress: BookProgress?): Boolean = when (filter) {
        LibraryFilter.ALL -> true
        LibraryFilter.NOT_STARTED -> progress == null
        LibraryFilter.FINISHED -> progress != null && progress.page == progress.pageCount - 1
        LibraryFilter.IN_PROGRESS -> progress != null && progress.page < progress.pageCount - 1
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val LATIN_MARKS = Regex("(?<=\\p{IsLatin})\\p{M}+")

        fun folded(text: String): String {
            val decomposed = Normalizer.normalize(text, Normalizer.Form.NFKD)
            return Normalizer.normalize(LATIN_MARKS.replace(decomposed, ""), Normalizer.Form.NFC).lowercase(Locale.ROOT)
        }
    }
}
