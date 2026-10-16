// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/OcrConfig.kt
// Purpose: Tunables for OCR of scanned and mis-mapped pages with Tesseract (P3-05, ADR-025).

package io.github.skm0078.audora.core.config

/**
 * Build Spec §5 step 3c, ADR-011, ADR-025.
 *
 * @property renderDpi pages are rendered at this resolution for OCR. Tesseract reads body text best at about 300 dpi
 *   (S2 measured 2–4 % character error on 300 dpi Hindi, Bengali and English pages).
 * @property maxRenderPixels a page larger than this many pixels at [renderDpi] (a poster, a scanned A3 sheet) is
 *   rendered at a lower resolution instead, which bounds the bitmap at 4 bytes per pixel while it is converted to
 *   grey. 12 M pixels covers A4 and US Legal at 300 dpi.
 * @property probeDpi a scanned book with no text layer anywhere gives no hint of its language, so its first OCR page
 *   is read once at this low resolution with every installed language to find out which ones it uses; 100 dpi told
 *   Hindi, Bengali and English apart on every S2 scan.
 * @property cacheMaxBytes OCR text is kept on disk so a page is never recognised twice (the reader and the player
 *   both read it, and so does a resume); the oldest pages are dropped beyond this size. A page is a few kilobytes.
 */
data class OcrConfig(
    val renderDpi: Int = 300,
    val maxRenderPixels: Long = DEFAULT_MAX_RENDER_PIXELS,
    val probeDpi: Int = 100,
    val cacheMaxBytes: Long = DEFAULT_CACHE_MAX_BYTES,
) {
    init {
        require(renderDpi > 0) { "renderDpi must be > 0, was $renderDpi" }
        require(probeDpi in 1..renderDpi) { "probeDpi must be in 1..renderDpi, was $probeDpi" }
        require(maxRenderPixels > 0) { "maxRenderPixels must be > 0, was $maxRenderPixels" }
        require(cacheMaxBytes > 0) { "cacheMaxBytes must be > 0, was $cacheMaxBytes" }
    }

    companion object {
        const val DEFAULT_MAX_RENDER_PIXELS = 12_000_000L
        const val DEFAULT_CACHE_MAX_BYTES = 16L shl 20 // 16 MiB
    }
}
