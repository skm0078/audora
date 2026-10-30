// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/PhotoConfig.kt
// Purpose: Tunables for photo books: a camera photo, picked pictures, a comic archive or a scanned DjVu, kept as a
//          scanned book (ADR-043, ADR-066, ADR 2026-10-26-more-ebook-formats, ADR 2026-10-27-djvu-books).

package io.github.skm0078.audora.core.config

/**
 * @property maxLongSidePx the photo is scaled down to at most this many pixels on its long side: about 360 dpi on an
 *   A4 page, more than OCR's render resolution, and it keeps the book file near 1–2 MB.
 * @property jpegQuality the stored photo's JPEG quality (0–100); 90 keeps print edges sharp for OCR.
 * @property pageLongSidePt the page's long side in PDF points (842 = A4), so OCR draws it at its usual resolution.
 * @property captureFolder the cache folder the camera app writes the photo to, through a FileProvider.
 * @property maxImagesPerBook pictures picked or shared at once become one book of at most this many pages: about a
 *   chapter, 50–100 MB of book file, and minutes of OCR the first time it is read.
 * @property maxComicPages a comic archive (.cbz) of more pages is refused: a long graphic novel, a few hundred MB of
 *   book file, read by OCR page by page as it plays (ADR 2026-10-26-more-ebook-formats).
 * @property maxComicBytes a comic archive larger than this is not copied in (it is copied whole before reading).
 * @property maxComicPageBytes a comic page that unpacks to more than this is not read (it is read whole before it is
 *   decoded and scaled): a scanned double page is a few MB, and the cap keeps a page that unpacks to far more than the
 *   archive's size (a zip bomb) from running the phone out of memory. Such a page becomes a blank page.
 * @property maxDjvuScanPages a DjVu with no text layer of more pages is refused: each page is drawn and kept as a
 *   picture, so a 600-page scan is several hundred MB of book file (ADR 2026-10-27-djvu-books).
 */
data class PhotoConfig(
    val maxLongSidePx: Int = 3_000,
    val jpegQuality: Int = 90,
    val pageLongSidePt: Int = 842,
    val captureFolder: String = "photo-capture",
    val maxImagesPerBook: Int = 50,
    val maxComicPages: Int = 400,
    val maxComicBytes: Long = DEFAULT_MAX_COMIC_BYTES,
    val maxComicPageBytes: Int = DEFAULT_MAX_COMIC_PAGE_BYTES,
    val maxDjvuScanPages: Int = 600,
) {
    init {
        require(maxLongSidePx > 0) { "maxLongSidePx must be > 0, was $maxLongSidePx" }
        require(jpegQuality in 1..MAX_QUALITY) { "jpegQuality must be in 1..$MAX_QUALITY, was $jpegQuality" }
        require(pageLongSidePt > 0) { "pageLongSidePt must be > 0, was $pageLongSidePt" }
        require(maxImagesPerBook > 0) { "maxImagesPerBook must be > 0, was $maxImagesPerBook" }
        require(maxComicPages > 0) { "maxComicPages must be > 0, was $maxComicPages" }
        require(maxComicBytes > 0) { "maxComicBytes must be > 0, was $maxComicBytes" }
        require(maxComicPageBytes > 0) { "maxComicPageBytes must be > 0, was $maxComicPageBytes" }
        require(maxDjvuScanPages > 0) { "maxDjvuScanPages must be > 0, was $maxDjvuScanPages" }
    }

    private companion object {
        const val MAX_QUALITY = 100
        const val DEFAULT_MAX_COMIC_BYTES = 512L * 1024L * 1024L
        const val DEFAULT_MAX_COMIC_PAGE_BYTES = 64 * 1024 * 1024
    }
}
