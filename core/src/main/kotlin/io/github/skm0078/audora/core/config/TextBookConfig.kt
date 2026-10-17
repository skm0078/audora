// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/TextBookConfig.kt
// Purpose: Tunables for EPUB, plain-text and shared-text books (plan items 42 and 43, ADR-042).

package io.github.skm0078.audora.core.config

/**
 * @property pageChars a text book is cut into pages of about this many characters (at paragraph ends), so the
 *   reader, resume, bookmarks, search and the studio work on it as on a PDF. About one printed page.
 * @property headingMaxChars a plain-text paragraph of one line, no longer than this and without closing punctuation,
 *   is read as a heading.
 * @property maxBookBytes a text file, or an EPUB's text (its XHTML, package and contents files), may not be larger
 *   than this; images and fonts in an EPUB are never read.
 * @property maxSharedChars shared text longer than this is cut (a share is a passage or an article, not a book).
 */
data class TextBookConfig(
    val pageChars: Int = 2_500,
    val headingMaxChars: Int = 100,
    val maxBookBytes: Long = DEFAULT_MAX_BOOK_BYTES,
    val maxSharedChars: Int = 2_000_000,
) {
    init {
        require(pageChars > 0) { "pageChars must be > 0, was $pageChars" }
        require(headingMaxChars > 0) { "headingMaxChars must be > 0, was $headingMaxChars" }
        require(maxBookBytes > 0) { "maxBookBytes must be > 0, was $maxBookBytes" }
        require(maxSharedChars > 0) { "maxSharedChars must be > 0, was $maxSharedChars" }
    }

    private companion object {
        const val DEFAULT_MAX_BOOK_BYTES = 64L * 1024L * 1024L
    }
}
