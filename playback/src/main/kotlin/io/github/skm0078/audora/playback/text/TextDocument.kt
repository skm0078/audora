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

/**
 * One row of a book's table of contents and the place it points at: [chapter] (an index into
 * [TextDocument.chapters]), the [block] in it, and [char], a character offset into that block's text. [depth] is 0
 * for a top-level row, 1 for one nested under it, and so on. A row can point into the middle of a chapter: an EPUB
 * that keeps several chapters in one file, or a scanned book whose chapters start mid-page.
 */
data class ContentsEntry(
    val title: String,
    val chapter: Int,
    val block: Int = 0,
    val char: Int = 0,
    val depth: Int = 0,
) {
    /** Leaves the title out, so logging a row never writes the book's text. */
    override fun toString() = "ContentsEntry(chapter=$chapter, block=$block, char=$char, depth=$depth)"
}

/**
 * [title] and [language] from the book's own metadata, when it has any (an EPUB); the library keeps its own title.
 * [contents] is the book's own table of contents when its format has one (an EPUB); null means "the titled
 * chapters", which is what every other format's parser gives ([BookContents]).
 */
data class TextDocument(
    val title: String?,
    val language: String?,
    val chapters: List<TextChapter>,
    val contents: List<ContentsEntry>? = null,
) {
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
