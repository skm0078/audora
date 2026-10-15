// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/layout/ReadingText.kt
// Purpose: A page's text in reading order, the text ReadingUnit offsets point into (ADR-018).

package io.github.skm0078.audora.pdf.layout

import io.github.skm0078.audora.core.model.TextBox
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

class ReadingTextBuilder {
    fun build(page: PageText, layout: PageLayout): ReadingText {
        val out = Output(page.text)
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

    private class Output(private val source: String) {
        val text = StringBuilder(source.length)
        val sources = IntArrayBuilder(source.length)
        val paragraphs = ArrayList<ReadingParagraph>()
        private var paragraphStart = NONE
        private var kind = ParagraphKind.BODY

        val hasOpenParagraph: Boolean get() = paragraphStart != NONE

        fun startParagraph(paragraphKind: ParagraphKind) {
            closeParagraph()
            if (text.isNotEmpty()) insert(PARAGRAPH_BREAK)
            paragraphStart = text.length
            kind = paragraphKind
        }

        fun closeParagraph() {
            if (paragraphStart != NONE && text.length > paragraphStart) {
                paragraphs.add(ReadingParagraph(paragraphStart, text.length, kind))
            }
            paragraphStart = NONE
        }

        /**
         * A line break, except after a line-end hyphen: PDFium turns "hypo-" at a line end into "hypo\u0002" and
         * joins the next line with no break, and soft hyphens (U+00AD) mean the same. Keeping the word whole
         * keeps P1-02's output; P2-03 drops the marks themselves.
         */
        fun lineBreak() {
            if (text.isNotEmpty() && text.last() in LINE_END_HYPHENS) return
            insert(LINE_BREAK)
        }

        fun space() = insert(SPACE)

        fun source(span: TextSpan) {
            text.append(source, span.start, span.end)
            for (offset in span.start until span.end) sources.add(offset)
        }

        private fun insert(separator: String) {
            text.append(separator)
            repeat(separator.length) { sources.add(ReadingText.INSERTED) }
        }
    }

    private companion object {
        const val NONE = -1
        const val SPACE = " "
        const val LINE_BREAK = "\n"
        const val PARAGRAPH_BREAK = "\n\n"

        /** PDFium's line-end hyphen marker (STX) and the soft hyphen. */
        const val LINE_END_HYPHENS = "\u0002\u00AD"
    }
}
