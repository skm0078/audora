// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/CoverConfig.kt
// Purpose: Tunables for library covers (plan item 21): the first page drawn small and cached as WEBP.

package io.github.skm0078.audora.core.config

/**
 * @property widthPx covers are drawn this wide: a row shows them about 48 dp wide, so this stays sharp up to 3×.
 * @property maxPixels caps a very tall page (a scroll or a receipt) so one cover never takes much memory.
 * @property webpQuality lossy WEBP quality, 0 to 100; covers are thumbnails, a few KB each on disk.
 * @property memoryCacheBytes decoded covers kept in memory, so scrolling back up does not decode them again.
 * @property lowMemory covers decode as 16-bit colour (half the memory; a cover is opaque paper, so nothing is lost
 *   but a little colour depth). [forDevice] sets it on phones Android marks as low on memory.
 */
data class CoverConfig(
    val widthPx: Int = DEFAULT_WIDTH_PX,
    val maxPixels: Int = DEFAULT_WIDTH_PX * DEFAULT_MAX_HEIGHT_PX,
    val webpQuality: Int = DEFAULT_WEBP_QUALITY,
    val memoryCacheBytes: Int = DEFAULT_MEMORY_CACHE_BYTES,
    val lowMemory: Boolean = false,
) {
    init {
        require(widthPx > 0) { "widthPx must be > 0, was $widthPx" }
        require(maxPixels >= widthPx) { "maxPixels ($maxPixels) must allow one row of widthPx ($widthPx)" }
        require(webpQuality in 0..MAX_QUALITY) { "webpQuality must be in 0..$MAX_QUALITY, was $webpQuality" }
        require(memoryCacheBytes > 0) { "memoryCacheBytes must be > 0, was $memoryCacheBytes" }
    }

    companion object {
        /**
         * Sized for the phone (ADR-069): the memory cache takes a sixteenth of what Android gives the app
         * ([memoryClassMb], ActivityManager.getMemoryClass), between 4 and 16 MiB, so a 2 GB phone keeps fewer covers
         * decoded and a large one scrolls a big library without decoding again.
         */
        fun forDevice(memoryClassMb: Int, lowRam: Boolean): CoverConfig = CoverConfig(
            memoryCacheBytes = (memoryClassMb.toLong() * BYTES_PER_MB / HEAP_SHARE)
                .coerceIn(MIN_CACHE_BYTES, MAX_CACHE_BYTES).toInt(),
            lowMemory = lowRam,
        )

        private const val BYTES_PER_MB = 1L shl 20
        private const val HEAP_SHARE = 16
        private const val MIN_CACHE_BYTES = 4L shl 20
        private const val MAX_CACHE_BYTES = 16L shl 20
        private const val DEFAULT_WIDTH_PX = 144
        private const val DEFAULT_MAX_HEIGHT_PX = 288
        private const val DEFAULT_WEBP_QUALITY = 80
        private const val DEFAULT_MEMORY_CACHE_BYTES = 8 shl 20 // 8 MiB, about 50 covers
        private const val MAX_QUALITY = 100
    }
}
