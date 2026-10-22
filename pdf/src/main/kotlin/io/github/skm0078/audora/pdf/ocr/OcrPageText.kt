// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/ocr/OcrPageText.kt
// Purpose: Turns OCR'd words of one page into a page with boxes and a layout the reading-text builder can repair.

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.core.model.TextBox
import io.github.skm0078.audora.pdf.cleanup.hyphenatedPairs
import io.github.skm0078.audora.pdf.cleanup.tableParagraphs
import io.github.skm0078.audora.pdf.extract.ActualTextRebuild
import io.github.skm0078.audora.pdf.extract.PageText
import io.github.skm0078.audora.pdf.extract.PageTextStats
import io.github.skm0078.audora.pdf.extract.RawChars
import io.github.skm0078.audora.pdf.extract.putBox
import io.github.skm0078.audora.pdf.layout.LayoutMode
import io.github.skm0078.audora.pdf.layout.PageLayout
import io.github.skm0078.audora.pdf.layout.Paragraph
import io.github.skm0078.audora.pdf.layout.ParagraphKind
import io.github.skm0078.audora.pdf.layout.TextBlock
import io.github.skm0078.audora.pdf.layout.TextLine
import io.github.skm0078.audora.pdf.layout.TextSpan
import io.github.skm0078.audora.pdf.layout.TextTurn
import io.github.skm0078.audora.pdf.render.ViewRect
import java.text.Normalizer

/** OCR text as a page with word boxes (when known) plus its layout, and the hyphenated words it prints. */
internal class OcrPage(val text: PageText, val layout: PageLayout, val hyphenated: Set<String>, val tables: Int = 0)

/**
 * Tesseract (page segmentation "auto") already puts blocks in reading order, one line per text line, in paragraphs.
 * That structure becomes a one-block [PageLayout], so the same repair as a text layer applies: line-end hyphens
 * resolved against the page's own hyphenated words (TextRepair), junk characters folded, and the same sentence split.
 *
 * Every character of a word gets the word's box, mapped onto the page by `toPage`, so a sentence read from a scan
 * is highlighted line by line like any other (ADR-032); without boxes (`toPage` null, or an engine that gives none)
 * `ReadingUnit.bounds` stays null. Lines in `dropped` (running headers and page numbers, [OcrClutter]) are left out,
 * and a paragraph holding only a page number ("12", "- 3 -", "১২") at the top or bottom of the page is dropped too,
 * which also covers text without positions, unless a table took it (a ruled table's last cell, read last).
 *
 * With `tables` and boxes, the page's tables ([OcrTables], RI-4a part 2) become a header paragraph and one paragraph
 * per row, put where the table's first word was read; their words leave the lines they were read in.
 */
internal object OcrPageText {
    private const val PARAGRAPH_BREAK = "\n\n"
    private const val LINE_BREAK = '\n'
    private const val WORD_BREAK = ' '

    /** PDFium's line-end hyphen mark, which the reading-text builder and TextRepair already resolve. */
    private const val HYPHEN_MARKER = '\u0002'
    private const val MAX_FOLIO_DIGITS = 4
    private const val NO_CONFIDENCE = -1

    /** Not the index of a word, nor one before any (so the first word kept always starts a run). */
    private const val NO_WORD = -2

    /** The turn that sets a page's boxes upright, by `OcrText.quarterTurns`. */
    private val TURNS = listOf(TextTurn.NONE, TextTurn.CLOCKWISE, TextTurn.HALF, TextTurn.COUNTER_CLOCKWISE)
    private val FOLIO = Regex("""[\s\-–—.|()\[\]]*\p{Nd}{1,$MAX_FOLIO_DIGITS}[\s\-–—.|()\[\]]*""")

    fun build(
        pageIndex: Int,
        ocr: OcrText,
        dropped: Set<Int> = emptySet(),
        toPage: ((ViewRect) -> TextBox)? = null,
        tables: OcrTables? = null,
    ): OcrPage {
        val paragraphs = paragraphsOf(ocr, dropped)
        val text = StringBuilder()
        val boxes = ArrayList<TextBox?>()
        val confidences = ArrayList<Byte>()
        // Each word's span of the text, by paragraph and line, in reading order.
        val spans = ArrayList<List<List<TextSpan>>>(paragraphs.size)
        fun append(chars: String, box: TextBox?, confidence: Int? = null) {
            text.append(chars)
            repeat(chars.length) {
                boxes.add(box)
                confidences.add((confidence ?: NO_CONFIDENCE).toByte())
            }
        }
        for (lines in paragraphs) {
            if (text.isNotEmpty()) append(PARAGRAPH_BREAK, null)
            spans.add(
                lines.mapIndexed { i, words ->
                    if (i > 0) append(LINE_BREAK.toString(), null)
                    val brokenWord = endsInBrokenWord(words, lines.getOrNull(i + 1))
                    words.mapIndexed { w, word ->
                        if (w > 0) append(WORD_BREAK.toString(), null)
                        val box = word.box?.let { rect -> toPage?.invoke(rect) }
                        val broken = brokenWord && w == words.lastIndex
                        val chars = if (broken) word.text.dropLast(1) + HYPHEN_MARKER else word.text
                        val start = text.length
                        append(chars, box, word.confidence)
                        TextSpan(start, text.length)
                    }
                },
            )
        }
        val flat = text.toString()
        val page = PageText(
            pageIndex = pageIndex,
            text = flat,
            boxes = boxArray(boxes),
            fontRuns = emptyList(),
            stats = PageTextStats(0, 0, 0, 0, 0, ActualTextRebuild.NOT_NEEDED),
            ocrConfidences = confidences.toByteArray(),
        )
        val read = paragraphs.zip(spans) { lines, lineSpans -> lines.zip(lineSpans) { words, of -> words.zip(of) } }
        val found = if (tables != null && toPage != null) tablesOf(page, ocr, read, toPage, tables) else emptyList()
        val taken = found.flatMapTo(HashSet()) { it.words }
        val folios = folios(paragraphs).filterTo(HashSet()) { p -> spans[p].none { line -> line.any { it in taken } } }
        val kept = layoutParagraphs(spans.filterIndexed { p, _ -> p !in folios }, found)
        val blocks = if (kept.isEmpty()) emptyList() else listOf(TextBlock(kept, box = null))
        val layout = PageLayout(pageIndex, blocks, bodySize = 0f, mode = LayoutMode.EXTRACTION_ORDER)
        return OcrPage(page, layout, hyphenatedPairs(flat), found.size)
    }

    /**
     * The page's tables, found in the table frame: the page turned so its text runs upright. [read] is each word with
     * its span of the page text, by paragraph and line.
     */
    private fun tablesOf(
        page: PageText,
        ocr: OcrText,
        read: List<List<List<Pair<OcrWord, TextSpan>>>>,
        toPage: (ViewRect) -> TextBox,
        tables: OcrTables,
    ): List<OcrTable> {
        val turn = TURNS[ocr.quarterTurns]
        val lines = read.flatMapIndexed { p, lines ->
            lines.map { words ->
                val placed = words.mapNotNull { (word, span) -> word.box?.let { span to turn.apply(toPage(it)) } }
                val top = placed.maxOfOrNull { it.second.top } ?: 0f
                TableLine(placed.map { (span, box) -> TableWord(span, box, top) }, p)
            }
        }
        return tables.find(page.withBoxes(turn::apply), lines, ocr.rules.map { turn.apply(toPage(it)) })
    }

    /**
     * The layout's paragraphs: the OCR paragraphs without the words [tables] took, each table's paragraphs put where
     * its first word was read. A line keeps one run per stretch of words left to it.
     */
    private fun layoutParagraphs(spans: List<List<List<TextSpan>>>, tables: List<OcrTable>): List<Paragraph> {
        val taken = tables.flatMapTo(HashSet()) { it.words }
        val firstWords = tables.mapIndexed { i, table -> table.words.minBy { it.start } to i }.toMap()
        val out = ArrayList<Paragraph>()
        for (lines in spans) {
            val kept = ArrayList<TextLine>()
            fun flush() {
                if (kept.isNotEmpty()) out.add(Paragraph(kept.toList(), ParagraphKind.BODY))
                kept.clear()
            }
            for (words in lines) {
                words.firstNotNullOfOrNull { firstWords[it] }?.let { index ->
                    flush()
                    val table = tables[index]
                    out.addAll(tableParagraphs(table.header, table.rows, index, box = null, size = 0f))
                }
                val runs = runsOf(words.filterNot { it in taken }, words)
                if (runs.isNotEmpty()) kept.add(TextLine(runs, box = null, size = 0f, bold = false))
            }
            flush()
        }
        return out
    }

    /** [left] (some of [words], in order) as one run per stretch of consecutive words. */
    private fun runsOf(left: List<TextSpan>, words: List<TextSpan>): List<TextSpan> {
        val runs = ArrayList<TextSpan>()
        var start: TextSpan? = null
        var end: TextSpan? = null
        var previous = NO_WORD
        for (span in left) {
            val index = words.indexOf(span)
            if (start != null && index != previous + 1) {
                runs.add(TextSpan(start.start, requireNotNull(end).end))
                start = null
            }
            if (start == null) start = span
            end = span
            previous = index
        }
        if (start != null) runs.add(TextSpan(start.start, requireNotNull(end).end))
        return runs
    }

    private fun boxArray(boxes: List<TextBox?>): FloatArray {
        val out = FloatArray(boxes.size * RawChars.BOX_FLOATS) { Float.NaN }
        boxes.forEachIndexed { i, box -> if (box != null) out.putBox(i, box) }
        return out
    }

    /** Paragraphs of lines of NFC words, [dropped] lines and empty words left out. */
    private fun paragraphsOf(ocr: OcrText, dropped: Set<Int>): List<List<List<OcrWord>>> {
        val paragraphs = ArrayList<List<List<OcrWord>>>()
        var current = ArrayList<List<OcrWord>>()
        var paragraph = -1
        ocr.lines.forEachIndexed { i, line ->
            if (i in dropped) return@forEachIndexed
            val words = line.words.mapNotNull { word ->
                val text = Normalizer.normalize(word.text, Normalizer.Form.NFC).trim()
                if (text.isEmpty()) null else OcrWord(text, word.box, word.confidence)
            }
            if (words.isEmpty()) return@forEachIndexed
            if (line.paragraph != paragraph && current.isNotEmpty()) {
                paragraphs.add(current)
                current = ArrayList()
            }
            paragraph = line.paragraph
            current.add(words)
        }
        if (current.isNotEmpty()) paragraphs.add(current)
        return paragraphs
    }

    /** The indexes of the first and last paragraphs when they hold only a page number. */
    private fun folios(paragraphs: List<List<List<OcrWord>>>): Set<Int> {
        if (paragraphs.size < 2) return emptySet()
        fun isFolio(paragraph: List<List<OcrWord>>) =
            paragraph.size == 1 && FOLIO.matches(paragraph[0].joinToString(" ") { it.text })
        return setOf(0, paragraphs.lastIndex).filterTo(HashSet()) { isFolio(paragraphs[it]) }
    }

    /**
     * "nega-" followed by a line starting with a lower-case letter is a word broken at the line end: its hyphen
     * becomes PDFium's marker, so it is joined unless the page prints the pair with a hyphen elsewhere.
     */
    private fun endsInBrokenWord(line: List<OcrWord>, next: List<OcrWord>?): Boolean {
        val last = line.last().text
        val first = next?.first()?.text ?: return false
        return last.length > 1 && last.last() == '-' && last[last.length - 2].isLetter() && first.first().isLowerCase()
    }
}
