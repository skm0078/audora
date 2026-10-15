// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/book/PageCursor.kt
// Purpose: Walks a book's pages from a start position, trimming the first page to start at the chosen sentence.

package io.github.skm0078.audora.playback.book

import io.github.skm0078.audora.core.model.ReadingUnit

/**
 * @param startChar offset into the start page's text: reading starts at the first sentence that ends after
 *   it, so an offset inside a sentence starts that sentence (Build Spec §6 re-anchoring by charStart).
 */
class PageCursor(val pageCount: Int, val startPage: Int, private val startChar: Int) {
    init {
        require(startPage in 0 until pageCount) { "startPage $startPage outside 0 until $pageCount" }
        require(startChar >= 0) { "startChar must be >= 0, was $startChar" }
    }

    private var next = startPage

    /** The next page to extract, or null after the last page. */
    fun nextPage(): Int? = next.takeIf { it < pageCount }?.also { next++ }

    /** The sentences of [page] that belong in the playlist. */
    fun playable(page: Int, units: List<ReadingUnit>): List<ReadingUnit> =
        if (page == startPage) units.filter { it.charEnd > startChar } else units
}
