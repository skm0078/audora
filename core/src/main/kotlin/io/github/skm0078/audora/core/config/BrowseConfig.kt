// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/BrowseConfig.kt
// Purpose: Tunables for the library Android Auto and other media browsers see (plan item 34).

package io.github.skm0078.audora.core.config

/**
 * @property recentCount books in the "Recent" tab, newest first; a driver picks from the top few, and a short list
 *   keeps the car's screen readable.
 * @property chapterCacheSize books whose chapter lists are kept after their PDF was opened to read the outline.
 */
data class BrowseConfig(val recentCount: Int = 10, val chapterCacheSize: Int = 8) {
    init {
        require(recentCount > 0) { "recentCount must be > 0, was $recentCount" }
        require(chapterCacheSize > 0) { "chapterCacheSize must be > 0, was $chapterCacheSize" }
    }
}
