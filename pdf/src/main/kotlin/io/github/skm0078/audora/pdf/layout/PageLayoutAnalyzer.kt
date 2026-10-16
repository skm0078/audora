// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/layout/PageLayoutAnalyzer.kt
// Purpose: Rebuilds a page's layout from glyph boxes: runs → XY-cut blocks → lines → paragraphs and headings.

package io.github.skm0078.audora.pdf.layout

import io.github.skm0078.audora.core.config.LayoutConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.TextBox
import io.github.skm0078.audora.pdf.extract.PageText

/**
 * Spec §5 step 4. Pure and stateless: one call per page, nothing kept between pages.
 *
 * Headings are found by relative font size and a bold font name, never by font weight (ADR-011 §1).
 */
class PageLayoutAnalyzer(private val config: LayoutConfig = LayoutConfig()) {
    private val runSplitter = GlyphRunSplitter(config)
    private val xyCut = XyCut(config)
    private val lineBuilder = LineBuilder(config)

    fun analyze(page: PageText): PageLayout {
        val styles = GlyphStyles(page, config)
        val split = runSplitter.split(page, styles)
        return when {
            split.glyphs == 0 -> PageLayout(page.pageIndex, emptyList(), styles.bodySize, LayoutMode.GEOMETRIC)
            !hasUsableGeometry(page, split) -> extractionOrder(page, styles)
            else -> geometric(page, styles, split.runs)
        }
    }

    /** Enough glyph boxes, and enough of them in runs (not one glyph per run, as vertical text gives). */
    private fun hasUsableGeometry(page: PageText, split: GlyphRunSplitter.Result): Boolean {
        val boxedShare = split.boxedGlyphs.toFloat() / split.glyphs
        val inRuns = split.runs.filter { it.boxedGlyphs > 1 }.sumOf { it.boxedGlyphs }
        val runShare = inRuns.toFloat() / maxOf(split.boxedGlyphs, 1)
        val usable = boxedShare >= config.minBoxedShare && runShare >= config.minRunShare
        if (!usable) AudoraLog.d(TAG, "Extraction order kept, page=${page.pageIndex} boxed=$boxedShare runs=$runShare")
        return usable
    }

    private fun geometric(page: PageText, styles: GlyphStyles, runs: List<GlyphRun>): PageLayout {
        val em = if (styles.bodySize > 0f) styles.bodySize else medianHeight(runs)
        val paragraphs = ParagraphBuilder(page, config, styles.bodySize, em)
        val blocks = xyCut.blocks(runs, em).map { blockRuns ->
            val lines = lineBuilder.linesOf(blockRuns, styles)
            TextBlock(paragraphs.of(lines), lines.mapNotNull { it.box }.reduceOrNull(TextBox::union))
        }
        return PageLayout(page.pageIndex, markContinuations(page, blocks), styles.bodySize, LayoutMode.GEOMETRIC)
    }

    /**
     * A block's first paragraph carries on the previous block's last one when the block starts a new column (it
     * begins above the previous block's bottom), neither is a heading, both are the same size and the earlier one
     * stops mid-sentence. Blocks stacked in one column were already joined (XyCut), so a block below its
     * predecessor is separated by a real gap and starts a paragraph.
     */
    private fun markContinuations(page: PageText, blocks: List<TextBlock>): List<TextBlock> {
        var previous: TextBlock? = null
        return blocks.map { block ->
            val before = previous
            previous = block
            if (before != null && continues(page, before, block)) {
                val first = block.paragraphs.first().copy(continuesPrevious = true)
                block.copy(paragraphs = listOf(first) + block.paragraphs.drop(1))
            } else {
                block
            }
        }
    }

    private fun continues(page: PageText, before: TextBlock, block: TextBlock): Boolean {
        val last = before.paragraphs.last()
        val first = block.paragraphs.first()
        val beforeBox = before.box
        val box = block.box
        return beforeBox != null &&
            box != null &&
            box.top > beforeBox.bottom &&
            last.kind != ParagraphKind.HEADING &&
            first.kind == ParagraphKind.BODY &&
            sameSize(last.lines.last().size, first.lines.first().size, config) &&
            !endsSentence(page.text, last)
    }

    private fun endsSentence(text: String, paragraph: Paragraph): Boolean {
        val end = paragraph.lines.last().spans.last().end
        var i = end - 1
        while (i >= 0 && (text[i].isWhitespace() || text[i] in CLOSERS)) i--
        return i >= 0 && text[i] in TERMINATORS
    }

    /** OCR'd, converted or vertical text: PDFium's lines in extraction order, all one paragraph. */
    private fun extractionOrder(page: PageText, styles: GlyphStyles): PageLayout {
        val lines = ArrayList<TextLine>()
        var lineStart = 0
        val text = page.text
        for (i in 0..text.length) {
            if (i < text.length && text[i] != '\n' && text[i] != '\r') continue
            trimmed(text, lineStart, i)?.let { lines.add(TextLine(listOf(it), box = null, size = 0f, bold = false)) }
            lineStart = i + 1
        }
        val blocks = if (lines.isEmpty()) {
            emptyList()
        } else {
            listOf(
                TextBlock(listOf(Paragraph(lines, ParagraphKind.BODY)), null),
            )
        }
        return PageLayout(page.pageIndex, blocks, styles.bodySize, LayoutMode.EXTRACTION_ORDER)
    }

    private fun trimmed(text: String, start: Int, end: Int): TextSpan? {
        var s = start
        var e = end
        while (s < e && text[s].isWhitespace()) s++
        while (e > s && text[e - 1].isWhitespace()) e--
        return if (s < e) TextSpan(s, e) else null
    }

    private fun medianHeight(runs: List<GlyphRun>): Float = runs.map { it.box.height }.sorted()[runs.size / 2]

    private companion object {
        const val TAG = "Audora.Layout"

        /** Sentence-final marks, Latin and Indic (danda, double danda). */
        const val TERMINATORS = ".!?…।॥"

        /** Skipped when looking for a sentence end: "…he said.”" ends a sentence. */
        const val CLOSERS = "\"'”’»)]"
    }
}
