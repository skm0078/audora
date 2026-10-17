// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/cleanup/ClutterRemover.kt
// Purpose: Drops running headers, footers, page numbers and table interiors from a page's layout (P2-03).

package io.github.skm0078.audora.pdf.cleanup

import io.github.skm0078.audora.core.config.CleanupConfig
import io.github.skm0078.audora.core.config.LayoutConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.pdf.extract.PageText
import io.github.skm0078.audora.pdf.layout.PageLayout
import io.github.skm0078.audora.pdf.layout.Paragraph
import io.github.skm0078.audora.pdf.layout.TextBlock
import io.github.skm0078.audora.pdf.layout.TextLine
import io.github.skm0078.audora.pdf.layout.TextTurn

/**
 * Spec §5 step 5, ADR-020. Works on whole lines of the layout, so the reading text built from the result still
 * maps every character back to the extracted text (ADR-018). Pure: the neighbours come from the caller.
 */
internal class ClutterRemover(config: CleanupConfig, layoutConfig: LayoutConfig) {
    private val margins = MarginClutter(config)
    private val tables = TableFinder(config, layoutConfig.lineOverlapRatio)

    fun clean(page: PageText, layout: PageLayout, profile: PageProfile, neighbours: List<PageProfile>): PageLayout {
        val margin = margins.find(profile, neighbours)
        // Table rows compare glyph boxes with line boxes, so both must be in the layout's (possibly turned) frame.
        val framed = if (layout.turn == TextTurn.NONE) page else page.withBoxes(layout.turn::apply)
        val table = tables.find(framed, layout) - margin.keys
        if (margin.isEmpty() && table.isEmpty()) return layout
        val counts = margin.values.groupingBy { it }.eachCount()
        AudoraLog.d(
            TAG,
            "Cleanup page=${layout.pageIndex} neighbours=${neighbours.size} " +
                MarginReason.entries.joinToString(" ") { "${it.name.lowercase()}=${counts[it] ?: 0}" } +
                " tableLines=${table.size}",
        )
        return without(layout, margin.keys + table)
    }

    /**
     * The layout minus [dropped]. A paragraph that loses every line goes; a block that loses every paragraph goes.
     * A continuation (`Paragraph.continuesPrevious`) stays one only when the paragraph it continued is still
     * there and still ends where it did: a removed table or footer must not glue two unrelated texts together.
     */
    private fun without(layout: PageLayout, dropped: Set<TextLine>): PageLayout {
        val blocks = ArrayList<TextBlock>()
        var previousIntact = false
        for (block in layout.blocks) {
            val paragraphs = ArrayList<Paragraph>()
            for (paragraph in block.paragraphs) {
                val lines = paragraph.lines.filterNot { it in dropped }
                val continues = paragraph.continuesPrevious && previousIntact
                previousIntact = lines.isNotEmpty() && lines.last() == paragraph.lines.last()
                if (lines.isNotEmpty()) paragraphs.add(paragraph.copy(lines = lines, continuesPrevious = continues))
            }
            if (paragraphs.isNotEmpty()) blocks.add(block.copy(paragraphs = paragraphs))
        }
        return layout.copy(blocks = blocks)
    }

    private companion object {
        const val TAG = "Audora.Cleanup"
    }
}
