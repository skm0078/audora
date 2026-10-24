// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/PhotoConfig.kt
// Purpose: Tunables for photo books: a camera photo, or picked pictures, kept as a scanned book (ADR-043, ADR-066).

package io.github.skm0078.audora.core.config

/**
 * @property maxLongSidePx the photo is scaled down to at most this many pixels on its long side: about 360 dpi on an
 *   A4 page, more than OCR's render resolution, and it keeps the book file near 1–2 MB.
 * @property jpegQuality the stored photo's JPEG quality (0–100); 90 keeps print edges sharp for OCR.
 * @property pageLongSidePt the page's long side in PDF points (842 = A4), so OCR draws it at its usual resolution.
 * @property captureFolder the cache folder the camera app writes the photo to, through a FileProvider.
 * @property maxImagesPerBook pictures picked or shared at once become one book of at most this many pages: about a
 *   chapter, 50–100 MB of book file, and minutes of OCR the first time it is read.
 */
data class PhotoConfig(
    val maxLongSidePx: Int = 3_000,
    val jpegQuality: Int = 90,
    val pageLongSidePt: Int = 842,
    val captureFolder: String = "photo-capture",
    val maxImagesPerBook: Int = 50,
) {
    init {
        require(maxLongSidePx > 0) { "maxLongSidePx must be > 0, was $maxLongSidePx" }
        require(jpegQuality in 1..MAX_QUALITY) { "jpegQuality must be in 1..$MAX_QUALITY, was $jpegQuality" }
        require(pageLongSidePt > 0) { "pageLongSidePt must be > 0, was $pageLongSidePt" }
        require(maxImagesPerBook > 0) { "maxImagesPerBook must be > 0, was $maxImagesPerBook" }
    }

    private companion object {
        const val MAX_QUALITY = 100
    }
}
