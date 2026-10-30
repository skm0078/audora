// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/FirstUseHintsConfig.kt
// Purpose: When the first-use hints count a book as long and half read, and a library as visited often enough to
//          suggest adding more books (ADR 2026-10-30-first-use-hints).

package io.github.skm0078.audora.core.config

/**
 * @property longBookPages a book of at least this many pages is long enough that preparing the rest ahead saves real
 *   waiting; shorter books render faster than they are read.
 * @property halfReadFrom with [halfReadTo], the share of a book read (0..1) that counts as "half read": started in
 *   earnest, with enough left to be worth preparing.
 * @property libraryVisitsForMoreBooks the library has been opened this many times (one per app start) with one book
 *   in it before it suggests the other ways to add books: once is a first look, three is a habit.
 */
data class FirstUseHintsConfig(
    val longBookPages: Int = DEFAULT_LONG_BOOK_PAGES,
    val halfReadFrom: Float = DEFAULT_HALF_READ_FROM,
    val halfReadTo: Float = DEFAULT_HALF_READ_TO,
    val libraryVisitsForMoreBooks: Int = DEFAULT_LIBRARY_VISITS,
) {
    init {
        require(longBookPages >= 1) { "longBookPages must be >= 1, was $longBookPages" }
        require(halfReadFrom in 0f..1f && halfReadTo in halfReadFrom..1f) {
            "half read must be a range within 0..1, was $halfReadFrom..$halfReadTo"
        }
        require(libraryVisitsForMoreBooks >= 1) {
            "libraryVisitsForMoreBooks must be >= 1, was $libraryVisitsForMoreBooks"
        }
    }

    /** Whether a [pageCount]-page book read to [page] (zero-based) is long and half read. */
    fun isLongAndHalfRead(page: Int, pageCount: Int): Boolean =
        pageCount >= longBookPages && (page + 1).toFloat() / pageCount in halfReadFrom..halfReadTo

    companion object {
        const val DEFAULT_LONG_BOOK_PAGES = 100
        const val DEFAULT_HALF_READ_FROM = 0.25f
        const val DEFAULT_HALF_READ_TO = 0.75f
        const val DEFAULT_LIBRARY_VISITS = 3
    }
}
