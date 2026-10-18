// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/layout/ParagraphBuilder.kt
// Purpose: Splits a block's lines into paragraphs (gap, indent, style change, list item) and marks headings.

package io.github.skm0078.audora.pdf.layout

import io.github.skm0078.audora.core.config.LayoutConfig
import io.github.skm0078.audora.pdf.extract.PageText
import kotlin.math.abs

internal class ParagraphBuilder(
    private val page: PageText,
    private val config: LayoutConfig,
    private val bodySize: Float,
    private val em: Float,
) {
    /** [lines] top to bottom, all with boxes. */
    fun of(lines: List<TextLine>): List<Paragraph> {
        if (lines.isEmpty()) return emptyList()
        val blockLeft = lines.minOf { requireNotNull(it.box).left }
        val pitch = usualPitch(lines)
        val groups = ArrayList<MutableList<TextLine>>()
        lines.forEachIndexed { i, line ->
            val inList = i > 0 && startsWithMarker(groups.last().first())
            val starts = i == 0 ||
                startsParagraph(lines[i - 1], line, blockLeft, pitch, inList) ||
                startsItem(lines[i - 1], line, groups.lastOrNull()?.first()?.takeIf { inList }) ||
                (inList && outdented(lines[i - 1], line))
            if (starts) groups.add(ArrayList())
            groups.last().add(line)
        }
        return groups.map { Paragraph(it, kindOf(it)) }
    }

    /** [inList]: the paragraph so far is a list item, whose indented continuation lines are not first-line indents. */
    private fun startsParagraph(
        previous: TextLine,
        line: TextLine,
        blockLeft: Float,
        pitch: Float,
        inList: Boolean,
    ): Boolean {
        val above = requireNotNull(previous.box)
        val box = requireNotNull(line.box)
        val lineEm = if (line.size > 0f) line.size else em
        val styleChanged = isHeadingStyled(previous) != isHeadingStyled(line) ||
            !sameSize(previous.size, line.size, config)
        // A line's own size sets a floor, so a large title's wide line spacing is not read as a paragraph gap.
        val expectedPitch = maxOf(pitch, line.size * config.defaultLineSpacing)
        val farBelow = above.top - box.top > expectedPitch * config.paragraphPitchRatio
        val indented = box.left - blockLeft >= config.indentEm * lineEm &&
            above.left - blockLeft < config.indentEm * lineEm
        return styleChanged || farBelow || (indented && !inList)
    }

    /**
     * Each list item is a paragraph of its own, so it gets its own sentences and pause: "• Apples" and "• Pears" are
     * not read as one sentence. A label ("3.") counts only after a line that ends a clause, or inside a list
     * ([item]: the first line of the item so far), never in the middle of running prose, and a lone small letter
     * with a full stop only in sequence ([ListMarkers.inSequence]).
     */
    private fun startsItem(previous: TextLine, line: TextLine, item: TextLine?): Boolean {
        val start = line.spans.first().start
        val label = ListMarkers.labelAt(page.text, start, line.spans.last().end)
        val before = item?.let { ListMarkers.labelAt(page.text, it.spans.first().start, it.spans.last().end) }
        return ListMarkers.startsWithBullet(page.text, start) ||
            (
                label != null && ListMarkers.inSequence(label, before) &&
                    (item != null || ListMarkers.endsClause(page.text, previous.spans.last().end))
                )
    }

    /**
     * Back left of an item's indented continuation lines: the list has ended and body text carries on ("• Pears and
     * /   plums / Then we leave.").
     */
    private fun outdented(previous: TextLine, line: TextLine): Boolean {
        val lineEm = if (line.size > 0f) line.size else em
        return requireNotNull(previous.box).left - requireNotNull(line.box).left >= config.indentEm * lineEm
    }

    private fun startsWithMarker(line: TextLine): Boolean {
        val start = line.spans.first().start
        return ListMarkers.startsWithBullet(page.text, start) ||
            ListMarkers.startsWithLabel(page.text, start, line.spans.last().end)
    }

    /** Median top-to-top distance between consecutive lines of the same size; a default when too few. */
    private fun usualPitch(lines: List<TextLine>): Float {
        val pitches = lines.zipWithNext()
            .filter { (a, b) -> sameSize(a.size, b.size, config) }
            .map { (a, b) -> requireNotNull(a.box).top - requireNotNull(b.box).top }
            .filter { it > 0f }
            .sorted()
        return if (pitches.size >= MIN_PITCHES) pitches[pitches.size / 2] else em * config.defaultLineSpacing
    }

    private fun kindOf(lines: List<TextLine>): ParagraphKind {
        val chars = lines.sumOf { line -> line.spans.sumOf { it.end - it.start } }
        val heading = lines.size <= config.maxHeadingLines &&
            chars <= config.maxHeadingChars &&
            lines.all(::isHeadingStyled) &&
            lines.any { line ->
                line.spans.any { span -> (span.start until span.end).any { page.text[it].isLetter() } }
            }
        return when {
            heading -> ParagraphKind.HEADING
            startsWithMarker(lines.first()) -> ParagraphKind.LIST_ITEM
            else -> ParagraphKind.BODY
        }
    }

    private fun isHeadingStyled(line: TextLine): Boolean =
        line.bold || (bodySize > 0f && line.size >= bodySize * config.headingSizeRatio)

    private companion object {
        /** Fewer measured pitches than this are not enough for a median. */
        const val MIN_PITCHES = 2
    }
}

/** Sizes within [LayoutConfig.sizeToleranceRatio] of each other are the same size. */
internal fun sameSize(a: Float, b: Float, config: LayoutConfig): Boolean =
    abs(a - b) <= maxOf(a, b) * config.sizeToleranceRatio
