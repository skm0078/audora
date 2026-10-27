// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/text/TextDocument.kt
// Purpose: A book that is text, not pages (EPUB, Word, OpenDocument, RTF, HTML, Markdown, text): chapters of blocks.

package io.github.skm0078.audora.playback.text

import io.github.skm0078.audora.core.model.UnitKind

/** How a block is read: a heading pauses longer, a list item a little less than a paragraph. */
enum class BlockKind(val unitKind: UnitKind) {
    HEADING(UnitKind.HEADING),
    BODY(UnitKind.BODY),
    LIST_ITEM(UnitKind.LIST_ITEM),
}

/** One heading, paragraph or list item; [text] has its whitespace collapsed to single spaces. */
data class TextBlock(val kind: BlockKind, val text: String)

data class TextChapter(val title: String?, val blocks: List<TextBlock>)

/** [title] and [language] from the book's own metadata, when it has any (an EPUB); the library keeps its own title. */
data class TextDocument(val title: String?, val language: String?, val chapters: List<TextChapter>) {
    val isEmpty: Boolean get() = chapters.all { it.blocks.isEmpty() }
}

sealed interface TextBookError {
    val message: String

    /**
     * Not a well-formed EPUB, Word or OpenDocument file (a damaged ZIP, no container or package, no main document:
     * a spreadsheet or slides saved with a Word-like package).
     */
    data class Damaged(override val message: String) : TextBookError

    /** Larger than [io.github.skm0078.audora.core.config.TextBookConfig.maxBookBytes]. */
    data class TooLarge(override val message: String) : TextBookError

    data class Unreadable(override val message: String) : TextBookError

    /**
     * Locked with DRM by the shop that sold it (an EPUB's encryption, a Kindle book's): refused, never broken, and the
     * listener is told why (ADR 2026-10-26-more-ebook-formats).
     */
    data class Locked(override val message: String) : TextBookError

    /** The book has no text to read (an EPUB of images only, an empty file). */
    data object NoText : TextBookError {
        override val message = "no text"
    }
}
