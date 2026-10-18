// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/book/PageCursor.kt
// Purpose: Walks a book's pages from a start position, trimming the first page to start at the chosen sentence.

package io.github.skm0078.audora.playback.book

import io.github.skm0078.audora.core.model.ReadingUnit

/**
 * @param startChar offset into the start page's text: reading starts at the first sentence that ends after
 *   it, so an offset inside a sentence starts that sentence (Build Spec §6 re-anchoring by charStart). A unit with
 *   no text (a continued table's "Table continues" summary, sharing its first row's charStart) counts as one
 *   character long, so resuming at that row plays the summary first: what a saved resume wants.
 * @param startIndex the start unit's index on the start page, when the start is a known unit (a tap, a skip, a
 *   rebuild of the sentence playing): units before it are dropped too, so a start on a continued table's first row
 *   starts at that row, not at the summary sharing its offset. Null for a saved resume or a page start.
 */
class PageCursor(
    val pageCount: Int,
    val startPage: Int,
    private val startChar: Int,
    private val startIndex: Int? = null,
) {
    init {
        require(startPage in 0 until pageCount) { "startPage $startPage outside 0 until $pageCount" }
        require(startChar >= 0) { "startChar must be >= 0, was $startChar" }
    }

    private var next = startPage

    /** The next page to extract, or null after the last page. */
    fun nextPage(): Int? = next.takeIf { it < pageCount }?.also { next++ }

    /** The sentences of [page] that belong in the playlist. */
    fun playable(page: Int, units: List<ReadingUnit>): List<ReadingUnit> =
        if (page == startPage) units.filter { it.spokenEnd > startChar && it.index >= (startIndex ?: 0) } else units
}

/** Where [this] ends for playback's position checks: an empty unit (no text) still takes one character. */
val ReadingUnit.spokenEnd: Int get() = maxOf(charEnd, charStart + 1)
