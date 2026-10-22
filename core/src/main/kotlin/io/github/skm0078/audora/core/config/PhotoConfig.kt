// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/PhotoConfig.kt
// Purpose: Tunables for "Point and read": a camera photo kept as a one-page scanned book (plan item 62, ADR-043).

package io.github.skm0078.audora.core.config

/**
 * @property maxLongSidePx the photo is scaled down to at most this many pixels on its long side: about 360 dpi on an
 *   A4 page, more than OCR's render resolution, and it keeps the book file near 1–2 MB.
 * @property jpegQuality the stored photo's JPEG quality (0–100); 90 keeps print edges sharp for OCR.
 * @property pageLongSidePt the page's long side in PDF points (842 = A4), so OCR draws it at its usual resolution.
 * @property captureFolder the cache folder the camera app writes the photo to, through a FileProvider.
 */
data class PhotoConfig(
    val maxLongSidePx: Int = 3_000,
    val jpegQuality: Int = 90,
    val pageLongSidePt: Int = 842,
    val captureFolder: String = "photo-capture",
) {
    init {
        require(maxLongSidePx > 0) { "maxLongSidePx must be > 0, was $maxLongSidePx" }
        require(jpegQuality in 1..MAX_QUALITY) { "jpegQuality must be in 1..$MAX_QUALITY, was $jpegQuality" }
        require(pageLongSidePt > 0) { "pageLongSidePt must be > 0, was $pageLongSidePt" }
    }

    private companion object {
        const val MAX_QUALITY = 100
    }
}
