// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/ocr/OcrPageText.kt
// Purpose: Turns OCR'd words of one page into a page with boxes and a layout the reading-text builder can repair.

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.core.model.TextBox
import io.github.skm0078.audora.pdf.cleanup.hyphenatedPairs
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
import io.github.skm0078.audora.pdf.render.ViewRect
import java.text.Normalizer

/** OCR text as a page with word boxes (when known) plus its layout, and the hyphenated words it prints. */
internal class OcrPage(val text: PageText, val layout: PageLayout, val hyphenated: Set<String>)

/**
 * Tesseract (page segmentation "auto") already puts blocks in reading order, one line per text line, in paragraphs.
 * That structure becomes a one-block [PageLayout], so the same repair as a text layer applies: line-end hyphens
 * resolved against the page's own hyphenated words (TextRepair), junk characters folded, and the same sentence split.
 *
 * Every character of a word gets the word's box, mapped onto the page by `toPage`, so a sentence read from a scan
 * is highlighted line by line like any other (ADR-032); without boxes (`toPage` null, or an engine that gives none)
 * `ReadingUnit.bounds` stays null. Lines in `dropped` (running headers and page numbers, [OcrClutter]) are left out,
 * and a paragraph holding only a page number ("12", "- 3 -", "১২") at the top or bottom of the page is dropped too,
 * which also covers text without positions.
 */
internal object OcrPageText {
    private const val PARAGRAPH_BREAK = "\n\n"
    private const val LINE_BREAK = '\n'
    private const val WORD_BREAK = ' '

    /** PDFium's line-end hyphen mark, which the reading-text builder and TextRepair already resolve. */
    private const val HYPHEN_MARKER = '\u0002'
    private const val MAX_FOLIO_DIGITS = 4
    private const val NO_CONFIDENCE = -1
    private val FOLIO = Regex("""[\s\-–—.|()\[\]]*\p{Nd}{1,$MAX_FOLIO_DIGITS}[\s\-–—.|()\[\]]*""")

    fun build(
        pageIndex: Int,
        ocr: OcrText,
        dropped: Set<Int> = emptySet(),
        toPage: ((ViewRect) -> TextBox)? = null,
    ): OcrPage {
        val paragraphs = paragraphsOf(ocr, dropped).dropFolios()
        val text = StringBuilder()
        val boxes = ArrayList<TextBox?>()
        val confidences = ArrayList<Byte>()
        val layoutParagraphs = ArrayList<Paragraph>(paragraphs.size)
        fun append(chars: String, box: TextBox?, confidence: Int? = null) {
            text.append(chars)
            repeat(chars.length) {
                boxes.add(box)
                confidences.add((confidence ?: NO_CONFIDENCE).toByte())
            }
        }
        for (lines in paragraphs) {
            if (text.isNotEmpty()) append(PARAGRAPH_BREAK, null)
            val spans = ArrayList<TextLine>(lines.size)
            lines.forEachIndexed { i, words ->
                if (i > 0) append(LINE_BREAK.toString(), null)
                val start = text.length
                val brokenWord = endsInBrokenWord(words, lines.getOrNull(i + 1))
                words.forEachIndexed { w, word ->
                    if (w > 0) append(WORD_BREAK.toString(), null)
                    val box = word.box?.let { rect -> toPage?.invoke(rect) }
                    val chars = if (brokenWord &&
                        w == words.lastIndex
                    ) {
                        word.text.dropLast(1) + HYPHEN_MARKER
                    } else {
                        word.text
                    }
                    append(chars, box, word.confidence)
                }
                spans.add(TextLine(listOf(TextSpan(start, text.length)), box = null, size = 0f, bold = false))
            }
            layoutParagraphs.add(Paragraph(spans, ParagraphKind.BODY))
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
        val blocks = if (layoutParagraphs.isEmpty()) emptyList() else listOf(TextBlock(layoutParagraphs, box = null))
        val layout = PageLayout(pageIndex, blocks, bodySize = 0f, mode = LayoutMode.EXTRACTION_ORDER)
        return OcrPage(page, layout, hyphenatedPairs(flat))
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

    private fun List<List<List<OcrWord>>>.dropFolios(): List<List<List<OcrWord>>> {
        if (size < 2) return this
        fun isFolio(paragraph: List<List<OcrWord>>) =
            paragraph.size == 1 && FOLIO.matches(paragraph[0].joinToString(" ") { it.text })
        val start = if (isFolio(first())) 1 else 0
        val end = if (isFolio(last())) size - 1 else size
        return subList(start, maxOf(start, end))
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
