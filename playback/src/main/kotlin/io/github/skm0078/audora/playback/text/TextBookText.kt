// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/text/TextBookText.kt
// Purpose: A text book as BookText: pages of about a printed page, sentences cut like the PDF pipeline's.

package io.github.skm0078.audora.playback.text

import io.github.skm0078.audora.core.config.ReadingUnitConfig
import io.github.skm0078.audora.core.config.TextBookConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.document.PdfPageError
import io.github.skm0078.audora.pdf.units.BreakIteratorSentenceSplitter
import io.github.skm0078.audora.pdf.units.ScriptDetector
import io.github.skm0078.audora.pdf.units.SentenceSplitter
import io.github.skm0078.audora.playback.book.BookText

/** One page of a text book: its blocks, and the chapter it belongs to. */
class TextPage(val chapter: Int, val blocks: List<TextBlock>) {
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
            for (block in content.blocks.flatMap { split(it, config.pageChars) }) {
                if (page.isNotEmpty() && chars + block.text.length > config.pageChars) {
                    pages += TextPage(chapter, page)
                    page = ArrayList()
                    chars = 0
                }
                page += block
                chars += block.text.length
            }
            if (page.isNotEmpty()) pages += TextPage(chapter, page)
        }
        return pages
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
    private val chapterStarts: List<Int> = if (document.chapters.size < 2) {
        emptyList()
    } else {
        pages.indices.filter { it == 0 || pages[it].chapter != pages[it - 1].chapter }
    }

    /** The chapters with a title and the page each starts on, for the reader's chapter list. */
    val contents: List<Pair<String, Int>> = chapterStarts.mapNotNull { page ->
        document.chapters[pages[page].chapter].title?.let { it to page }
    }

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
