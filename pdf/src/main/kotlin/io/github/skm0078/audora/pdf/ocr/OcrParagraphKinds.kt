// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/ocr/OcrParagraphKinds.kt
// Purpose: Splits an OCR paragraph's lines into headings, list items and body text by type size and markers (RI-10).

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.core.config.LayoutConfig
import io.github.skm0078.audora.core.model.TextBox
import io.github.skm0078.audora.pdf.extract.PageText
import io.github.skm0078.audora.pdf.layout.ListMarkers
import io.github.skm0078.audora.pdf.layout.NoteFinder
import io.github.skm0078.audora.pdf.layout.Paragraph
import io.github.skm0078.audora.pdf.layout.ParagraphKind
import io.github.skm0078.audora.pdf.layout.TextLine

/**
 * OCR gives no fonts, so a scanned page read as Tesseract's paragraphs had no headings or list items, and a scanned
 * textbook read like a novel. Here, as on a text layer ([io.github.skm0078.audora.pdf.layout.ParagraphBuilder]):
 *
 * - **Headings.** A line is heading-sized when Tesseract's estimate of its type size (`OcrLine.size`, in
 *   `TextLine.size`) is at least `LayoutConfig.ocrHeadingSizeRatio` times the page's body size (the median line).
 *   A change between heading-sized and body lines starts a paragraph; a paragraph of heading-sized lines within
 *   `maxHeadingLines` and `maxHeadingChars` that reads like one ([looksLikeHeading]) is a heading.
 * - **List items.** A line starting with a bullet, or with a label ("3.", "(क)") after a line that ends a clause or
 *   inside a list, starts an item ([ListMarkers]).
 * - **Asides.** A body paragraph opening with a note label ("Note:", "टिप्पणी:", "দ্রষ্টব্য:") is a
 *   [ParagraphKind.ASIDE], as on a text layer (`NoteFinder`, RI-5a), up to its short last line ([splitAtNoteEnd]).
 *
 * [page] holds the text and OCR confidences the lines' spans point into; [bodySize] 0 (no sizes, plain OCR text)
 * finds no headings.
 */
internal class OcrParagraphKinds(
    private val page: PageText,
    private val config: LayoutConfig,
    private val bodySize: Float,
) {
    private val text = page.text

    fun paragraphs(lines: List<TextLine>): List<Paragraph> {
        val groups = ArrayList<MutableList<TextLine>>()
        val items = ArrayList<Boolean>()
        val labels = ArrayList<String>()
        val labelsSoFar = ArrayList<List<String>>()
        lines.forEachIndexed { i, line ->
            val previous = lines.getOrNull(i - 1)
            val starts = previous == null ||
                isHeadingSized(previous) != isHeadingSized(line) ||
                startsItem(previous, line, inList = items.last(), labels)
            if (starts) {
                groups.add(ArrayList())
                items.add(ListMarkers.opensItem(text, line, labels))
                if (items.last()) ListMarkers.labelOf(text, line)?.let(labels::add)
                labelsSoFar.add(labels.toList())
            }
            groups.last().add(line)
        }
        // A paragraph split off after a note's end opens an item only in sequence too.
        return groups.indices.flatMap { g ->
            splitAtNoteEnd(groups[g]).mapIndexed { k, piece ->
                val item = if (k == 0) items[g] else ListMarkers.opensItem(text, piece.first(), labelsSoFar[g])
                Paragraph(piece, kindOf(piece, item))
            }
        }
    }

    /**
     * Tesseract often merges a "Note:" paragraph with the paragraph after it, which then read as part of the aside.
     * The note ends at its first line that ends a sentence well short of the paragraph's widest line
     * (`NoteConfig.ocrNoteEndShare`); the lines after it are a paragraph of their own.
     */
    private fun splitAtNoteEnd(lines: List<TextLine>): List<List<TextLine>> {
        val end = if (NoteFinder.opensWithNoteLabel(lineText(lines.first()))) noteEnd(lines) else null
        return if (end == null) listOf(lines) else listOf(lines.subList(0, end + 1), lines.subList(end + 1, lines.size))
    }

    private fun noteEnd(lines: List<TextLine>): Int? {
        val boxes = lines.mapNotNull(::extent).takeIf { it.size == lines.size } ?: return null
        val left = boxes.minOf { it.left }
        val reach = (boxes.maxOf { it.right } - left) * config.notes.ocrNoteEndShare
        return (0 until lines.lastIndex).firstOrNull { i ->
            val last = lineText(lines[i]).trimEnd { it in CLOSERS }.lastOrNull()
            last != null && last in SENTENCE_ENDS + EXCLAIMS + OCR_DANDAS && boxes[i].right - left < reach
        }
    }

    /** The box around [line]'s words, from [page]'s word boxes (upright); null when OCR gave none. */
    private fun extent(line: TextLine): TextBox? =
        line.spans.flatMap { span -> (span.start until span.end).mapNotNull(page::boxAt) }.reduceOrNull(TextBox::union)

    /** As on a text layer (`ParagraphBuilder`): a bullet, or a label in sequence inside a list or after a clause. */
    private fun startsItem(previous: TextLine, line: TextLine, inList: Boolean, labels: List<String>): Boolean =
        ListMarkers.opensItem(text, line, labels) && (
            ListMarkers.startsWithBullet(text, line.spans.first().start) || inList ||
                ListMarkers.endsClause(text, previous.spans.last().end)
            )

    /** [item]: the paragraph opens with a bullet or a label in sequence ([ListMarkers.opensItem]). */
    private fun kindOf(lines: List<TextLine>, item: Boolean): ParagraphKind {
        val chars = lines.sumOf { line -> line.spans.sumOf { it.end - it.start } }
        val heading = lines.size <= config.maxHeadingLines &&
            chars <= config.maxHeadingChars &&
            lines.all(::isHeadingSized) &&
            looksLikeHeading(lines)
        return when {
            heading -> ParagraphKind.HEADING
            item -> ParagraphKind.LIST_ITEM
            NoteFinder.opensWithNoteLabel(lineText(lines.first())) -> ParagraphKind.ASIDE
            else -> ParagraphKind.BODY
        }
    }

    /**
     * Read with confidence (`LayoutConfig.ocrHeadingMinConfidence`), starting with a letter or digit, with no table bar
     * and no sentence end: the large lines that are not headings are screenshots, stamps and noise, read badly,
     * pointers ("> click here") and sentences in a larger type.
     */
    private fun looksLikeHeading(lines: List<TextLine>): Boolean {
        val offsets = lines.flatMap { line -> line.spans.flatMap { it.start until it.end } }
        val letters = offsets.filter { text[it].isLetter() }
        val confidences = letters.mapNotNull(page::ocrConfidenceAt)
        val confident = confidences.isEmpty() || confidences.average() >= config.ocrHeadingMinConfidence
        val first = text[offsets.first()]
        val last = text[offsets.last()]
        val shaped = first.isLetterOrDigit() && last !in SENTENCE_ENDS && offsets.none { text[it] == TABLE_BAR }
        return letters.isNotEmpty() && confident && shaped
    }

    private fun lineText(line: TextLine): String = line.spans.joinToString(" ") { text.substring(it.start, it.end) }

    private fun isHeadingSized(line: TextLine): Boolean =
        bodySize > 0f && line.size >= bodySize * config.ocrHeadingSizeRatio

    private companion object {
        const val TABLE_BAR = '|'
        const val SENTENCE_ENDS = ".।॥"
        const val EXCLAIMS = "?!"

        /** A danda as OCR reads it before the danda repairs run: "है|", or "৷" after a digit or a quote. */
        const val OCR_DANDAS = "|৷"
        const val CLOSERS = "\"'’”)"
    }
}
