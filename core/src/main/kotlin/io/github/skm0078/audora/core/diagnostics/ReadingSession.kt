// File: core/src/main/kotlin/io/github/skm0078/audora/core/diagnostics/ReadingSession.kt
// Purpose: The "audio is playing" marker that survives process death, so a stop nobody asked for is noticed.

package io.github.skm0078.audora.core.diagnostics

/**
 * Which book was playing, kept on disk while audio plays.
 *
 * Only identifiers: never the book's title or text, because the marker's fields go into the log.
 * The exact sentence to resume at is P1-06's `ResumeAnchor`; this only says that a resume is due.
 */
data class ReadingSession(val bookId: String, val page: Int, val startedAtMillis: Long) {
    init {
        require(bookId.isNotBlank()) { "bookId must not be blank" }
        require(page >= 0) { "page must be >= 0, was $page" }
    }
}
