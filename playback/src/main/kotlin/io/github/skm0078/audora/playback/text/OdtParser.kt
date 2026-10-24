// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/text/OdtParser.kt
// Purpose: Reads an OpenDocument text (.odt) into chapters of headings, paragraphs, list items and rows (ADR-066).

package io.github.skm0078.audora.playback.text

import io.github.skm0078.audora.core.result.Outcome

/**
 * An .odt is a ZIP whose `content.xml` holds the text and `meta.xml` the title; only those two are read. `text:h`
 * at outline level 1 (or a paragraph in the Title style) starts a chapter, deeper `text:h` are headings, paragraphs
 * inside `text:list-item` are list items, and a table row is read as its cells joined by "; ". Footnote and
 * endnote bodies go last; their citation numbers, comments and tracked deletions are not read.
 */
object OdtParser {
    private const val CONTENT = "content.xml"
    private const val META = "meta.xml"

    fun isWanted(path: String): Boolean = path == CONTENT || path == META

    fun parse(entries: Map<String, ByteArray>): Outcome<TextDocument, TextBookError> {
        val content = entries[CONTENT] ?: return Outcome.Failure(TextBookError.Damaged("no content.xml"))
        val builder = ChapterBuilder()
        OdtWalk(builder).run(scan(content))
        val document = builder.build(entries[META]?.let { OfficeMeta.title(scan(it)) })
        return if (document.isEmpty) Outcome.Failure(TextBookError.NoText) else Outcome.Success(document)
    }

    private fun scan(bytes: ByteArray): List<Markup> = MarkupScanner.scan(EpubParser.decode(bytes))
}

private class OdtWalk(private val builder: ChapterBuilder) {
    private class Paragraph(val level: Int?, val note: Boolean, val list: Boolean) {
        val text = StringBuilder()
    }

    private val paragraphs = ArrayList<Paragraph>()
    private val tables = TableRows()
    private val skipped = ArrayList<String>()
    private var listItems = 0
    private var noteBodies = 0

    fun run(markup: List<Markup>) {
        for (token in markup) {
            when (token) {
                is Markup.Start -> start(token)
                is Markup.End -> end(token.name)
                is Markup.Text -> if (skipped.isEmpty()) paragraphs.lastOrNull()?.text?.append(token.text)
            }
        }
    }

    @Suppress("CyclomaticComplexMethod") // One flat table of the OpenDocument elements that matter for reading.
    private fun start(tag: Markup.Start) {
        if (skipped.isNotEmpty() || tag.name in SKIPPED) {
            if (!tag.selfClosing) skipped += tag.name
            return
        }
        val text = paragraphs.lastOrNull()?.text
        when (tag.name) {
            "text:h" -> open(tag, tag.attributes["text:outline-level"]?.toIntOrNull()?.minus(1) ?: 0)
            "text:p" -> open(tag, STYLE_LEVELS[tag.attributes["text:style-name"]])
            "text:s" -> text?.append(" ".repeat(spaces(tag)))
            "text:tab", "text:line-break" -> text?.append(' ')
            "text:list-item" -> if (!tag.selfClosing) listItems++
            "text:note-body" -> if (!tag.selfClosing) noteBodies++
            "table:table" -> if (!tag.selfClosing) tables.openTable()
            "table:table-row" -> if (!tag.selfClosing) tables.openRow()
            "table:table-cell" -> if (!tag.selfClosing) tables.openCell()
        }
    }

    /** `<text:s text:c="3"/>` stands for three spaces; whitespace is collapsed later, so a few are plenty. */
    private fun spaces(tag: Markup.Start): Int = tag.attributes["text:c"]?.toIntOrNull()?.coerceIn(1, MAX_SPACES) ?: 1

    private fun open(tag: Markup.Start, level: Int?) {
        if (!tag.selfClosing) paragraphs += Paragraph(level, noteBodies > 0, listItems > 0)
    }

    private fun end(name: String) {
        if (skipped.isNotEmpty()) {
            if (skipped.last() == name) skipped.removeAt(skipped.lastIndex)
            return
        }
        when (name) {
            "text:h", "text:p" -> paragraphs.removeLastOrNull()?.let(::emit)
            "text:list-item" -> listItems = (listItems - 1).coerceAtLeast(0)
            "text:note-body" -> noteBodies = (noteBodies - 1).coerceAtLeast(0)
            "table:table-cell" -> tables.closeCell()
            "table:table-row" -> tables.closeRow()?.let { builder.block(BlockKind.BODY, it) }
            "table:table" -> tables.closeTable()
        }
    }

    private fun emit(paragraph: Paragraph) {
        val text = paragraph.text.toString()
        when {
            paragraph.note -> builder.note(text)
            tables.inTable -> tables.addToCell(text)
            paragraph.level == 0 -> builder.chapterHeading(text)
            paragraph.level != null -> builder.block(BlockKind.HEADING, text)
            paragraph.list -> builder.block(BlockKind.LIST_ITEM, text)
            else -> builder.block(BlockKind.BODY, text)
        }
    }

    private companion object {
        const val MAX_SPACES = 8

        /** Paragraph styles that are headings though not `text:h`: the Title starts a chapter. */
        val STYLE_LEVELS = mapOf("Title" to 0, "Subtitle" to 1)

        /** The note's number ("1"), comments, tracked deletions, alt text and the field declarations. */
        val SKIPPED = setOf(
            "text:note-citation", "office:annotation", "text:tracked-changes", "svg:title", "svg:desc",
            "text:sequence-decls", "text:variable-decls", "text:user-field-decls", "office:forms",
        )
    }
}
