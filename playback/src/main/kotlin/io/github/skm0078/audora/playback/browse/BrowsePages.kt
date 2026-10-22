// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/browse/BrowsePages.kt
// Purpose: One page of a browse folder or search result, as Media3 asks for them (page number and size).

package io.github.skm0078.audora.playback.browse

object BrowsePages {
    /**
     * Page [page] (zero-based) of [entries], [pageSize] rows each. The start is computed as a Long, so a car asking
     * for a huge page number gets an empty page instead of an Int overflow wrapping back to the first rows.
     */
    fun <T> of(entries: List<T>, page: Int, pageSize: Int): List<T> {
        val size = pageSize.coerceAtLeast(1)
        val start = page.coerceAtLeast(0).toLong() * size
        if (start >= entries.size) return emptyList()
        return entries.subList(start.toInt(), minOf(entries.size.toLong(), start + size).toInt()).toList()
    }
}
