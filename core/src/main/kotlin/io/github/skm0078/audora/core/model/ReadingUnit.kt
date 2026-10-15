// File: core/src/main/kotlin/io/github/skm0078/audora/core/model/ReadingUnit.kt
// Purpose: One speakable sentence with enough position data to resume, highlight and re-anchor (Build Spec §4).

package io.github.skm0078.audora.core.model

/**
 * @property page zero-based page index in the document.
 * @property index sentence index within the page. Display only, NOT the resume key (that is [charStart]).
 * @property charStart start offset (inclusive) into the page's reading-order text (layout output, ADR-018). Units
 *   of a page ascend by [charStart] in reading order and do not overlap.
 * @property charEnd end offset (exclusive) into the same text.
 * @property text the speakable text: whitespace collapsed; later tickets normalize it further (P2-04).
 * @property pauseAfterMs silence after the sentence; prosody (P2-04) sets real values.
 * @property bounds one box per line the sentence covers; null for OCR'd or converted text.
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
) {
    init {
        require(page >= 0) { "page must be >= 0, was $page" }
        require(charStart in 0..charEnd) { "charStart ($charStart) must be in 0..charEnd ($charEnd)" }
    }
}
