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
 * in their column and a full-size line above them at least `footnoteGapEm` of that line's height away, is footnotes,
 * when it also looks placed like notes:
 *
 * - it opens with a note's number or sign; else it carries on a note from the page before, so it runs at least
 *   `minCarriedNoteWidthShare` of the width of the line above (a stray label does not) and starts flush with its
 *   column or under a rule (a block quote, verse or epigraph set small is indented or centred);
 * - a run shorter than `ocrSureNoteLines` shows a second sign, as one OCR size is a rough measure: it opens with a
 *   note sign ("*", "†"), a rule stands above it, or its lines are closer together than the body's
 *   (`ocrNoteSpacingRatio`); a body line OCR measured a little small and starting with a number ("15 अगस्त…") is not;
 * - it reads like text (`readsLikeNotes`): read with the page's confidence, opening with neither a caption label
 *   ("Figure 4:"), a source line ("Source:") nor a bullet.
 *
 * Each OCR paragraph of the run starts a [ParagraphKind.FOOTNOTE], and so does a line opening with a marker that
 * continues the notes' numbering (note 3 after note 2): OCR often misreads a raised number ("“To illustrate"), so the
 * paragraph break is the surer sign of a new note, and a line that only starts with a number ("12 pages…", "11 SCC
 * 345") carries on the note above. The markers that open notes ("4To", "† Work", "* Equal") are returned to be left
 * out of the reading text, as a text layer's are; no other number is silenced.
 *
 * Boxes come from [PageText.boxAt] in the upright frame (y up); lines and words without boxes are never notes.
 */
internal class OcrFootnotes(private val config: NoteConfig) {
    /** [paragraphs] with their footnotes tagged, and the opening markers to leave out of the reading text. */
    class Tagged(val paragraphs: List<Paragraph>, val markers: List<TextSpan>)

    /** The whole page ([box]) and the lines ruled on it ([rules], `OcrText.rules`), in the upright frame. */
    class Sheet(val box: TextBox, val rules: List<TextBox>)

    /**
     * [paragraphs] in reading order on [page] (boxes upright), [obstacles] the spans of words that are on the page but
     * in no line of [paragraphs] (a table's cells), [bodySize] the page's body size in `OcrLine.size` units.
     */
    fun tag(
        page: PageText,
        sheet: Sheet,
        paragraphs: List<Paragraph>,
        obstacles: Collection<TextSpan>,
        bodySize: Float,
    ): Tagged {
        val placed = paragraphs.flatMapIndexed { p, paragraph ->
            paragraph.lines.mapIndexed { i, line -> Placed(line, paragraph.kind, page.boxOf(line.spans), p, i == 0) }
        }
        val blockers = placed.mapNotNull { it.box } + obstacles.mapNotNull { page.boxOf(listOf(it)) }
        val noteRuns = if (bodySize <= 0f) {
            emptyList()
        } else {
            val foot = Foot(page, sheet, placed, blockers, bodySize)
            smallRuns(placed, bodySize).map(foot::notesOf).filter { it.isNotEmpty() }
        }
        if (noteRuns.isEmpty()) return Tagged(paragraphs, emptyList())
        val notes = noteRuns.flatMapTo(HashSet()) { run -> run.map { it.line } }
        val cited = citedMarkers(page.text, placed.map { it.line }.filter { it !in notes })
        val markers = openingMarkers(page.text, noteRuns, cited)
        return Tagged(
            split(paragraphs, notes, markers, page.text),
            markers.values.filterNotNull().sortedBy {
                it.start
            },
        )
    }

    /** A line with its paragraph's kind, its box, its paragraph's place and whether it opens that paragraph. */
    private class Placed(
        val line: TextLine,
        val kind: ParagraphKind,
        val box: TextBox?,
        val paragraph: Int,
        val opensParagraph: Boolean,
    )

    /** Runs of consecutive lines in reading order that are small enough to be notes, in body text or list items. */
    private fun smallRuns(placed: List<Placed>, bodySize: Float): List<List<Placed>> {
        val runs = ArrayList<List<Placed>>()
        var run = ArrayList<Placed>()
        for (item in placed) {
            if (item.kind in NOTE_KINDS && item.box != null && isSmall(item, bodySize)) {
                run.add(item)
            } else if (run.isNotEmpty()) {
                runs.add(run)
                run = ArrayList()
            }
        }
        if (run.isNotEmpty()) runs.add(run)
        return runs
    }

    private fun isSmall(item: Placed, bodySize: Float): Boolean =
        item.line.size > 0f && item.line.size <= bodySize * config.footnoteSizeRatio

    /**
     * Each note line's opening marker, when it opens a note: a line opening an OCR paragraph, a line whose marker
     * continues the numbering of the last note (3 after 2, "†" after "*"), or the first line of a run whose marker the
     * body text cites ([cited]); null for the rest, whose numbers are read. A run's first line may carry on a note
     * from the page before: "12 pages…", "11 SCC 345" or "१२ पृष्ठ" there is text, not note 12.
     */
    private fun openingMarkers(
        text: String,
        noteRuns: List<List<Placed>>,
        cited: Set<String>,
    ): Map<TextLine, TextSpan?> {
        val out = HashMap<TextLine, TextSpan?>()
        var lastRef: Int? = null
        for (run in noteRuns) {
            run.forEachIndexed { i, note ->
                val marker = openingMarker(text, note.line)
                val ref = marker?.let { NoteMarkers.refOf(text.substring(it.start, it.end)) }
                val continues = ref != null && lastRef != null && ref == lastRef + 1
                val citedFirst = i == 0 && marker != null && text.substring(marker.start, marker.end).trim() in cited
                val accepted = marker != null && (citedFirst || note.opensParagraph || continues)
                out[note.line] = marker.takeIf { accepted }
                if (accepted && ref != null) lastRef = ref
            }
        }
        return out
    }

    /**
     * One page's lines ([placed]), every box a note must have nothing of below it ([blockers]) and the page's body
     * size and line spacing.
     */
    private inner class Foot(
        private val page: PageText,
        private val sheet: Sheet,
        private val placed: List<Placed>,
        private val blockers: List<TextBox>,
        private val bodySize: Float,
    ) {
        /** How far apart the body's lines follow each other within a paragraph (median); null when not known. */
        private val bodyPitch: Float? = placed.filter { it.box != null && !isSmall(it, bodySize) }
            .zipWithNext()
            .filter { (a, b) -> a.paragraph == b.paragraph }
            .map { (a, b) -> requireNotNull(a.box).top - requireNotNull(b.box).top }
            .filter { it > 0f }
            .let(::median)

        /**
         * The page's own mean letter confidence less the allowed drop, at most `ocrFootnoteMinConfidence` and at least
         * `ocrFootnoteConfidenceFloor`.
         */
        private val minConfidence: Double = run {
            val text = page.text
            val confidences = text.indices.filter { text[it].isLetter() }.mapNotNull(page::ocrConfidenceAt)
            val pageMean = if (confidences.isEmpty()) Double.MAX_VALUE else confidences.average()
            minOf(config.ocrFootnoteMinConfidence.toDouble(), pageMean - config.ocrFootnoteConfidenceDrop)
                .coerceAtLeast(config.ocrFootnoteConfidenceFloor.toDouble())
        }

        /**
         * The notes of [run]: the whole run, else its first tail that starts `footnoteGapEm` below the line before
         * it, else none. OCR measures a short body line as small at times ("Consider these sentences:" above the
         * notes): a run opening with one such line, narrower than `minCarriedNoteWidthShare` of the notes under the
         * space and with no marker, starts after it.
         */
        fun notesOf(run: List<Placed>): List<Placed> {
            val cuts = (1 until run.size).filter { i ->
                standsApart(requireNotNull(run[i - 1].box), requireNotNull(run[i].box))
            }
            val tailFirst = cuts.firstOrNull() == 1 && isShortLead(run)
            val start = when {
                tailFirst && isNotes(run.subList(1, run.size), afterCut = true) -> 1
                isNotes(run, afterCut = false) -> 0
                else -> cuts.firstOrNull { isNotes(run.subList(it, run.size), afterCut = true) }
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
         * it ([afterCut]: or from the short line a run was cut after), and is set and reads like notes.
         */
        private fun isNotes(run: List<Placed>, afterCut: Boolean): Boolean {
            val boxes = run.map { requireNotNull(it.box) }
            val members = run.mapTo(HashSet()) { it.line }
            val above = lineAbove(boxes, members) ?: return false
            val aboveBox = requireNotNull(above.box)
            val apart = (above.line.size > bodySize * config.footnoteSizeRatio || afterCut) &&
                standsApart(aboveBox, boxes.first())
            return isAtFoot(boxes) && apart && isSetLikeNotes(run, boxes, aboveBox, members) &&
                readsLikeNotes(run.map { it.line })
        }

        /** The nearest line above [boxes] in their column, not one of [members]. */
        private fun lineAbove(boxes: List<TextBox>, members: Set<TextLine>): Placed? {
            val first = boxes.first()
            return placed.filter { it.line !in members && it.box != null && it.box.overlapsAny(boxes) }
                .filter { it.box!!.bottom >= first.top }
                .minByOrNull { it.box!!.bottom - first.top }
        }

        /** [boxes] end within `ocrFootnoteBandShare` of the page's foot, with nothing below them in their column. */
        private fun isAtFoot(boxes: List<TextBox>): Boolean {
            val runBottom = boxes.minOf { it.bottom }
            return runBottom - sheet.box.bottom <= sheet.box.height * config.ocrFootnoteBandShare &&
                blockers.none { box -> box.top <= runBottom && box.overlapsAny(boxes) && box !in boxes }
        }

        /**
         * [run] opens with a marker, or runs wide and starts flush with its column or under a rule; and, when short, a
         * note sign, a rule above it or close-set lines show it is notes.
         */
        private fun isSetLikeNotes(
            run: List<Placed>,
            boxes: List<TextBox>,
            aboveBox: TextBox,
            members: Set<TextLine>,
        ): Boolean {
            val first = boxes.first()
            val marker = openingMarker(page.text, run.first().line)?.let { page.text.substring(it.start, it.end) }
            val ruled = sheet.rules.any { isBetween(it, aboveBox, first) && it.overlapsAny(boxes) }
            val wide = marker != null || first.width >= aboveBox.width * config.minCarriedNoteWidthShare
            val placedLikeNotes = marker != null || ruled || isFlush(first, boxes, members)
            val sure = run.size >= config.ocrSureNoteLines || marker?.none(Char::isDigit) == true || ruled ||
                isTight(boxes)
            return wide && placedLikeNotes && sure
        }

        /**
         * [first] starts within `ocrFlushEm` line heights of the left edge of the other lines in its column: lines over
         * it no wider than `ocrColumnWidthRatio` times its widest line or the column's median line, whichever is wider
         * (a title across two columns is not its column, and a short centred block is measured against the column's
         * full lines, not against nothing).
         */
        private fun isFlush(first: TextBox, boxes: List<TextBox>, members: Set<TextLine>): Boolean {
            val over = placed.filter { it.line !in members }.mapNotNull { it.box }.filter { it.overlapsAny(boxes) }
            val usual = median(over.map { it.width }) ?: 0f
            val widest = maxOf(boxes.maxOf { it.width }, usual) * config.ocrColumnWidthRatio
            val column = over.filter { it.width <= widest }
            val left = column.minOfOrNull { it.left } ?: first.left
            return first.left - left <= first.height * config.ocrFlushEm
        }

        /** [boxes] (two lines or more) follow each other at most `ocrNoteSpacingRatio` of the body's spacing apart. */
        private fun isTight(boxes: List<TextBox>): Boolean {
            val pitch = median(boxes.zipWithNext { a, b -> a.top - b.top }.filter { it > 0f })
            return pitch != null && bodyPitch != null && pitch <= bodyPitch * config.ocrNoteSpacingRatio
        }

        /**
         * Read with the page's confidence (at least [minConfidence]) and opening with neither a caption label
         * ("Figure 4:", a caption under a figure), a source line ("Source:", "स्रोत:") nor a bullet (a list in small
         * type): a page's stamp, print slug or garbled strip reads badly or holds no letters.
         */
        private fun readsLikeNotes(lines: List<TextLine>): Boolean {
            val text = page.text
            val letters = lines.flatMap { line -> line.spans.flatMap { it.start until it.end } }
                .filter { text[it].isLetter() }
            val confidences = letters.mapNotNull(page::ocrConfidenceAt)
            val confident = confidences.isEmpty() || confidences.average() >= minConfidence
            val first = lines.first().spans.joinToString(" ") { text.substring(it.start, it.end) }
            return letters.isNotEmpty() && confident &&
                !FurnitureFinder.opensWithCaptionLabel(first) && !SOURCE_LABEL.containsMatchIn(first) &&
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

    /** Note markers [lines] cite glued to a word ("both.12", "Smith*", "পরিমাণ১২"): what a note may open with. */
    private fun citedMarkers(text: String, lines: List<TextLine>): Set<String> = lines.flatMapTo(HashSet()) { line ->
        line.spans.flatMap { span -> CITED_MARKER.findAll(text.substring(span.start, span.end)).map { it.value } }
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
        val OPENING_MARKER = Regex("""^([$SIGNS]{1,3}(?=[\s"“‘']*\p{L})|\p{Nd}+(?=\s+\p{L})|\p{Nd}+(?=\p{Lu}\p{Ll}))""")

        /** A source or credit line set small under a table or figure: not a note. */
        val SOURCE_LABEL = Regex("""^\s*(?i:sources?|courtesy|स्रोत|स्त्रोत|साभार|সূত্র|উৎস)\s*[:：\-–]""")

        /**
         * A marker glued to the end of a word or after its punctuation, as the body text cites a note; not the digits
         * after a decimal point or a group comma ("12.5", "1,250").
         */
        val CITED_MARKER = Regex(
            """(?<=[\p{L}\p{M}.,;:)’”])(?<!\p{Nd}[.,])(?:\p{Nd}{1,3}|[*†‡§¶#]+)(?=[\s.,;:)]|$)""",
        )
    }
}

/** A rule whose middle lies between [above]'s middle and [below]'s. */
private fun isBetween(rule: TextBox, above: TextBox, below: TextBox): Boolean {
    val middle = (rule.top + rule.bottom) / 2
    return middle < (above.top + above.bottom) / 2 && middle > (below.top + below.bottom) / 2
}

private fun median(values: List<Float>): Float? = values.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }

private fun TextBox.overlapsAny(boxes: List<TextBox>): Boolean =
    boxes.any { minOf(it.right, right) > maxOf(it.left, left) }

private fun PageText.boxOf(spans: List<TextSpan>): TextBox? =
    spans.flatMap { it.start until it.end }.mapNotNull(::boxAt).reduceOrNull(TextBox::union)
