// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/text/BookContents.kt
// Purpose: A text book's table of contents: its own when it has a real one, else the chapters found in its text.

package io.github.skm0078.audora.playback.text

import io.github.skm0078.audora.core.config.TextBookConfig

internal object BookContents {
    /** Where a row points, first to last in the book; rows at the same place keep their order. */
    val READING_ORDER: Comparator<ContentsEntry> = compareBy({ it.chapter }, { it.block }, { it.char })

    /**
     * The document's own contents (an EPUB's), else its titled chapters. When those have fewer than
     * `sparseContentsRows` rows, the chapters [ChapterMarkers] finds in the text are added, if there are at least as
     * many of them: one "Notice" row is not a contents, and two stray "Chapter 3" lines are not chapters either.
     */
    fun of(document: TextDocument, config: TextBookConfig): List<ContentsEntry> {
        val own = document.contents ?: document.chapters.mapIndexedNotNull { index, chapter ->
            chapter.title?.let { ContentsEntry(it, index) }
        }
        val found = if (own.size < config.sparseContentsRows) ChapterMarkers.find(document, config) else emptyList()
        return if (found.size < config.sparseContentsRows) {
            own
        } else {
            (own + found).sortedWith(READING_ORDER).distinctBy { Triple(it.chapter, it.block, it.char) }
        }
    }
}
