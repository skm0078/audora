// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/ocr/OcrFootnotes.kt
// Purpose: Tags the footnotes at the foot of a scanned page's columns from OCR's line sizes and boxes (RI-5a).

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.core.config.NoteConfig
import io.github.skm0078.audora.core.model.TextBox
import io.github.skm0078.audora.pdf.extract.PageText
import io.github.skm0078.audora.pdf.layout.FurnitureFinder
import io.github.skm0078.audora.pdf.layout.ListMarkers
import io.github.skm0078.audora.pdf.layout.NoteMarkers
import io.github.skm0078.audora.pdf.layout.Paragraph
import io.github.skm0078.audora.pdf.layout.ParagraphKind
import io.github.skm0078.audora.pdf.layout.TextLine
import io.github.skm0078.audora.pdf.layout.TextSpan

/**
 * The text-layer rule (`NoteFinder`) on OCR's measures: a run of lines, one after another in reading order, set
 * smaller than the page's body (`OcrLine.size` at most `NoteConfig.footnoteSizeRatio` of it), with no line below them
 * in their column and a full-size line above them at least `footnoteGapEm` of that line's height away, is footnotes.
 * The run opens with a note's number or sign, or carries on a note from the page before and so runs at least
 * `minCarriedNoteWidthShare` of the width of the line above (a stray label does not). A run opening with a caption
 * label ("Figure 4:") is a caption under a figure, not a note.
 *
 * Each OCR paragraph of the run, and each line opening with a marker, starts a [ParagraphKind.FOOTNOTE] holding that
 * note's number: OCR often misreads a raised number ("“To illustrate", "SWe used"), so its paragraph break, from the
 * first line's indent, is the surer sign of a new note. The opening marker itself ("4To", "† Work", "* Equal") is
 * returned to be left out of the reading text, as a text layer's is.
 *
 * Boxes come from [PageText.boxAt] in the upright frame (y up); lines and words without boxes are never notes.
 */
internal class OcrFootnotes(private val config: NoteConfig) {
    /** [paragraphs] with their footnotes tagged, and the opening markers to leave out of the reading text. */
    class Tagged(val paragraphs: List<Paragraph>, val markers: List<TextSpan>)

    /**
     * [paragraphs] in reading order on [page] (boxes upright), [sheet] the whole page in the same frame, [obstacles]
     * the spans of words that are on the page but in no line of [paragraphs] (a table's cells), [bodySize] the page's
     * body size in `OcrLine.size` units.
     */
    fun tag(
        page: PageText,
        sheet: TextBox,
        paragraphs: List<Paragraph>,
        obstacles: Collection<TextSpan>,
        bodySize: Float,
    ): Tagged {
        val placed = paragraphs.flatMap { paragraph ->
            paragraph.lines.map { Placed(it, paragraph.kind, page.boxOf(it.spans)) }
        }
        val foot = Foot(
            page,
            sheet,
            placed,
            placed.mapNotNull { it.box } + obstacles.mapNotNull { page.boxOf(listOf(it)) },
        )
        val notes = if (bodySize <= 0f) {
            emptySet()
        } else {
            smallRuns(placed, bodySize).flatMapTo(HashSet()) { run -> foot.notesOf(run, bodySize).map { it.line } }
        }
        if (notes.isEmpty()) return Tagged(paragraphs, emptyList())
        val markers = notes.associateWith { openingMarker(page.text, it) }
        val silent = markers.values.filterNotNull().sortedBy { it.start }
        return Tagged(split(paragraphs, notes, markers, page.text), silent)
    }

    private class Placed(val line: TextLine, val kind: ParagraphKind, val box: TextBox?)

    /** Runs of consecutive lines in reading order that are small enough to be notes, in body text or list items. */
    private fun smallRuns(placed: List<Placed>, bodySize: Float): List<List<Placed>> {
        val runs = ArrayList<List<Placed>>()
        var run = ArrayList<Placed>()
        for (item in placed) {
            val small = item.kind in NOTE_KINDS && item.box != null &&
                item.line.size > 0f && item.line.size <= bodySize * config.footnoteSizeRatio
            if (small) {
                run.add(item)
            } else if (run.isNotEmpty()) {
                runs.add(run)
                run = ArrayList()
            }
        }
        if (run.isNotEmpty()) runs.add(run)
        return runs
    }

    /** One page's lines ([placed]) and every box a note must have nothing of below it ([blockers]). */
    private inner class Foot(
        private val page: PageText,
        private val sheet: TextBox,
        private val placed: List<Placed>,
        private val blockers: List<TextBox>,
    ) {
        /**
         * The notes of [run]: the whole run, else its first tail that starts `footnoteGapEm` below the line before
         * it, else none. OCR measures a short body line as small at times ("Consider these sentences:" above the
         * notes): a run opening with one such line, narrower than `minCarriedNoteWidthShare` of the notes under the
         * space and with no marker, starts after it.
         */
        fun notesOf(run: List<Placed>, bodySize: Float): List<Placed> {
            val cuts = (1 until run.size).filter { i ->
                standsApart(requireNotNull(run[i - 1].box), requireNotNull(run[i].box))
            }
            val tailFirst = cuts.firstOrNull() == 1 && isShortLead(run)
            val start = when {
                tailFirst && isNotes(run.subList(1, run.size), bodySize, afterCut = true) -> 1
                isNotes(run, bodySize, afterCut = false) -> 0
                else -> cuts.firstOrNull { isNotes(run.subList(it, run.size), bodySize, afterCut = true) }
            }
            return if (start == null) emptyList() else run.subList(start, run.size)
        }

        private fun isShortLead(run: List<Placed>): Boolean {
            val widest = run.drop(1).maxOf { requireNotNull(it.box).width }
            return openingMarker(page.text, run[0].line) == null &&
                requireNotNull(run[0].box).width < widest * config.minCarriedNoteWidthShare
        }

        /**
         * [run] sits at the page's foot with nothing below it in its column, set apart from the full-size line above
         * it ([afterCut]: or from the short line a run was cut after), and opens with a marker or runs wide.
         */
        private fun isNotes(run: List<Placed>, bodySize: Float, afterCut: Boolean): Boolean {
            val boxes = run.map { requireNotNull(it.box) }
            val first = boxes.first()
            val lowest = boxes.minOf { it.bottom }
            val members = run.mapTo(HashSet()) { it.line }
            val above = placed.filter { it.line !in members && it.box != null && it.box.overlapsAny(boxes) }
                .filter { it.box!!.bottom >= first.top }
                .minByOrNull { it.box!!.bottom - first.top }
            val aboveBox = above?.box ?: return false
            val atFoot = lowest - sheet.bottom <= sheet.height * config.ocrFootnoteBandShare &&
                blockers.none { box -> box.top <= lowest && box.overlapsAny(boxes) && box !in boxes }
            val apart = (above.line.size > bodySize * config.footnoteSizeRatio || afterCut) &&
                standsApart(aboveBox, first)
            val opens = openingMarker(page.text, run.first().line) != null
            val wide = opens || first.width >= aboveBox.width * config.minCarriedNoteWidthShare
            return atFoot && apart && wide && readsLikeNotes(run.map { it.line })
        }

        /**
         * Read with confidence (`NoteConfig.ocrFootnoteMinConfidence`) and opening with neither a caption label
         * ("Figure 4:", a caption under a figure) nor a bullet (a list in small type): a page's stamp, print slug or
         * garbled strip reads badly or holds no letters.
         */
        private fun readsLikeNotes(lines: List<TextLine>): Boolean {
            val text = page.text
            val letters = lines.flatMap { line -> line.spans.flatMap { it.start until it.end } }
                .filter { text[it].isLetter() }
            val confidences = letters.mapNotNull(page::ocrConfidenceAt)
            val confident = confidences.isEmpty() || confidences.average() >= config.ocrFootnoteMinConfidence
            val first = lines.first().spans.joinToString(" ") { text.substring(it.start, it.end) }
            return letters.isNotEmpty() && confident &&
                !FurnitureFinder.opensWithCaptionLabel(first) &&
                !ListMarkers.startsWithBullet(text, lines.first().spans.first().start)
        }
    }

    /** [below] starts at least `footnoteGapEm` of the taller line's height under [above]. */
    private fun standsApart(above: TextBox, below: TextBox): Boolean =
        above.bottom - below.top >= maxOf(above.height, below.height) * config.footnoteGapEm

    /**
     * [paragraphs] with each run of [notes] lines split off into footnotes: a new note at each OCR paragraph and at
     * each line opening with a marker. The lines left keep their paragraph's kind.
     */
    private fun split(
        paragraphs: List<Paragraph>,
        notes: Set<TextLine>,
        markers: Map<TextLine, TextSpan?>,
        text: String,
    ): List<Paragraph> {
        val out = ArrayList<Paragraph>()
        for (paragraph in paragraphs) {
            var piece = ArrayList<TextLine>()
            var pieceIsNote = false
            fun emit() {
                if (piece.isEmpty()) return
                out.add(
                    if (pieceIsNote) {
                        val ref = markers[piece.first()]?.let { NoteMarkers.refOf(text.substring(it.start, it.end)) }
                        Paragraph(piece, ParagraphKind.FOOTNOTE, noteRef = ref)
                    } else {
                        paragraph.copy(lines = piece, continuesPrevious = paragraph.continuesPrevious && out.isEmpty())
                    },
                )
                piece = ArrayList()
            }
            for (line in paragraph.lines) {
                val isNote = line in notes
                if (isNote != pieceIsNote || (isNote && markers[line] != null)) emit()
                pieceIsNote = isNote
                piece.add(line)
            }
            emit()
        }
        return out
    }

    /**
     * The marker a note line opens with: a sign ("*", "†", alone or glued to the word), a number of its own followed
     * by a word, or a number glued to a capitalised word ("4To", never "3D" or "2017").
     */
    private fun openingMarker(text: String, line: TextLine): TextSpan? {
        val start = line.spans.first().start
        val marker = OPENING_MARKER.find(text.substring(start, line.spans.last().end))?.groups?.get(1) ?: return null
        val tooLong = marker.value.count(Char::isDigit) > config.maxMarkerDigits
        return if (tooLong) null else TextSpan(start + marker.range.first, start + marker.range.last + 1)
    }

    private companion object {
        /** Footnote lines come out of `OcrParagraphKinds` as body text or list items ("1 Arrays are…" is a label). */
        val NOTE_KINDS = setOf(ParagraphKind.BODY, ParagraphKind.LIST_ITEM)

        private const val SIGNS = "*∗†‡§¶‖#"

        /** A sign run, alone or glued; a number alone before a word; a number glued to a capital and a small letter. */
        val OPENING_MARKER = Regex("""^([$SIGNS]{1,3}(?=\s*\p{L})|\p{Nd}+(?=\s+\p{L})|\p{Nd}+(?=\p{Lu}\p{Ll}))""")
    }
}

private fun TextBox.overlapsAny(boxes: List<TextBox>): Boolean =
    boxes.any { minOf(it.right, right) > maxOf(it.left, left) }

private fun PageText.boxOf(spans: List<TextSpan>): TextBox? =
    spans.flatMap { it.start until it.end }.mapNotNull(::boxAt).reduceOrNull(TextBox::union)
