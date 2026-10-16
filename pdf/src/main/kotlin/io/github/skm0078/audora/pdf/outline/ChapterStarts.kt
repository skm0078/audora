// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/outline/ChapterStarts.kt
// Purpose: Picks the pages where chapters start from a PDF outline, for the end-of-chapter sleep timer.

package io.github.skm0078.audora.pdf.outline

import io.github.skm0078.audora.pdf.document.OutlineEntry

/**
 * Chapters are the shallowest outline level that splits the book into at least two parts. Many books wrap
 * everything in one top-level entry (the book's title, or "Contents"), so a level with a single start page
 * is skipped in favour of the one below it; a book whose outline never splits keeps whatever start pages it
 * has, so the timer still pauses before that one chapter.
 */
object ChapterStarts {
    /** Sorted, distinct, zero-based pages inside `0 until pageCount`; empty when the outline has none. */
    fun from(outline: List<OutlineEntry>, pageCount: Int, maxDepth: Int): List<Int> {
        val levels = generateSequence(outline) { level -> level.flatMap { it.children }.ifEmpty { null } }
            .take(maxDepth)
            .map { level -> pagesOf(level, pageCount) }
            .toList()
        return levels.firstOrNull { it.size >= MIN_CHAPTERS }
            ?: levels.flatten().distinct().sorted()
    }

    private fun pagesOf(level: List<OutlineEntry>, pageCount: Int): List<Int> =
        level.mapNotNull { it.page }.filter { it in 0 until pageCount }.distinct().sorted()

    private const val MIN_CHAPTERS = 2
}
