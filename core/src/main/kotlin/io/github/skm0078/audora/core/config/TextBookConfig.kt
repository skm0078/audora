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
 * @property maxDjvuCopyBytes a DjVu whose provider gives no seekable file is copied in to be read, up to this size
 *   (ADR 2026-10-27-djvu-books); one read in place has no limit, as only its text layer is read.
 */
data class TextBookConfig(
    val pageChars: Int = 2_500,
    val headingMaxChars: Int = 100,
    val maxBookBytes: Long = DEFAULT_MAX_BOOK_BYTES,
    val maxSharedChars: Int = 2_000_000,
    val maxDjvuCopyBytes: Long = DEFAULT_MAX_DJVU_COPY_BYTES,
) {
    init {
        require(pageChars > 0) { "pageChars must be > 0, was $pageChars" }
        require(headingMaxChars > 0) { "headingMaxChars must be > 0, was $headingMaxChars" }
        require(maxBookBytes > 0) { "maxBookBytes must be > 0, was $maxBookBytes" }
        require(maxSharedChars > 0) { "maxSharedChars must be > 0, was $maxSharedChars" }
        require(maxDjvuCopyBytes > 0) { "maxDjvuCopyBytes must be > 0, was $maxDjvuCopyBytes" }
    }

    private companion object {
        const val DEFAULT_MAX_BOOK_BYTES = 64L * 1024L * 1024L
        const val DEFAULT_MAX_DJVU_COPY_BYTES = 512L * 1024L * 1024L
    }
}
