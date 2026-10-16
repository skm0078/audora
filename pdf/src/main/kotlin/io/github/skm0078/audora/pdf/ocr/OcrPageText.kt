// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/ocr/OcrPageText.kt
// Purpose: Turns Tesseract's text of one page into a page and layout the normal reading-text builder can repair.

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.pdf.cleanup.hyphenatedPairs
import io.github.skm0078.audora.pdf.extract.ActualTextRebuild
import io.github.skm0078.audora.pdf.extract.PageText
import io.github.skm0078.audora.pdf.extract.PageTextStats
import io.github.skm0078.audora.pdf.extract.RawChars
import io.github.skm0078.audora.pdf.layout.LayoutMode
import io.github.skm0078.audora.pdf.layout.PageLayout
import io.github.skm0078.audora.pdf.layout.Paragraph
import io.github.skm0078.audora.pdf.layout.ParagraphKind
import io.github.skm0078.audora.pdf.layout.TextBlock
import io.github.skm0078.audora.pdf.layout.TextLine
import io.github.skm0078.audora.pdf.layout.TextSpan
import java.text.Normalizer

/** OCR text as a page without glyph boxes plus its layout, and the hyphenated words it prints. */
internal class OcrPage(val text: PageText, val layout: PageLayout, val hyphenated: Set<String>)

/**
 * Tesseract (page segmentation "auto") already puts blocks in reading order, one line per text line and a blank line
 * between paragraphs. That structure becomes a one-block [PageLayout], so the same repair as a text layer applies:
 * line-end hyphens resolved against the page's own hyphenated words (TextRepair), junk characters folded, and the
 * same sentence split. Glyph boxes are not kept (`ReadingUnit.bounds` is null for OCR'd text, Build Spec §4).
 *
 * A paragraph holding only a page number ("12", "- 3 -", "১২") at the top or bottom of the page is dropped, the one
 * piece of clutter that is easy to tell without positions. Running headers stay (P3-05 known limit).
 */
internal object OcrPageText {
    private const val PARAGRAPH_BREAK = "\n\n"
    private const val LINE_BREAK = '\n'

    /** PDFium's line-end hyphen mark, which the reading-text builder and TextRepair already resolve. */
    private const val HYPHEN_MARKER = '\u0002'
    private const val MAX_FOLIO_DIGITS = 4
    private val FOLIO = Regex("""[\s\-–—.|()\[\]]*\p{Nd}{1,$MAX_FOLIO_DIGITS}[\s\-–—.|()\[\]]*""")

    fun build(pageIndex: Int, ocrText: String): OcrPage {
        val paragraphs = paragraphsOf(Normalizer.normalize(ocrText, Normalizer.Form.NFC)).dropFolios()
        val text = StringBuilder()
        val layoutParagraphs = ArrayList<Paragraph>(paragraphs.size)
        for (lines in paragraphs) {
            if (text.isNotEmpty()) text.append(PARAGRAPH_BREAK)
            val spans = ArrayList<TextLine>(lines.size)
            lines.forEachIndexed { i, line ->
                if (i > 0) text.append(LINE_BREAK)
                val start = text.length
                text.append(markLineEndHyphen(line, lines.getOrNull(i + 1)))
                spans.add(TextLine(listOf(TextSpan(start, text.length)), box = null, size = 0f, bold = false))
            }
            layoutParagraphs.add(Paragraph(spans, ParagraphKind.BODY))
        }
        val flat = text.toString()
        val page = PageText(
            pageIndex = pageIndex,
            text = flat,
            boxes = FloatArray(flat.length * RawChars.BOX_FLOATS) { Float.NaN },
            fontRuns = emptyList(),
            stats = PageTextStats(0, 0, 0, 0, 0, ActualTextRebuild.NOT_NEEDED),
        )
        val blocks = if (layoutParagraphs.isEmpty()) emptyList() else listOf(TextBlock(layoutParagraphs, box = null))
        val layout = PageLayout(pageIndex, blocks, bodySize = 0f, mode = LayoutMode.EXTRACTION_ORDER)
        return OcrPage(page, layout, hyphenatedPairs(flat))
    }

    /** Paragraphs of trimmed, non-empty lines; blank lines separate paragraphs. */
    private fun paragraphsOf(text: String): List<List<String>> {
        val paragraphs = ArrayList<List<String>>()
        var current = ArrayList<String>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isNotEmpty()) {
                current.add(line)
            } else if (current.isNotEmpty()) {
                paragraphs.add(current)
                current = ArrayList()
            }
        }
        if (current.isNotEmpty()) paragraphs.add(current)
        return paragraphs
    }

    private fun List<List<String>>.dropFolios(): List<List<String>> {
        if (size < 2) return this
        fun isFolio(paragraph: List<String>) = paragraph.size == 1 && FOLIO.matches(paragraph[0])
        val start = if (isFolio(first())) 1 else 0
        val end = if (isFolio(last())) size - 1 else size
        return subList(start, maxOf(start, end))
    }

    /**
     * "nega-" followed by a line starting with a lower-case letter is a word broken at the line end: its hyphen
     * becomes PDFium's marker, so it is joined unless the page prints the pair with a hyphen elsewhere.
     */
    private fun markLineEndHyphen(line: String, next: String?): String {
        val brokenWord = next != null && line.length > 1 && line.last() == '-' &&
            line[line.length - 2].isLetter() && next.first().isLowerCase()
        return if (brokenWord) line.dropLast(1) + HYPHEN_MARKER else line
    }
}
