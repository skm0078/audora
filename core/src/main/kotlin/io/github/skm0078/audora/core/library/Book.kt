// File: core/src/main/kotlin/io/github/skm0078/audora/core/library/Book.kt
// Purpose: A book in the library: where its file is, how to recognize it again, when it was last opened, and what
//          it is (title, author, language, format, cover).

package io.github.skm0078.audora.core.library

/** Stable for the book's life in the library; keys resume anchors and the reading-session marker. */
@JvmInline
value class BookId(val value: String) {
    init {
        require(value.isNotBlank()) { "BookId must not be blank" }
    }

    /** Short form for log lines: enough to tell books apart, never the title. */
    val logLabel: String get() = value.take(LOG_LABEL_LENGTH)

    override fun toString(): String = value

    private companion object {
        const val LOG_LABEL_LENGTH = 8
    }
}

/**
 * @property documentUri the Storage Access Framework URI Audora holds a persisted read grant for.
 * @property fingerprint identifies the content (size + hash of the first bytes), so the same PDF picked again
 *   from a new place is recognized as this book and keeps its reading position.
 * @property pageCount null until the book has been opened once (an encrypted book needs its password first).
 * @property author, [language] (a BCP 47 primary tag such as "en") and [format]: filled in from the file itself
 *   ([BookDetails]) or by the listener; null while unknown (ADR-068).
 * @property cover which picture the library shows for the book.
 * @property edited the fields the listener set; a later fill from the file never changes them.
 * @property detailsVersion the [BookDetails.VERSION] that last filled this book in; 0 for a book never filled in.
 */
data class Book(
    val id: BookId,
    val documentUri: String,
    val title: String,
    val sizeBytes: Long,
    val fingerprint: String,
    val pageCount: Int?,
    val addedAtMillis: Long,
    val lastOpenedAtMillis: Long?,
    val author: String? = null,
    val language: String? = null,
    val format: DocumentFormat? = null,
    val cover: CoverChoice = CoverChoice.FirstPage,
    val edited: Set<BookField> = emptySet(),
    val detailsVersion: Int = 0,
) {
    /** The library lists books by this, newest first. */
    val recentAtMillis: Long get() = lastOpenedAtMillis ?: addedAtMillis
}

/** Whether a book's file can be read right now. Checked when the library is shown, never stored. */
enum class BookAvailability {
    AVAILABLE,

    /** The file was moved, renamed or deleted, or its storage (SD card, cloud app) is gone. */
    MISSING,

    /** Audora no longer holds a read grant for the file, e.g. after the providing app was reinstalled. */
    ACCESS_LOST,
}
