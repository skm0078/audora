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
 * @property bounds one box per line the sentence covers; null for OCR'd or converted text.
 * @property kind what the sentence is part of on the page; some readings depend on it ("IV." in a heading).
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
) {
    init {
        require(page >= 0) { "page must be >= 0, was $page" }
        require(charStart in 0..charEnd) { "charStart ($charStart) must be in 0..charEnd ($charEnd)" }
    }
}

/** The page element a [ReadingUnit] comes from, as layout detected it (P2-02 headings, P2-04 list items). */
enum class UnitKind { BODY, HEADING, LIST_ITEM }
