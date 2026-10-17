// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/epub/BookDocument.kt
// Purpose: A book's text as structure (chapters, headings, paragraphs, lists, tables, notes), for its copies.

package io.github.skm0078.audora.playback.epub

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.UnitKind

/**
 * One sentence of a copy.
 * @property pageBreak page [ReadingUnit.page] starts here: the copy marks it, so a reader can go to a page. The number
 *   is the PDF's page position, not the label printed on the page (that needs PDFium's page labels in `:pdf`).
 */
data class DocSentence(val unit: ReadingUnit, val pageBreak: Boolean = false) {
    /** Unique in the book; the talking book's audio points at it. */
    val id: String get() = "s${unit.page}-${unit.index}"
}

/** A block of a chapter, in reading order. */
sealed interface DocBlock {
    val sentences: List<DocSentence>

    data class Heading(override val sentences: List<DocSentence>) : DocBlock

    /** Body text, a caption, an equation or a sidebar ([kind]). */
    data class Paragraph(override val sentences: List<DocSentence>, val kind: UnitKind = UnitKind.BODY) : DocBlock

    /** A run of list items, each one or more sentences. */
    data class ListItems(val items: List<List<DocSentence>>) : DocBlock {
        override val sentences: List<DocSentence> get() = items.flatten()
    }

    /** A table: its header row (null when the table goes on from the page before) and its data rows. */
    data class Table(val header: DocSentence?, val rows: List<DocSentence>) : DocBlock {
        override val sentences: List<DocSentence> get() = listOfNotNull(header) + rows
    }

    /** A footnote or endnote; kept apart from the body text, at the end of its chapter. */
    data class Note(override val sentences: List<DocSentence>) : DocBlock
}

/**
 * Pages [pages] of the book, zero-based. [title] is its first heading, or a page range when it has none.
 */
data class DocChapter(
    val number: Int,
    val title: String,
    val pages: IntRange,
    val blocks: List<DocBlock>,
    val notes: List<DocBlock.Note>,
) {
    val sentences: List<DocSentence> get() = (blocks + notes).flatMap { it.sentences }

    /** The pages (PDF positions) that start in this chapter, in order (one-based, as the reader shows them). */
    val pageStarts: List<Int> get() = sentences.filter { it.pageBreak }.map { it.unit.page + 1 }
}

/** [lang] is the language most of its text is in; [langs] every language it has. */
data class BookDocument(val title: String, val lang: Lang, val langs: Set<Lang>, val chapters: List<DocChapter>)
