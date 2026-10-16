// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/layout/ReadingText.kt
// Purpose: A page's text in reading order, the text ReadingUnit offsets point into (ADR-018).

package io.github.skm0078.audora.pdf.layout

import io.github.skm0078.audora.core.model.TextBox
import io.github.skm0078.audora.pdf.cleanup.MappedText
import io.github.skm0078.audora.pdf.cleanup.TextRepair
import io.github.skm0078.audora.pdf.extract.IntArrayBuilder
import io.github.skm0078.audora.pdf.extract.PageText

/**
 * The page's text as it is read: blocks in reading order, a line break between lines, a blank line between
 * paragraphs. `ReadingUnit.charStart`/`charEnd` are offsets into [text] (ADR-018), so units are contiguous and
 * ascending in reading order. Each offset maps back to the extracted text for glyph boxes.
 */
class ReadingText internal constructor(
    val pageIndex: Int,
    val text: String,
    /** Offset into `PageText.text` per UTF-16 unit of [text]; [INSERTED] for separators layout added. */
    private val sources: IntArray,
    val paragraphs: List<ReadingParagraph>,
    private val page: PageText,
) {
    fun sourceOffset(offset: Int): Int = sources[offset]

    /** The glyph box behind [offset], or null for separators and glyphs without a box. */
    fun boxAt(offset: Int): TextBox? = sources[offset].takeIf { it != INSERTED }?.let(page::boxAt)

    companion object {
        const val INSERTED = -1
    }
}

/** [start] inclusive to [end] exclusive in [ReadingText.text]. */
data class ReadingParagraph(val start: Int, val end: Int, val kind: ParagraphKind)

/**
 * Builds the reading text from a layout. With a [repair] (P2-03, ADR-020) each paragraph is repaired for speech
 * before it is added: hyphens at line ends resolved, ligatures expanded, junk characters, bullets and citation
 * markers dropped. Without one the text is the layout's characters as extracted (layout tests use that).
 */
class ReadingTextBuilder internal constructor(private val repair: TextRepair?) {
    constructor() : this(null)

    /** [lexicon]: hyphenated words printed on this page and its neighbours ("block-pulse"), for de-hyphenation. */
    fun build(page: PageText, layout: PageLayout, lexicon: Set<String> = emptySet()): ReadingText {
        val out = Output(page.text, repair, lexicon)
        for (block in layout.blocks) {
            for (paragraph in block.paragraphs) {
                // Continuations are body text on both sides (PageLayoutAnalyzer), so the paragraph keeps its kind.
                if (paragraph.continuesPrevious && out.hasOpenParagraph) {
                    out.lineBreak()
                } else {
                    out.startParagraph(paragraph.kind)
                }
                paragraph.lines.forEachIndexed { i, line ->
                    if (i > 0) out.lineBreak()
                    line.spans.forEachIndexed { j, span ->
                        // Runs split by a glyph drawn out of place ("Link¨oping") had no space between them.
                        if (j > 0 && line.spans[j - 1].end != span.start) out.space()
                        out.source(span)
                    }
                }
            }
        }
        out.closeParagraph()
        return ReadingText(page.pageIndex, out.text.toString(), out.sources.toArray(), out.paragraphs, page)
    }

    private class Output(
        private val source: String,
        private val repair: TextRepair?,
        private val lexicon: Set<String>,
    ) {
        val text = StringBuilder(source.length)
        val sources = IntArrayBuilder(source.length)
        val paragraphs = ArrayList<ReadingParagraph>()
        private var paragraph: MappedText.Builder? = null
        private var kind = ParagraphKind.BODY

        val hasOpenParagraph: Boolean get() = paragraph != null

        fun startParagraph(paragraphKind: ParagraphKind) {
            closeParagraph()
            paragraph = MappedText.Builder(LINE_CAPACITY)
            kind = paragraphKind
        }

        /** Repairs the open paragraph and appends it; a paragraph left without a visible character is dropped. */
        fun closeParagraph() {
            val open = paragraph ?: return
            paragraph = null
            val raw = open.build()
            val done = repair?.repair(raw, lexicon) ?: raw
            if (done.text.isBlank()) return
            if (text.isNotEmpty()) {
                text.append(PARAGRAPH_BREAK)
                repeat(PARAGRAPH_BREAK.length) { sources.add(ReadingText.INSERTED) }
            }
            val start = text.length
            text.append(done.text)
            done.sources.forEach(sources::add)
            paragraphs.add(ReadingParagraph(start, text.length, kind))
        }

        /**
         * A line break, except after a line-end hyphen: PDFium turns "hypo-" at a line end into "hypo\u0002" and
         * joins the next line with no break, and soft hyphens (U+00AD) mean the same. The repair resolves the
         * marks themselves (TextRepair).
         */
        fun lineBreak() {
            val open = paragraph ?: return
            if (open.last()?.let { it in LINE_END_HYPHENS } == true) return
            open.append('\n', ReadingText.INSERTED)
        }

        fun space() {
            paragraph?.append(' ', ReadingText.INSERTED)
        }

        fun source(span: TextSpan) {
            val open = paragraph ?: return
            for (offset in span.start until span.end) open.append(source[offset], offset)
        }
    }

    private companion object {
        const val PARAGRAPH_BREAK = "\n\n"
        const val LINE_CAPACITY = 256

        /** PDFium's line-end hyphen marker (STX) and the soft hyphen. */
        const val LINE_END_HYPHENS = "\u0002\u00AD"
    }
}
