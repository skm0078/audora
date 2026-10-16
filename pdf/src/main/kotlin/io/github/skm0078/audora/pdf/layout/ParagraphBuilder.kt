// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/layout/ParagraphBuilder.kt
// Purpose: Splits a block's lines into paragraphs (gap, indent, style change) and marks headings.

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
            if (i == 0 || startsParagraph(lines[i - 1], line, blockLeft, pitch)) groups.add(ArrayList())
            groups.last().add(line)
        }
        return groups.map { Paragraph(it, kindOf(it)) }
    }

    private fun startsParagraph(previous: TextLine, line: TextLine, blockLeft: Float, pitch: Float): Boolean {
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
        return styleChanged || farBelow || indented
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
        return if (heading) ParagraphKind.HEADING else ParagraphKind.BODY
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
