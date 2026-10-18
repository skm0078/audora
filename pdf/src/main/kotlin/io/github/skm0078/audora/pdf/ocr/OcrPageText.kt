// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/ocr/kt
// Purpose: Turns OCR'd words of one page into a page with boxes and a layout the reading-text builder can repair.

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.core.config.LayoutConfig
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
import io.github.skm0078.audora.pdf.layout.TextBlock
import io.github.skm0078.audora.pdf.layout.TextLine
import io.github.skm0078.audora.pdf.layout.TextSpan
import io.github.skm0078.audora.pdf.layout.TextTurn
import io.github.skm0078.audora.pdf.render.ViewRect
import java.text.Normalizer
import kotlin.math.abs

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
 * With boxes, the page's tables ([OcrTables], RI-4a part 2) become a header paragraph and one paragraph
 * per row, put where the table's first word was read; their words leave the lines they were read in.
 */
internal class OcrPageText(private val tableFinder: OcrTables, private val layoutConfig: LayoutConfig) {
    private val noteMarkers = OcrNoteMarkers(layoutConfig.notes)
    private val dandas = OcrDandas(layoutConfig.ocrDandaMinIndicWords)
    private val footnotes = OcrFootnotes(layoutConfig.notes)

    fun build(
        pageIndex: Int,
        ocr: OcrText,
        dropped: Set<Int> = emptySet(),
        toPage: ((ViewRect) -> TextBox)? = null,
    ): OcrPage {
        val (paragraphs, sizes) = paragraphsOf(ocr, dropped, bulletGap(ocr, toPage))
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
        val found = if (toPage != null) tablesOf(page, ocr, read, toPage) else emptyList()
        val taken = found.flatMapTo(HashSet()) { it.words }
        val folios = folios(paragraphs).filterTo(HashSet()) { p -> spans[p].none { line -> line.any { it in taken } } }
        val keep = paragraphs.indices.filter { it !in folios }
        val bodySize = bodySizeOf(sizes)
        val kinds = OcrParagraphKinds(page, layoutConfig, bodySize)
        val laidOut = layoutParagraphs(keep.map { spans[it] }, keep.map { sizes[it] }, found, kinds)
        val notes = if (toPage == null) {
            OcrFootnotes.Tagged(laidOut, emptyList())
        } else {
            val turn = ocr.uprightTurn(toPage)
            val sheet = OcrFootnotes.Sheet(turn.apply(toPage(WHOLE_VIEW)), ocr.rules.map { turn.apply(toPage(it)) })
            footnotes.tag(page.withBoxes(turn::apply), sheet, laidOut, taken, bodySize)
        }
        val kept = notes.paragraphs
        val blocks = if (kept.isEmpty()) emptyList() else listOf(TextBlock(kept, box = null))
        val layout = PageLayout(
            pageIndex,
            blocks,
            bodySize = 0f,
            mode = LayoutMode.EXTRACTION_ORDER,
            noteMarkers = notes.markers,
        )
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
    ): List<OcrTable> {
        val turn = ocr.uprightTurn(toPage)
        val lines = read.flatMapIndexed { p, lines ->
            lines.map { words ->
                val placed = words.mapNotNull { (word, span) -> word.box?.let { span to turn.apply(toPage(it)) } }
                val top = placed.maxOfOrNull { it.second.top } ?: 0f
                TableLine(placed.map { (span, box) -> TableWord(span, box, top) }, p)
            }
        }
        return tableFinder.find(page.withBoxes(turn::apply), lines, ocr.rules.map { turn.apply(toPage(it)) })
    }

    /**
     * The layout's paragraphs: the OCR paragraphs without the words [tables] took, each table's paragraphs put where
     * its first word was read, the rest split into headings, list items and body text ([OcrParagraphKinds]). A line
     * keeps one run per stretch of words left to it, and its size.
     */
    private fun layoutParagraphs(
        spans: List<List<List<TextSpan>>>,
        sizes: List<List<Float>>,
        tables: List<OcrTable>,
        kinds: OcrParagraphKinds,
    ): List<Paragraph> {
        val taken = tables.flatMapTo(HashSet()) { it.words }
        val firstWords = tables.mapIndexed { i, table -> table.words.minBy { it.start } to i }.toMap()
        val out = ArrayList<Paragraph>()
        spans.forEachIndexed { p, lines ->
            val kept = ArrayList<TextLine>()
            fun flush() {
                out.addAll(kinds.paragraphs(kept.toList()))
                kept.clear()
            }
            lines.forEachIndexed { l, words ->
                // Every table whose first word is on this line: two set side by side start on the same line.
                val starting = words.mapNotNull { firstWords[it] }
                if (starting.isNotEmpty()) flush()
                for (index in starting) {
                    val table = tables[index]
                    out.addAll(tableParagraphs(table.header, table.rows, index, box = null, size = 0f))
                }
                val runs = runsOf(words.filterNot { it in taken }, words)
                if (runs.isNotEmpty()) kept.add(TextLine(runs, box = null, size = sizes[p][l], bold = false))
            }
            flush()
        }
        return out
    }

    /**
     * Whether a word and the next are as close as a bullet and its text: at most `LayoutConfig.ocrBulletMaxGapEm` of
     * the text's height apart in the upright page. Farther apart, the "©" is a symbol in a table's first column.
     * Without boxes, any distance passes.
     */
    private fun bulletGap(ocr: OcrText, toPage: ((ViewRect) -> TextBox)?): (OcrWord, OcrWord) -> Boolean {
        val turn = toPage?.let { ocr.uprightTurn(it) } ?: TextTurn.NONE
        return { bullet, text ->
            val a = bullet.box
            val b = text.box
            if (toPage == null || a == null || b == null) {
                true
            } else {
                val first = turn.apply(toPage(a))
                val next = turn.apply(toPage(b))
                next.left - first.right <= layoutConfig.ocrBulletMaxGapEm * next.height
            }
        }
    }

    /** The page's body text size: the median size of its lines that have one, 0 when none has. */
    private fun bodySizeOf(sizes: List<List<Float>>): Float {
        val known = sizes.flatten().filter { it > 0f }.sorted()
        return if (known.isEmpty()) 0f else known[known.size / 2]
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

    /**
     * Paragraphs of lines of NFC words, [dropped] lines, empty words and note markers ([OcrNoteMarkers]) left out and
     * misread line-end dandas ([OcrDandas]) and bullets ([OcrBullets]) restored, and each line's size
     * (`OcrLine.size`) in the same shape.
     */
    private fun paragraphsOf(
        ocr: OcrText,
        dropped: Set<Int>,
        isBulletGap: (OcrWord, OcrWord) -> Boolean,
    ): Pair<List<List<List<OcrWord>>>, List<List<Float>>> {
        val paragraphs = ArrayList<List<List<OcrWord>>>()
        val sizes = ArrayList<List<Float>>()
        var current = ArrayList<List<OcrWord>>()
        var currentSizes = ArrayList<Float>()
        var paragraph = -1
        val read = ocr.lines.map { line ->
            line.words.mapNotNull { word ->
                val text = Normalizer.normalize(word.text, Normalizer.Form.NFC).trim()
                if (text.isEmpty()) null else OcrWord(text, word.box, word.confidence)
            }
        }
        ocr.lines.forEachIndexed { i, line ->
            if (i in dropped) return@forEachIndexed
            val restored = dandas.restore(
                noteMarkers.silence(read[i], ocr::readingFrame),
                read.getOrNull(i + 1).orEmpty(),
            )
            val words = OcrBullets.restore(isBulletGap, restored)
            if (words.isEmpty()) return@forEachIndexed
            if (line.paragraph != paragraph && current.isNotEmpty()) {
                paragraphs.add(current)
                sizes.add(currentSizes)
                current = ArrayList()
                currentSizes = ArrayList()
            }
            paragraph = line.paragraph
            current.add(words)
            currentSizes.add(line.size)
        }
        if (current.isNotEmpty()) {
            paragraphs.add(current)
            sizes.add(currentSizes)
        }
        return paragraphs to sizes
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

    private companion object {
        const val PARAGRAPH_BREAK = "\n\n"
        const val LINE_BREAK = '\n'
        const val WORD_BREAK = ' '

        /** PDFium's line-end hyphen mark, which the reading-text builder and TextRepair already resolve. */
        const val HYPHEN_MARKER = '\u0002'
        const val MAX_FOLIO_DIGITS = 4
        const val NO_CONFIDENCE = -1

        /** The whole page as displayed, in view fractions. */
        val WHOLE_VIEW = ViewRect(0f, 0f, 1f, 1f)

        /** Not the index of a word, nor one before any (so the first word kept always starts a run). */
        const val NO_WORD = -2

        val FOLIO = Regex("""[\s\-–—.|()\[\]]*\p{Nd}{1,$MAX_FOLIO_DIGITS}[\s\-–—.|()\[\]]*""")
    }
}

/**
 * The turn that sets the page's boxes upright in page space: the image's own turn (`OcrText.quarterTurns`) plus
 * the page's `/Rotate`, read from [toPage] as the way the displayed page's left-to-right runs in page space
 * (right: none; up: a quarter turn; left: a half; down: three quarters).
 */
internal fun OcrText.uprightTurn(toPage: (ViewRect) -> TextBox): TextTurn {
    val from = toPage(PROBE_LEFT)
    val to = toPage(PROBE_RIGHT)
    val dx = to.left - from.left
    val dy = to.bottom - from.bottom
    val rotate = when {
        abs(dx) >= abs(dy) -> if (dx >= 0f) 0 else HALF_TURN
        dy > 0f -> 1
        else -> HALF_TURN + 1
    }
    return TURNS[(quarterTurns + rotate) % OcrText.FULL_TURN]
}

/** The turn that sets a page's boxes upright, by quarter turns (the image's and the page's `/Rotate`). */
private val TURNS = listOf(TextTurn.NONE, TextTurn.CLOCKWISE, TextTurn.HALF, TextTurn.COUNTER_CLOCKWISE)
private const val HALF_TURN = 2

/** Two points across the middle of the displayed page, left then right: where they land tells `/Rotate`. */
private const val PROBE_LEFT_U = 0.25f
private const val PROBE_RIGHT_U = 0.75f
private const val PROBE_V = 0.5f
private val PROBE_LEFT = ViewRect(PROBE_LEFT_U, PROBE_V, PROBE_LEFT_U, PROBE_V)
private val PROBE_RIGHT = ViewRect(PROBE_RIGHT_U, PROBE_V, PROBE_RIGHT_U, PROBE_V)
