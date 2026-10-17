// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/CoverConfig.kt
// Purpose: Tunables for library covers (plan item 21): the first page drawn small and cached as WEBP.

package io.github.skm0078.audora.core.config

/**
 * @property widthPx covers are drawn this wide: a row shows them about 48 dp wide, so this stays sharp up to 3×.
 * @property maxPixels caps a very tall page (a scroll or a receipt) so one cover never takes much memory.
 * @property webpQuality lossy WEBP quality, 0 to 100; covers are thumbnails, a few KB each on disk.
 * @property memoryCacheBytes decoded covers kept in memory, so scrolling back up does not decode them again.
 */
data class CoverConfig(
    val widthPx: Int = DEFAULT_WIDTH_PX,
    val maxPixels: Int = DEFAULT_WIDTH_PX * DEFAULT_MAX_HEIGHT_PX,
    val webpQuality: Int = DEFAULT_WEBP_QUALITY,
    val memoryCacheBytes: Int = DEFAULT_MEMORY_CACHE_BYTES,
) {
    init {
        require(widthPx > 0) { "widthPx must be > 0, was $widthPx" }
        require(maxPixels >= widthPx) { "maxPixels ($maxPixels) must allow one row of widthPx ($widthPx)" }
        require(webpQuality in 0..MAX_QUALITY) { "webpQuality must be in 0..$MAX_QUALITY, was $webpQuality" }
        require(memoryCacheBytes > 0) { "memoryCacheBytes must be > 0, was $memoryCacheBytes" }
    }

    private companion object {
        const val DEFAULT_WIDTH_PX = 144
        const val DEFAULT_MAX_HEIGHT_PX = 288
        const val DEFAULT_WEBP_QUALITY = 80
        const val DEFAULT_MEMORY_CACHE_BYTES = 8 shl 20 // 8 MiB, about 50 covers
        const val MAX_QUALITY = 100
    }
}
