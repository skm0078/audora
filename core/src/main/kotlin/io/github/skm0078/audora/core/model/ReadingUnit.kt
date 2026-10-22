// File: core/src/main/kotlin/io/github/skm0078/audora/core/model/ReadingUnit.kt
// Purpose: One speakable sentence with enough position data to resume, highlight and re-anchor (Build Spec §4).

package io.github.skm0078.audora.core.model

/**
 * @property page zero-based page index in the document.
 * @property index sentence index within the page. Display only, NOT the resume key (that is [charStart]).
 * @property charStart start offset (inclusive) into the page's reading-order text (layout output, ADR-018). Units
 *   of a page ascend by [charStart] in reading order and do not overlap.
 * @property charEnd end offset (exclusive) into the same text.
 * @property text the extracted text, whitespace collapsed. What the reader shows and resume fingerprints hash; the
 *   voice gets it normalized at render time (ADR-019).
 * @property pauseAfterMs silence played after the sentence: longer after headings, paragraphs and list items
 *   (prosody, P2-04, ADR-029).
 * @property bounds one box per line the sentence covers, in page space; null when no glyph has a position (OCR without
 *   word boxes, generated text).
 * @property kind what the sentence is part of on the page; some readings depend on it ("IV." in a heading), and
 *   playback decides by setting whether some kinds are read, announced or skipped (ADR-035). `:pdf` tags, it never
 *   drops: a table row stays a unit, so resume and highlight can land on it whatever the setting.
 * @property speakScore how likely the sentence is real language a voice can read, 0–1 (higher is better), from
 *   OCR confidence, dictionary words, letter statistics and symbol soup (RI-2, ADR-035); null when not scored.
 *   Playback skips units below [io.github.skm0078.audora.core.config.SpeakabilityConfig.skipBelow] when
 *   "Skip unreadable text" is on.
 * @property table where a [UnitKind.TABLE_SUMMARY] or [UnitKind.TABLE_ROW] unit sits in its table; null for every
 *   other kind.
 * @property noteRef the note number a [UnitKind.FOOTNOTE] unit holds (its marker in the body text points to it);
 *   null for every other kind.
 */
data class ReadingUnit(
    val page: Int,
    val index: Int,
    val charStart: Int,
    val charEnd: Int,
    val text: String,
    val lang: Lang,
    val pauseAfterMs: Int,
    val bounds: List<TextBox>?,
    val kind: UnitKind = UnitKind.BODY,
    val speakScore: Float? = null,
    val table: TableRowInfo? = null,
    val noteRef: Int? = null,
) {
    init {
        require(page >= 0) { "page must be >= 0, was $page" }
        require(charStart in 0..charEnd) { "charStart ($charStart) must be in 0..charEnd ($charEnd)" }
        require(speakScore == null || speakScore in 0f..1f) { "speakScore ($speakScore) must be in 0..1" }
        require((table != null) == kind.isTable) { "table info must be set exactly on table units, kind was $kind" }
        table?.let { info ->
            require(info.cells.all { it.end <= text.length }) { "table cells must lie inside the unit's text" }
        }
        require(noteRef == null || kind == UnitKind.FOOTNOTE) { "noteRef is only for footnotes, kind was $kind" }
    }
}

/**
 * The page element a [ReadingUnit] comes from, as layout detected it (P2-02 headings, P2-04 list items, ADR-035 for
 * the rest). The golden corpus scores every kind except the table kinds (pdf/golden/README.md: tables are not in
 * the expected text; their cells are scored against hand labels).
 */
enum class UnitKind {
    BODY,
    HEADING,
    LIST_ITEM,

    /**
     * Opens a table: its header row as text, the header cells and the table's size in [ReadingUnit.table]. The
     * caption, when it has one, is the [CAPTION] unit before it.
     */
    TABLE_SUMMARY,

    /** One row of a table, cells in reading order; the header texts and cell spans in [ReadingUnit.table]. */
    TABLE_ROW,

    /** A footnote or endnote entry, read where it is printed (the bottom of the page). */
    FOOTNOTE,

    /** "Figure 2. …", "Table 3 …", "चित्र …": announced, then read. */
    CAPTION,

    /** A display equation, which reads as symbol soup character by character. */
    EQUATION,

    /** A "Note:" box or sidebar set apart from the body text. */
    ASIDE,
    ;

    val isTable: Boolean get() = this == TABLE_SUMMARY || this == TABLE_ROW
}

/**
 * Where a table unit sits in its table (RI-4, ADR-035). Header texts are copies of the header row's cells, kept so
 * a row can be read as "Name: Ram. Age: 34." without the header row in view; nothing here is added to the unit's
 * `text` (ADR-018: text is what the page says).
 *
 * @property tableIndex the table's place among the page's tables, from 0.
 * @property row the data row's index from 0; null on the [UnitKind.TABLE_SUMMARY] unit, which holds the header row.
 * @property rowCount the table's data rows, header row not included.
 * @property columnCount columns in the table.
 * @property headers the header row's cell texts, one per column; empty when the table has no header row.
 * @property cells this row's cells as spans of the unit's text, one per column in order (an empty cell is an empty
 *   span); on the summary unit, the header row's cells.
 */
data class TableRowInfo(
    val tableIndex: Int,
    val row: Int?,
    val rowCount: Int,
    val columnCount: Int,
    val headers: List<String>,
    val cells: List<CellSpan>,
) {
    init {
        require(tableIndex >= 0) { "tableIndex must be >= 0, was $tableIndex" }
        require(rowCount > 0 && columnCount > 0) { "a table has rows and columns, was $rowCount × $columnCount" }
        require(row == null || row in 0 until rowCount) { "row ($row) must be in 0 until $rowCount" }
        require(headers.isEmpty() || headers.size == columnCount) { "headers must be empty or one per column" }
        require(cells.size <= columnCount) { "at most one cell per column, was ${cells.size} for $columnCount" }
    }
}

/** A cell's text as offsets into its row unit's `text`: [start] inclusive, [end] exclusive. */
data class CellSpan(val start: Int, val end: Int) {
    init {
        require(start in 0..end) { "start ($start) must be in 0..end ($end)" }
    }
}
