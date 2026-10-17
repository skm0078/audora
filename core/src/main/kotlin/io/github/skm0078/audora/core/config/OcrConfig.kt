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
 * @property orientationCheckBelow a page read with a mean word confidence (0–100) below this may be sideways or
 *   upside down: it is read again turned each way at [orientationDpi] and the best turn wins (ADR-032). Upright
 *   print reads at 85–95; a sideways page reads as noise, well below 50.
 * @property orientationDpi the shrunk copy the orientation check reads four times; enough to tell text from noise.
 * @property orientationMinGain a turn wins only when it reads at least this much more confidently than the page as
 *   it is, so a merely poor scan is never turned.
 * @property marginDpi the strips at the top and bottom of neighbouring pages, read to find running headers on
 *   scanned pages (ADR-032), are drawn at this resolution: enough for 8 pt header text, cheaper than [renderDpi].
 * @property retryLineBelow a line read with a mean confidence (0–100) below this is read again on its own with each
 *   other installed model (RI-3): an English line in a Hindi book reads as noise with the Hindi model alone. 0 turns
 *   the retry off. Clean print reads at 85–95.
 * @property retryMinGain a retry replaces the line only when it reads at least this much more confidently.
 * @property retryMaxLines at most this many lines of a page are retried, the least confident first.
 * @property retryBudgetMs retries stop once a page has spent this long on them (done-when: at most 3 s extra a page).
 */
data class OcrConfig(
    val renderDpi: Int = 300,
    val maxRenderPixels: Long = DEFAULT_MAX_RENDER_PIXELS,
    val probeDpi: Int = 100,
    val cacheMaxBytes: Long = DEFAULT_CACHE_MAX_BYTES,
    val orientationCheckBelow: Int = 65,
    val orientationDpi: Int = 150,
    val orientationMinGain: Int = 10,
    val marginDpi: Int = 200,
    val retryLineBelow: Int = 60,
    val retryMinGain: Int = 10,
    val retryMaxLines: Int = 8,
    val retryBudgetMs: Long = 2_000L,
) {
    init {
        require(renderDpi > 0) { "renderDpi must be > 0, was $renderDpi" }
        require(probeDpi in 1..renderDpi) { "probeDpi must be in 1..renderDpi, was $probeDpi" }
        require(maxRenderPixels > 0) { "maxRenderPixels must be > 0, was $maxRenderPixels" }
        require(cacheMaxBytes > 0) { "cacheMaxBytes must be > 0, was $cacheMaxBytes" }
        require(orientationCheckBelow in 0..MAX_CONFIDENCE) { "orientationCheckBelow must be in 0..100" }
        require(orientationDpi in 1..renderDpi && marginDpi in 1..renderDpi) {
            "probe resolutions must be in 1..renderDpi"
        }
        require(orientationMinGain >= 0) { "orientationMinGain must be >= 0, was $orientationMinGain" }
        require(retryLineBelow in 0..MAX_CONFIDENCE) { "retryLineBelow must be in 0..100, was $retryLineBelow" }
        require(retryMinGain >= 0 && retryMaxLines >= 0 && retryBudgetMs >= 0) { "retry limits must be >= 0" }
    }

    companion object {
        const val DEFAULT_MAX_RENDER_PIXELS = 12_000_000L
        const val DEFAULT_CACHE_MAX_BYTES = 16L shl 20 // 16 MiB
        const val MAX_CONFIDENCE = 100
    }
}
