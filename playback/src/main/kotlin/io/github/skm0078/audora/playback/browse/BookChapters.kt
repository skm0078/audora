// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/browse/BookChapters.kt
// Purpose: A book's chapters with their outline titles, for the browsable library (plan item 34).

package io.github.skm0078.audora.playback.browse

import io.github.skm0078.audora.pdf.document.OutlineEntry
import io.github.skm0078.audora.pdf.outline.ChapterStarts

/** [title] is the outline's, trimmed; blank when the entry had none (the tree then numbers it). */
data class BookChapter(val title: String, val page: Int)

object BookChapters {
    /**
     * The same chapter pages the end-of-chapter sleep timer uses ([ChapterStarts]), each titled by the first outline
     * entry, shallowest first, that starts on it.
     */
    fun from(outline: List<OutlineEntry>, pageCount: Int, maxDepth: Int): List<BookChapter> {
        val levels = generateSequence(outline) { level -> level.flatMap { it.children }.ifEmpty { null } }
            .take(maxDepth)
            .flatten()
            .toList()
        return ChapterStarts.from(outline, pageCount, maxDepth).map { page ->
            BookChapter(levels.firstOrNull { it.page == page }?.title?.trim().orEmpty(), page)
        }
    }
}
