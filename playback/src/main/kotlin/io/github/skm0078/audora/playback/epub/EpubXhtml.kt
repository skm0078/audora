// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/epub/EpubXhtml.kt
// Purpose: Writes a copy's chapters and its navigation document as EPUB 3 XHTML, with page numbers and languages.

package io.github.skm0078.audora.playback.epub

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.UnitKind

/** The words a copy shows besides the book's own text, in the app's language. */
data class CopyLabels(val contents: String = "Contents", val pages: String = "Pages", val notes: String = "Notes")

/**
 * Accessible by construction (EPUB Accessibility 1.1): real headings, lists and tables with header cells, the language
 * of every sentence that differs from the book's, a page-break marker per page (PDF position) and a page list, notes as
 * `doc-footnote` asides. Each sentence is one element with an [DocSentence.id], so a talking book can highlight it.
 */
internal class EpubXhtml(private val doc: BookDocument, private val labels: CopyLabels = CopyLabels()) {
    fun chapter(chapter: DocChapter): String = page(chapter.title) {
        append("<section epub:type=\"chapter\" role=\"doc-chapter\" aria-label=\"").append(Xml.escape(chapter.title))
        append("\">\n")
        var headings = 0
        chapter.blocks.forEach { block ->
            if (block is DocBlock.Heading) headings++
            block(block, firstHeading = headings == 1 && block is DocBlock.Heading)
        }
        if (chapter.notes.isNotEmpty()) {
            append("<section class=\"notes\" aria-label=\"").append(Xml.escape(labels.notes)).append("\">\n")
            chapter.notes.forEach { note ->
                append("<aside epub:type=\"footnote\" role=\"doc-footnote\" id=\"n-").append(note.sentences.first().id)
                append("\"><p>")
                sentences(note.sentences)
                append("</p></aside>\n")
            }
            append("</section>\n")
        }
        append("</section>\n")
    }

    fun nav(): String = page(labels.contents) {
        append("<nav epub:type=\"toc\" role=\"doc-toc\" id=\"toc\"><h1>").append(Xml.escape(labels.contents))
        append("</h1>\n<ol>\n")
        doc.chapters.forEach { chapter ->
            append("<li><a href=\"").append(EpubPaths.chapter(chapter.number)).append("\">")
            append(Xml.escape(chapter.title)).append("</a></li>\n")
        }
        append("</ol></nav>\n")
        val pages = doc.chapters.flatMap { chapter -> chapter.pageStarts.map { chapter.number to it } }
        if (pages.isNotEmpty()) {
            append("<nav epub:type=\"page-list\" role=\"doc-pagelist\" hidden=\"hidden\"><h2>")
            append(Xml.escape(labels.pages)).append("</h2>\n<ol>\n")
            pages.forEach { (number, page) ->
                append("<li><a href=\"").append(EpubPaths.chapter(number)).append("#page").append(page).append("\">")
                append(page).append("</a></li>\n")
            }
            append("</ol></nav>\n")
        }
    }

    private fun page(title: String, body: StringBuilder.() -> Unit): String = buildString {
        val lang = doc.lang.bcp47Tag
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE html>\n")
        append("<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\" lang=\"")
        append(lang).append("\" xml:lang=\"").append(lang).append("\">\n<head>\n<meta charset=\"UTF-8\"/>\n<title>")
        append(Xml.escape(title)).append("</title>\n")
        append("<link rel=\"stylesheet\" type=\"text/css\" href=\"").append(EpubPaths.STYLE).append("\"/>\n")
        append("</head>\n<body>\n")
        body()
        append("</body>\n</html>\n")
    }

    private fun StringBuilder.block(block: DocBlock, firstHeading: Boolean) {
        when (block) {
            is DocBlock.Heading -> wrapped(if (firstHeading) "h1" else "h2", block.sentences)

            is DocBlock.Paragraph -> if (block.kind == UnitKind.ASIDE) {
                append("<aside class=\"sidebar\">")
                wrapped("p", block.sentences)
                append("</aside>\n")
            } else {
                wrapped("p", block.sentences, PARAGRAPH_CLASSES[block.kind])
            }

            is DocBlock.ListItems -> {
                append("<ul>\n")
                block.items.forEach { wrapped("li", it) }
                append("</ul>\n")
            }

            is DocBlock.Table -> table(block)

            is DocBlock.Note -> wrapped("p", block.sentences)
        }
    }

    private fun StringBuilder.wrapped(tag: String, sentences: List<DocSentence>, cssClass: String? = null) {
        append('<').append(tag)
        cssClass?.let { append(" class=\"").append(it).append('"') }
        append('>')
        sentences(sentences)
        append("</").append(tag).append(">\n")
    }

    private fun StringBuilder.sentences(sentences: List<DocSentence>) {
        sentences.forEachIndexed { i, sentence ->
            if (i > 0) append(' ')
            pageBreak(sentence)
            append("<span id=\"").append(sentence.id).append('"')
            lang(sentence.unit.lang)
            append('>').append(Xml.escape(sentence.unit.text)).append("</span>")
        }
    }

    private fun StringBuilder.table(table: DocBlock.Table) {
        append("<table>\n")
        table.header?.let { header ->
            append("<thead>")
            row(header, cellTag = "th")
            append("</thead>\n")
        }
        if (table.rows.isNotEmpty()) {
            append("<tbody>\n")
            table.rows.forEach { row(it, cellTag = "td") }
            append("</tbody>\n")
        }
        append("</table>\n")
    }

    /** A row is one sentence: the row element carries its id, so the talking book highlights the whole row. */
    private fun StringBuilder.row(sentence: DocSentence, cellTag: String) {
        append("<tr id=\"").append(sentence.id).append('"')
        lang(sentence.unit.lang)
        append('>')
        sentence.cells().forEachIndexed { i, cell ->
            append('<').append(cellTag)
            if (cellTag == "th") append(" scope=\"col\"")
            append('>')
            if (i == 0) pageBreak(sentence)
            append(Xml.escape(cell)).append("</").append(cellTag).append('>')
        }
        append("</tr>\n")
    }

    private fun StringBuilder.pageBreak(sentence: DocSentence) {
        if (!sentence.pageBreak) return
        val number = sentence.unit.page + 1
        append("<span epub:type=\"pagebreak\" role=\"doc-pagebreak\" id=\"page").append(number)
        append("\" aria-label=\"").append(number).append("\"></span>")
    }

    private fun StringBuilder.lang(lang: Lang) {
        if (lang == doc.lang) return
        append(" lang=\"").append(lang.bcp47Tag).append("\" xml:lang=\"").append(lang.bcp47Tag).append('"')
    }

    private companion object {
        val PARAGRAPH_CLASSES = mapOf(UnitKind.CAPTION to "caption", UnitKind.EQUATION to "equation")
    }
}

/** A table row's cells as text (trimmed); the whole sentence as one cell when `:pdf` found no cells. */
internal fun DocSentence.cells(): List<String> {
    val spans = unit.table?.cells.orEmpty()
    return if (spans.isEmpty()) listOf(unit.text) else spans.map { unit.text.substring(it.start, it.end).trim() }
}

/** File names inside the EPUB. */
internal object EpubPaths {
    const val STYLE = "style.css"
    const val NAV = "nav.xhtml"
    const val PACKAGE = "content.opf"
    const val ROOT = "OEBPS/"

    fun chapter(number: Int): String = "chapter-$number.xhtml"

    fun overlay(number: Int): String = "chapter-$number.smil"

    fun audio(number: Int): String = "audio/chapter-$number.m4a"
}

/** XML text and attribute escaping; drops the control characters XML 1.0 does not allow. */
internal object Xml {
    private const val TAB = '\t'
    private const val LINE_FEED = '\n'
    private const val CARRIAGE_RETURN = '\r'
    private const val FIRST_ALLOWED = ' '

    fun escape(text: String): String = buildString(text.length) {
        text.forEach { c ->
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                TAB, LINE_FEED, CARRIAGE_RETURN -> append(c)
                else -> if (c >= FIRST_ALLOWED && c != '￾' && c != '￿') append(c)
            }
        }
    }
}
