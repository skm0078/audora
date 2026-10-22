// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/layout/ReadingText.kt
// Purpose: A page's text in reading order, the text ReadingUnit offsets point into (ADR-018).

package io.github.skm0078.audora.pdf.layout

import io.github.skm0078.audora.core.model.TextBox
import io.github.skm0078.audora.pdf.cleanup.MappedText
import io.github.skm0078.audora.pdf.cleanup.TextRepair
import io.github.skm0078.audora.pdf.extract.IntArrayBuilder
import io.github.skm0078.audora.pdf.extract.PageText
import io.github.skm0078.audora.pdf.legacy.LegacyConverter
import io.github.skm0078.audora.pdf.legacy.LegacyRuns
import io.github.skm0078.audora.pdf.ocr.OcrWord
import io.github.skm0078.audora.pdf.quality.IndicOrder
import io.github.skm0078.audora.pdf.quality.IndicReorder

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

    /**
     * The OCR engine's mean confidence in [start] until [end], 0–1, weighted by characters; null on a text layer or
     * when no character in the range has one (ADR-035).
     */
    fun ocrConfidence(start: Int, end: Int): Float? {
        var sum = 0L
        var count = 0
        for (offset in start until end) {
            val source = sources[offset]
            val confidence = if (source == INSERTED) null else page.ocrConfidenceAt(source)
            if (confidence != null) {
                sum += confidence
                count++
            }
        }
        return if (count == 0) null else sum.toFloat() / count / OcrWord.MAX_CONFIDENCE
    }

    companion object {
        const val INSERTED = -1
    }
}

/**
 * [start] inclusive to [end] exclusive in [ReadingText.text]. [table]: a table row's cells (RI-4a), as offsets into
 * the extracted text (`ReadingText.sourceOffset` maps back). [noteRef]: a footnote's number (RI-5a).
 */
data class ReadingParagraph(
    val start: Int,
    val end: Int,
    val kind: ParagraphKind,
    val table: TableCells? = null,
    val noteRef: Int? = null,
)

/**
 * Builds the reading text from a layout. With a [repair] (P2-03, ADR-020) each paragraph is repaired for speech
 * before it is added: legacy-font text converted to Unicode (P3-04, [LegacyConverter]); Indic clusters repaired
 * and, on visual-order pages, reordered (P3-03, [IndicReorder]); then hyphens at line ends resolved, ligatures
 * expanded, junk characters, bullets and citation markers dropped. The layout's note markers (RI-5a) are left out
 * either way.
 * Without one the text is the layout's characters as extracted (layout tests use that).
 */
class ReadingTextBuilder internal constructor(private val repair: TextRepair?) {
    constructor() : this(null)

    /**
     * [lexicon]: hyphenated words printed on this page and its neighbours ("block-pulse"), for de-hyphenation.
     * [legacy]: the page's legacy-font runs (`PageQuality.legacyRuns`).
     */
    fun build(
        page: PageText,
        layout: PageLayout,
        lexicon: Set<String> = emptySet(),
        indicOrder: IndicOrder = IndicOrder.LOGICAL,
        legacy: LegacyRuns = LegacyRuns.NONE,
    ): ReadingText {
        val out = Output(page.text, silenced(page.text.length, layout.noteMarkers), repair, lexicon, indicOrder, legacy)
        for (block in layout.blocks) {
            for (paragraph in block.paragraphs) {
                // Continuations are body text on both sides (PageLayoutAnalyzer), so the paragraph keeps its kind.
                if (paragraph.continuesPrevious && out.hasOpenParagraph) {
                    out.lineBreak()
                } else {
                    out.startParagraph(paragraph)
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

    private fun silenced(length: Int, spans: List<TextSpan>): BooleanArray? {
        if (spans.isEmpty()) return null
        val silent = BooleanArray(length)
        for (span in spans) for (offset in span.start until span.end) silent[offset] = true
        return silent
    }

    private class Output(
        private val source: String,
        private val silent: BooleanArray?,
        private val repair: TextRepair?,
        private val lexicon: Set<String>,
        private val indicOrder: IndicOrder,
        private val legacy: LegacyRuns,
    ) {
        val text = StringBuilder(source.length)
        val sources = IntArrayBuilder(source.length)
        val paragraphs = ArrayList<ReadingParagraph>()
        private var paragraph: MappedText.Builder? = null
        private var kind = ParagraphKind.BODY
        private var table: TableCells? = null
        private var noteRef: Int? = null

        val hasOpenParagraph: Boolean get() = paragraph != null

        fun startParagraph(from: Paragraph) {
            closeParagraph()
            paragraph = MappedText.Builder(LINE_CAPACITY)
            kind = from.kind
            table = from.table
            noteRef = from.noteRef
        }

        /** Repairs the open paragraph and appends it; a paragraph left without a visible character is dropped. */
        fun closeParagraph() {
            val open = paragraph ?: return
            paragraph = null
            val raw = open.build()
            val done = repair?.let {
                val unicode = LegacyConverter.convert(raw, legacy).text
                it.repair(IndicReorder.apply(unicode, indicOrder), lexicon)
            } ?: raw
            if (done.text.isBlank()) return
            if (text.isNotEmpty()) {
                text.append(PARAGRAPH_BREAK)
                repeat(PARAGRAPH_BREAK.length) { sources.add(ReadingText.INSERTED) }
            }
            val start = text.length
            text.append(done.text)
            done.sources.forEach(sources::add)
            paragraphs.add(ReadingParagraph(start, text.length, kind, table, noteRef))
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
            for (offset in span.start until span.end) {
                if (silent?.get(offset) != true) open.append(source[offset], offset)
            }
        }
    }

    private companion object {
        const val PARAGRAPH_BREAK = "\n\n"
        const val LINE_CAPACITY = 256

        /** PDFium's line-end hyphen marker (STX) and the soft hyphen. */
        const val LINE_END_HYPHENS = "\u0002\u00AD"
    }
}
