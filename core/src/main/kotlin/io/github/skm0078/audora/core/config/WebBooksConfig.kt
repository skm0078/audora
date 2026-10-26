// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/WebBooksConfig.kt
// Purpose: Tunables for books from the web: a page read from its link (CF-12) and OPDS catalogues (CF-15).

package io.github.skm0078.audora.core.config

/**
 * Everything here is fetched only when the listener taps (ADR 2026-10-26-web-books): nothing runs in the background.
 *
 * @property connectTimeoutMs a server that does not answer within this long fails with "could not reach".
 * @property readTimeoutMs a download that stalls this long fails instead of hanging.
 * @property maxPageBytes a web page or catalogue page larger than this is refused (a page is text, not a book).
 * @property maxBookBytes a book downloaded from a catalogue larger than this is refused, so a wrong link never fills
 *   the phone; far above any public-domain EPUB.
 * @property minArticleChars a page whose article text is shorter than this has nothing worth reading (a home page,
 *   a log-in wall, a list of links) and is refused with its own message.
 * @property maxEntriesPerPage a catalogue page lists at most this many books; the rest are behind "More".
 * @property maxCatalogues the listener can add at most this many catalogues of their own.
 */
data class WebBooksConfig(
    val connectTimeoutMs: Int = 15_000,
    val readTimeoutMs: Int = 30_000,
    val maxPageBytes: Long = DEFAULT_MAX_PAGE_BYTES,
    val maxBookBytes: Long = DEFAULT_MAX_BOOK_BYTES,
    val minArticleChars: Int = 200,
    val maxEntriesPerPage: Int = 500,
    val maxCatalogues: Int = 20,
) {
    init {
        require(connectTimeoutMs > 0) { "connectTimeoutMs must be > 0, was $connectTimeoutMs" }
        require(readTimeoutMs > 0) { "readTimeoutMs must be > 0, was $readTimeoutMs" }
        require(maxPageBytes > 0) { "maxPageBytes must be > 0, was $maxPageBytes" }
        require(maxBookBytes > 0) { "maxBookBytes must be > 0, was $maxBookBytes" }
        require(minArticleChars > 0) { "minArticleChars must be > 0, was $minArticleChars" }
        require(maxEntriesPerPage > 0) { "maxEntriesPerPage must be > 0, was $maxEntriesPerPage" }
        require(maxCatalogues > 0) { "maxCatalogues must be > 0, was $maxCatalogues" }
    }

    private companion object {
        const val MIB = 1024L * 1024L
        const val DEFAULT_MAX_PAGE_BYTES = 8L * MIB
        const val DEFAULT_MAX_BOOK_BYTES = 200L * MIB
    }
}
