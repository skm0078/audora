// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/outline/ChapterList.kt
// Purpose: The chapter list a listener jumps from (plan item 11): the PDF outline flattened in reading order.

package io.github.skm0078.audora.pdf.outline

import io.github.skm0078.audora.pdf.document.OutlineEntry

/**
 * One row of the chapter list. [title] is the PDF's own text: show it, never log it.
 *
 * @property depth 0 for a top-level entry, 1 for one nested under it, and so on.
 */
data class ChapterEntry(val title: String, val page: Int, val depth: Int) {
    override fun toString() = "ChapterEntry(page=$page, depth=$depth)"
}

object ChapterList {
    /**
     * Every outline entry down to [maxDepth] levels that points at a page of the book, in outline order (depth
     * first, as a table of contents reads). Entries without a page or out of range are left out, but their children
     * still count. Titles are trimmed; a blank one stays blank (the screen shows the page number instead).
     */
    fun from(outline: List<OutlineEntry>, pageCount: Int, maxDepth: Int): List<ChapterEntry> {
        val entries = ArrayList<ChapterEntry>()
        fun visit(level: List<OutlineEntry>, depth: Int) {
            if (depth >= maxDepth) return
            for (entry in level) {
                val page = entry.page
                if (page != null && page in 0 until pageCount) entries += ChapterEntry(entry.title.trim(), page, depth)
                visit(entry.children, depth + 1)
            }
        }
        visit(outline, 0)
        return entries
    }

    /**
     * The row [page] belongs to: the one starting latest on or before it; of rows starting on the same page, the last
     * in the list (a section beats the chapter around it). Null before the first row.
     */
    fun currentIndex(entries: List<ChapterEntry>, page: Int): Int? = entries.withIndex()
        .filter { it.value.page <= page }
        .maxWithOrNull(compareBy({ it.value.page }, { it.index }))
        ?.index
}
