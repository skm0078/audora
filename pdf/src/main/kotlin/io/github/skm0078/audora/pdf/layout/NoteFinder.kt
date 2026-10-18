// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/layout/NoteFinder.kt
// Purpose: Silences note markers and tags footnotes and "Note:" asides in a cleaned layout (RI-5a, ADR-035).

package io.github.skm0078.audora.pdf.layout

import io.github.skm0078.audora.core.config.LayoutConfig
import io.github.skm0078.audora.core.config.NoteConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.pdf.extract.PageText

/**
 * Tags, never drops (ADR-035), except the markers themselves, which a reader does not say ("both.¹" is read
 * "both."), the way citation markers go (`CleanupConfig.skipCitationMarkers`).
 *
 * - **Markers** ([NoteMarkers]) of every line but display equations and tables: [PageLayout.noteMarkers].
 * - **Footnotes.** The last lines of a column, set smaller than the body (`NoteConfig.footnoteSizeRatio`) and apart
 *   from the line above (`footnoteGapEm`), with nothing below them. Each line opening with a marker starts a
 *   [ParagraphKind.FOOTNOTE] holding that note's number; lines before the first marker carry on a note from the page
 *   before (no number). Footnotes stay where they are printed, so playback reads them in page order.
 * - **Asides.** A body paragraph opening with a note label ("Note:", "NOTE –", "टिप्पणी:", "দ্রষ্টব্য:") is a
 *   [ParagraphKind.ASIDE].
 *
 * Text-layer pages only: OCR'd and turned pages carry no font sizes to tell a marker by.
 */
internal class NoteFinder(private val layoutConfig: LayoutConfig) {
    private val config: NoteConfig = layoutConfig.notes
    private val markerFinder = NoteMarkers(config)

    fun tag(page: PageText, layout: PageLayout): PageLayout {
        if (layout.mode != LayoutMode.GEOMETRIC || layout.turn != TextTurn.NONE || layout.bodySize <= 0f) return layout
        val styles = GlyphStyles(page, layoutConfig)
        val markers = HashMap<TextLine, List<NoteMarker>>()
        for (block in layout.blocks) {
            for (paragraph in block.paragraphs) {
                if (paragraph.kind.isTable || paragraph.kind == ParagraphKind.EQUATION) continue
                for (line in paragraph.lines) markers[line] = markerFinder.of(page, styles, line)
            }
        }
        val footnotes = footnoteLines(layout) { line -> markers[line].orEmpty().any { it.atLineStart } }
        val blocks = layout.blocks.map { block ->
            block.copy(paragraphs = block.paragraphs.flatMap { tag(page, it, footnotes, markers) })
        }
        val silent = if (config.silenceMarkers) markers.values.flatten().map { it.span } else emptyList()
        val tagged = layout.copy(blocks = withoutStrayContinuations(blocks), noteMarkers = silent.sortedBy { it.start })
        logCounts(tagged, "text layer")
        return tagged
    }

    private fun tag(
        page: PageText,
        paragraph: Paragraph,
        footnotes: Set<TextLine>,
        markers: Map<TextLine, List<NoteMarker>>,
    ): List<Paragraph> {
        val firstNote = paragraph.lines.indexOfFirst { it in footnotes }
        if (firstNote < 0) return listOf(asAside(page, paragraph))
        val out = ArrayList<Paragraph>()
        if (firstNote > 0) out.add(asAside(page, paragraph.copy(lines = paragraph.lines.subList(0, firstNote))))
        var entry = ArrayList<TextLine>()
        var ref: Int? = null
        for (line in paragraph.lines.subList(firstNote, paragraph.lines.size)) {
            val opening = markers[line].orEmpty().firstOrNull { it.atLineStart }
            if (opening != null && entry.isNotEmpty()) {
                out.add(Paragraph(entry, ParagraphKind.FOOTNOTE, noteRef = ref))
                entry = ArrayList()
            }
            if (entry.isEmpty()) ref = opening?.ref
            entry.add(line)
        }
        out.add(Paragraph(entry, ParagraphKind.FOOTNOTE, noteRef = ref))
        return out
    }

    private fun asAside(page: PageText, paragraph: Paragraph): Paragraph {
        if (paragraph.kind != ParagraphKind.BODY) return paragraph
        val first = paragraph.lines.first()
        val text = first.spans.joinToString(" ") { page.text.substring(it.start, it.end) }
        return if (opensWithNoteLabel(text)) paragraph.copy(kind = ParagraphKind.ASIDE) else paragraph
    }

    /**
     * Each block's trailing small lines, when they are the bottom of their column (no line below them) and stand
     * apart from the full-size line above them.
     */
    private fun footnoteLines(layout: PageLayout, opening: (TextLine) -> Boolean): Set<TextLine> {
        val all = layout.blocks.flatMap { block -> block.paragraphs.flatMap { it.lines } }.filter { it.box != null }
        val out = HashSet<TextLine>()
        for (block in layout.blocks) {
            val run = trailingSmallLines(block, layout.bodySize)
            if (run.isNotEmpty() && isFootnoteRun(run, all, layout.bodySize, opening(run.first()))) out.addAll(run)
        }
        return out
    }

    private fun trailingSmallLines(block: TextBlock, bodySize: Float): List<TextLine> {
        val lines = block.paragraphs.flatMap { paragraph -> paragraph.lines.map { it to paragraph.kind } }
        val run = lines.takeLastWhile { (line, kind) ->
            kind in NOTE_KINDS && line.box != null && line.size > 0f && line.size <= bodySize * config.footnoteSizeRatio
        }
        return run.map { it.first }
    }

    /**
     * [opensWithMarker]: the run's first line starts with a note's number. Without one it must carry on a note from
     * the page before, and so run the width of the column (the line above), not be a stray table cell.
     */
    private fun isFootnoteRun(
        run: List<TextLine>,
        all: List<TextLine>,
        bodySize: Float,
        opensWithMarker: Boolean,
    ): Boolean {
        val first = run.first().box!!
        val last = run.last().box!!
        val members = run.toSet()
        val others = all.filter { it !in members && overlapsHorizontally(it, run) }
        val above = others.filter { it.box!!.bottom >= first.top }.minByOrNull { it.box!!.bottom - first.top }
        val nothingBelow = others.none { it.box!!.top <= last.bottom }
        val aboveBox = above?.box ?: return false
        val wide = opensWithMarker || first.width >= aboveBox.width * config.minCarriedNoteWidthShare
        return nothingBelow && wide &&
            above.size > bodySize * config.footnoteSizeRatio &&
            aboveBox.bottom - first.top >= bodySize * config.footnoteGapEm
    }

    private fun overlapsHorizontally(line: TextLine, run: List<TextLine>): Boolean {
        val box = line.box!!
        return run.any { other -> other.box!!.let { minOf(it.right, box.right) > maxOf(it.left, box.left) } }
    }

    /** A paragraph carries on only body text: not a footnote that now ends the column before it. */
    private fun withoutStrayContinuations(blocks: List<TextBlock>): List<TextBlock> {
        var previous: ParagraphKind? = null
        return blocks.map { block ->
            val paragraphs = block.paragraphs.map { paragraph ->
                val stray = paragraph.continuesPrevious &&
                    (previous != ParagraphKind.BODY || paragraph.kind != ParagraphKind.BODY)
                previous = paragraph.kind
                if (stray) paragraph.copy(continuesPrevious = false) else paragraph
            }
            block.copy(paragraphs = paragraphs)
        }
    }

    internal companion object {
        private const val TAG = "Audora.Notes"

        /** Logs how many footnotes, asides and silenced markers [layout] has, if any (counts only, no text). */
        fun logCounts(layout: PageLayout, source: String) {
            val kinds = layout.blocks.flatMap { block -> block.paragraphs.map { it.kind } }
            val footnotes = kinds.count { it == ParagraphKind.FOOTNOTE }
            val asides = kinds.count { it == ParagraphKind.ASIDE }
            if (footnotes + asides + layout.noteMarkers.size == 0) return
            AudoraLog.d(
                TAG,
                "Notes page=${layout.pageIndex} source=$source footnotes=$footnotes asides=$asides " +
                    "markers=${layout.noteMarkers.size}",
            )
        }

        /** [text] opens with a note label ("Note:", "टिप्पणी:", "দ্রষ্টব্য:"): an aside, on a text layer or a scan. */
        fun opensWithNoteLabel(text: String): Boolean = NOTE_LABEL.containsMatchIn(text)

        /** Footnote lines come out of layout as body text or list items ("1 Arrays are…" looks like a label). */
        private val NOTE_KINDS = setOf(ParagraphKind.BODY, ParagraphKind.LIST_ITEM)

        /**
         * "Note:", "NOTES –", "N.B.", "नोट:", "नोट्स:", "टिप्पणी:", "দ্রষ্টব্য:", "টীকা:", "বিঃদ্রঃ" (also written with
         * colons: "बि:द्र:", "বি:দ্র:"). A full stop or dash counts only before a space: "Note-taking", "नोट-बुक",
         * "নোট-বই" and "Notes.txt" are words. "N.B." and "বিঃদ্রঃ" need no mark after them. Spaces include a no-break
         * space, which `\s` matches on Android but not on the JVM.
         */
        private val NOTE_LABEL = Regex(
            "^[\\s\\u00A0]*(?:" +
                "(?:N\\.[\\s\\u00A0]?B\\.|বিঃ[\\s\\u00A0]?দ্রঃ|बि:[\\s\\u00A0]?द्र:|বি:[\\s\\u00A0]?দ্র:)" +
                "(?=[\\s\\u00A0:：,\\-–—]|$)|" +
                "(?:Notes?|NOTES?|नोट्स|नोट|टिप्पणी|टिप्पणियाँ|ध्यान दें|দ্রষ্টব্য|টীকা|নোট)" +
                "(?:[\\s\\u00A0]*\\p{Nd}+)?" +
                "[\\s\\u00A0]*(?:[:：।]|[.\\-–—](?=[\\s\\u00A0]|$))" +
                ")",
        )
    }
}
