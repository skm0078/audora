// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/cleanup/ClutterRemover.kt
// Purpose: Drops running headers, footers and page numbers from a page's layout and rebuilds its tables (P2-03, RI-4a).

package io.github.skm0078.audora.pdf.cleanup

import io.github.skm0078.audora.core.config.CleanupConfig
import io.github.skm0078.audora.core.config.LayoutConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.TextBox
import io.github.skm0078.audora.pdf.extract.PageText
import io.github.skm0078.audora.pdf.layout.PageLayout
import io.github.skm0078.audora.pdf.layout.Paragraph
import io.github.skm0078.audora.pdf.layout.ParagraphKind
import io.github.skm0078.audora.pdf.layout.TableCells
import io.github.skm0078.audora.pdf.layout.TextBlock
import io.github.skm0078.audora.pdf.layout.TextLine
import io.github.skm0078.audora.pdf.layout.TextSpan
import io.github.skm0078.audora.pdf.layout.TextTurn

/**
 * Spec §5 step 5, ADR-020. Works on whole lines of the layout, so the reading text built from the result still
 * maps every character back to the extracted text (ADR-018). Pure: the neighbours come from the caller.
 *
 * A table ([TableFinder]) is kept as a header paragraph and one paragraph per data row ([TableGrid], RI-4a), put
 * where its first line was; each holds one line of its cells' spans, cell after cell. A table that does not make a
 * grid is dropped as before.
 */
internal class ClutterRemover(config: CleanupConfig, layoutConfig: LayoutConfig) {
    private val margins = MarginClutter(config)
    private val tables = TableFinder(config, layoutConfig.lineOverlapRatio)
    private val grids = TableGrid(config.tables, layoutConfig.lineOverlapRatio)

    fun clean(page: PageText, layout: PageLayout, profile: PageProfile, neighbours: List<PageProfile>): PageLayout {
        val margin = margins.find(profile, neighbours)
        // Table rows compare glyph boxes with line boxes, so both must be in the layout's (possibly turned) frame.
        val framed = if (layout.turn == TextTurn.NONE) page else page.withBoxes(layout.turn::apply)
        val found = tables.find(framed, layout).map { it - margin.keys }.filter { it.isNotEmpty() }
        if (margin.isEmpty() && found.isEmpty()) return layout
        val built = found.map { region -> region to grids.build(framed, region, layout.bodySize) }
        val counts = margin.values.groupingBy { it }.eachCount()
        AudoraLog.d(
            TAG,
            "Cleanup page=${layout.pageIndex} neighbours=${neighbours.size} " +
                MarginReason.entries.joinToString(" ") { "${it.name.lowercase()}=${counts[it] ?: 0}" } +
                " tables=${built.sumOf { it.second.size }} sideBySide=${built.count { it.second.size > 1 }}" +
                " droppedTableLines=" +
                built.filter { it.second.isEmpty() }.sumOf { it.first.size },
        )
        // Two tables side by side share their first line: both go there, left first.
        val inserted = HashMap<TextLine, MutableList<Paragraph>>()
        var tableIndex = 0
        for (grid in built.flatMap { it.second }) {
            val first = layout.blocks.asSequence().flatMap { it.paragraphs }.flatMap { it.lines }.first {
                it in
                    grid.lines
            }
            inserted.getOrPut(first) { ArrayList() }.addAll(paragraphsOf(grid, tableIndex++))
        }
        // A grid leaves a title across the table's edge to the text; a region that makes no grid goes whole.
        val tableLines = built.flatMap { (region, grids) -> grids.flatMap { it.lines }.ifEmpty { region } }
        return without(layout, margin.keys + tableLines, inserted)
    }

    /** The table as paragraphs ([tableParagraphs]) in the frame of its lines. */
    private fun paragraphsOf(grid: Grid, tableIndex: Int): List<Paragraph> =
        tableParagraphs(grid.header, grid.rows, tableIndex, grid.lines.unionBox(), grid.lines.maxOf { it.size })

    /**
     * The layout minus [dropped], with [inserted] paragraphs put right before their key line: a paragraph holding key
     * lines is split there, so an intro line set close above a table ("The rates are as follows:") is read before it
     * and a line after it starts a new paragraph, and every table keyed in one paragraph is read. A paragraph that
     * loses every line goes; a block that loses every paragraph goes. A continuation (`Paragraph.continuesPrevious`)
     * stays one only when the paragraph it continued is still there and still ends where it did: a removed table or
     * footer must not glue two unrelated texts together.
     */
    private fun without(
        layout: PageLayout,
        dropped: Set<TextLine>,
        inserted: Map<TextLine, List<Paragraph>>,
    ): PageLayout {
        val blocks = ArrayList<TextBlock>()
        var previousIntact = false
        for (block in layout.blocks) {
            val paragraphs = ArrayList<Paragraph>()
            for (paragraph in block.paragraphs) {
                previousIntact = split(paragraph, dropped, inserted, previousIntact, paragraphs)
            }
            if (paragraphs.isNotEmpty()) blocks.add(block.copy(paragraphs = paragraphs))
        }
        return layout.copy(blocks = blocks)
    }

    /**
     * [paragraph]'s kept lines, split before each key line of [inserted] with its paragraphs put there, added to
     * [out]. Returns whether the paragraph still ends where it did, with nothing inserted after its last kept line.
     */
    private fun split(
        paragraph: Paragraph,
        dropped: Set<TextLine>,
        inserted: Map<TextLine, List<Paragraph>>,
        previousIntact: Boolean,
        out: MutableList<Paragraph>,
    ): Boolean {
        val kept = ArrayList<TextLine>()
        var continues = paragraph.continuesPrevious && previousIntact
        fun emit() {
            if (kept.isNotEmpty()) out.add(paragraph.copy(lines = kept.toList(), continuesPrevious = continues))
            kept.clear()
            continues = false
        }
        for (line in paragraph.lines) {
            inserted[line]?.let { table ->
                emit()
                continues = false
                out.addAll(table)
            }
            if (line !in dropped) kept.add(line)
        }
        val intact = kept.isNotEmpty() && kept.last() == paragraph.lines.last()
        emit()
        return intact
    }

    private companion object {
        const val TAG = "Audora.Cleanup"
    }
}
