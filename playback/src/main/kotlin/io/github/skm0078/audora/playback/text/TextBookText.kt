// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/text/TextBookText.kt
// Purpose: A text book as BookText: pages of about a printed page, sentences cut like the PDF pipeline's.

package io.github.skm0078.audora.playback.text

import io.github.skm0078.audora.core.config.ReadingUnitConfig
import io.github.skm0078.audora.core.config.TextBookConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.document.OutlineEntry
import io.github.skm0078.audora.pdf.document.PdfPageError
import io.github.skm0078.audora.pdf.outline.ChapterStarts
import io.github.skm0078.audora.pdf.units.BreakIteratorSentenceSplitter
import io.github.skm0078.audora.pdf.units.ScriptDetector
import io.github.skm0078.audora.pdf.units.SentenceSplitter
import io.github.skm0078.audora.playback.book.BookText

/**
 * One page of a text book: its blocks, and the chapter it belongs to. [firstBlock] and [firstChar] say where in the
 * chapter the page starts: the index of its first block's source block, and the character in that block's text (a
 * block longer than a page is cut into pieces).
 */
class TextPage(val chapter: Int, val blocks: List<TextBlock>, val firstBlock: Int = 0, val firstChar: Int = 0) {
    /** What a page's offsets index: its blocks, separated by a blank line. */
    val text: String by lazy { blocks.joinToString(BLOCK_SEPARATOR) { it.text } }

    companion object {
        const val BLOCK_SEPARATOR = "\n\n"
    }
}

/**
 * A chapter always starts a page; a page ends at the block that takes it past `pageChars`; a block longer than a page
 * is cut at a sentence end (else a space) first. The cut is a pure function of the text and the config, so a book
 * always has the same pages and resume anchors stay valid.
 */
object TextPages {
    private val SENTENCE_END = Regex("""[.!?।॥…]["'”’)]*\s""")

    fun of(document: TextDocument, config: TextBookConfig = TextBookConfig()): List<TextPage> {
        val pages = ArrayList<TextPage>()
        document.chapters.forEachIndexed { chapter, content ->
            var page = ArrayList<TextBlock>()
            var chars = 0
            var first: Piece? = null
            for (piece in pieces(content.blocks, config.pageChars)) {
                val block = piece.block
                if (page.isNotEmpty() && chars + block.text.length > config.pageChars) {
                    pages += pageOf(chapter, page, first)
                    page = ArrayList()
                    chars = 0
                }
                if (page.isEmpty()) first = piece
                page += block
                chars += block.text.length
            }
            if (page.isNotEmpty()) pages += pageOf(chapter, page, first)
        }
        return pages
    }

    /** A block, or a page-sized piece of one: [source] is the block's index in its chapter, [char] where it starts. */
    private class Piece(val source: Int, val char: Int, val block: TextBlock)

    private fun pageOf(chapter: Int, blocks: List<TextBlock>, first: Piece?) =
        TextPage(chapter, blocks, first?.source ?: 0, first?.char ?: 0)

    /** Each block cut to page size, with the source block's index and where in its text the piece starts. */
    private fun pieces(blocks: List<TextBlock>, pageChars: Int): List<Piece> = blocks.flatMapIndexed { source, block ->
        var from = 0
        split(block, pageChars).map { piece ->
            val at = block.text.indexOf(piece.text, from).coerceAtLeast(from)
            from = at + piece.text.length
            Piece(source, at, piece)
        }
    }

    private fun split(block: TextBlock, pageChars: Int): List<TextBlock> {
        if (block.text.length <= pageChars) return listOf(block)
        val pieces = ArrayList<TextBlock>()
        var rest = block.text
        while (rest.length > pageChars) {
            val window = rest.substring(0, pageChars)
            val cut = SENTENCE_END.findAll(window).lastOrNull()?.range?.last?.plus(1)
                ?: window.lastIndexOf(' ').takeIf { it > 0 }
                ?: pageChars
            pieces += TextBlock(block.kind, rest.substring(0, cut).trim())
            rest = rest.substring(cut).trim()
        }
        if (rest.isNotEmpty()) pieces += TextBlock(block.kind, rest)
        return pieces
    }
}

/**
 * [BookText] over a text book. Sentences are split with the PDF pipeline's splitter (with its Hindi and Bengali
 * rules) and each unit's language detected the same way, so voices, pauses and the reader behave as for a PDF.
 * Born-digital text needs no cleanup, OCR or speakability score. Thread-safe: pages are immutable.
 */
class TextBookText(
    document: TextDocument,
    private val config: TextBookConfig = TextBookConfig(),
    private val units: ReadingUnitConfig = ReadingUnitConfig(),
    private val splitter: SentenceSplitter = BreakIteratorSentenceSplitter(),
) : BookText {
    private val pages: List<TextPage> = TextPages.of(document, config)
    private val bookLang: Lang =
        Lang.entries.firstOrNull { document.language?.startsWith(it.bcp47Tag) == true } ?: Lang.EN
    private val firstPages: Map<Int, Int> = pages.indices.reversed().associateBy { pages[it].chapter }

    /** The table of contents ([BookContents]) with the page each row starts on, for the reader's chapter list. */
    val contents: List<ContentsRow> = BookContents.of(document, config).map {
        ContentsRow(it.title, pageOf(it), it.depth)
    }

    /** [contents] as a tree, as a PDF's outline is, so the reader lists and indents both the same way. */
    val outline: List<OutlineEntry> = ContentsRow.tree(contents)

    /**
     * Where the chapters of the contents start ([ChapterStarts]: the shallowest level with two or more), for the
     * end-of-chapter sleep timer and audiobook chapters. A book with fewer has each of its files' starts instead.
     */
    private val chapterStarts: List<Int> = ChapterStarts.from(outline, pages.size, Int.MAX_VALUE)
        .takeIf { it.size >= MIN_CHAPTERS }
        ?: chapterFileStarts(document)

    override val pageCount: Int get() = pages.size

    override fun units(page: Int): Outcome<List<ReadingUnit>, PdfPageError> {
        val content = pages.getOrNull(page) ?: return Outcome.Failure(PdfPageError.PageOutOfRange(page, pages.size))
        val out = ArrayList<ReadingUnit>()
        var offset = 0
        for (block in content.blocks) {
            addBlock(out, page, block, offset)
            offset += block.text.length + TextPage.BLOCK_SEPARATOR.length
        }
        return Outcome.Success(out)
    }

    override fun chapterStarts(): List<Int> = chapterStarts

    /** The last page of the row's chapter that starts at or before the row's place. */
    private fun pageOf(row: ContentsEntry): Int {
        var page = firstPages[row.chapter] ?: return 0
        while (page + 1 < pages.size && pages[page + 1].chapter == row.chapter && startsBy(pages[page + 1], row)) page++
        return page
    }

    private fun startsBy(page: TextPage, row: ContentsEntry) =
        page.firstBlock < row.block || (page.firstBlock == row.block && page.firstChar <= row.char)

    private fun chapterFileStarts(document: TextDocument): List<Int> = if (document.chapters.size < MIN_CHAPTERS) {
        emptyList()
    } else {
        pages.indices.filter { it == 0 || pages[it].chapter != pages[it - 1].chapter }
    }

    override fun close() = Unit

    private fun addBlock(out: MutableList<ReadingUnit>, page: Int, block: TextBlock, offset: Int) {
        val text = block.text
        val lang = ScriptDetector.dominantLang(text) ?: bookLang
        val pieces = splitter.split(text, lang).flatMap { cap(text, trim(text, it)) }
            .filter { range -> text.substring(range.first, range.last + 1).any { it.isLetterOrDigit() } }
        pieces.forEachIndexed { i, range ->
            val piece = text.substring(range.first, range.last + 1)
            out += ReadingUnit(
                page = page,
                index = out.size,
                charStart = offset + range.first,
                charEnd = offset + range.last + 1,
                text = piece,
                lang = ScriptDetector.dominantLang(piece) ?: lang,
                pauseAfterMs = pauseAfter(block.kind, last = i == pieces.lastIndex),
                bounds = null,
                kind = block.kind.unitKind,
            )
        }
    }

    private fun pauseAfter(kind: BlockKind, last: Boolean): Int = when {
        kind == BlockKind.HEADING -> maxOf(units.sentencePauseMs, units.headingPauseMs)
        !last -> units.sentencePauseMs
        kind == BlockKind.LIST_ITEM -> maxOf(units.sentencePauseMs, units.listItemPauseMs)
        else -> maxOf(units.sentencePauseMs, units.paragraphPauseMs)
    }

    private fun trim(text: String, range: IntRange): IntRange {
        var start = range.first
        var end = range.last + 1
        while (start < end && text[start].isWhitespace()) start++
        while (end > start && text[end - 1].isWhitespace()) end--
        return start until end
    }

    private companion object {
        const val MIN_CHAPTERS = 2
    }

    /** A "sentence" longer than maxUnitChars is cut at the last space before the limit, as the PDF pipeline does. */
    private fun cap(text: String, range: IntRange): List<IntRange> {
        if (range.isEmpty()) return emptyList()
        val pieces = ArrayList<IntRange>()
        var start = range.first
        val end = range.last + 1
        while (end - start > units.maxUnitChars) {
            val limit = start + units.maxUnitChars
            var cut = text.lastIndexOf(' ', limit).takeIf { it > start } ?: limit
            if (Character.isLowSurrogate(text[cut])) cut--
            pieces += trim(text, start until cut)
            start = trim(text, cut until end).first
        }
        pieces += start until end
        return pieces.filterNot { it.isEmpty() }
    }
}

/** One row of a text book's contents: its [title], the [page] it starts on, and its [depth] (0 at the top). */
data class ContentsRow(val title: String, val page: Int, val depth: Int = 0) {
    /** Leaves the title out, so logging a row never writes the book's text. */
    override fun toString() = "ContentsRow(page=$page, depth=$depth)"

    companion object {
        /** Rows in contents order to a tree: a row deeper than the one before it is nested under it. */
        fun tree(rows: List<ContentsRow>): List<OutlineEntry> {
            class Node(val row: ContentsRow, val children: MutableList<Node> = ArrayList())

            val roots = ArrayList<Node>()
            val open = ArrayList<Node>()
            for (row in rows) {
                while (open.isNotEmpty() && open.last().row.depth >= row.depth) open.removeAt(open.lastIndex)
                val node = Node(row)
                (open.lastOrNull()?.children ?: roots) += node
                open += node
            }
            fun Node.entry(): OutlineEntry = OutlineEntry(row.title, row.page, children.map { it.entry() })
            return roots.map { it.entry() }
        }
    }
}
