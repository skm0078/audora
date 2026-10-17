// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/PipelineVersion.kt
// Purpose: Version of the :pdf output; resume re-anchors by fingerprint when it changes (Build Spec §6, C8).

package io.github.skm0078.audora.pdf

/**
 * Bump whenever any :pdf change can move a ReadingUnit's charStart or change its text (AGENTS.md).
 * 1 = P1-02: PDFium text page + `/ActualText` rebuild, sentence split by BreakIterator, no layout.
 * 2 = P2-02: layout (XY-cut columns, reading order, paragraphs, headings); charStart indexes the reading-order
 *     text, not the extracted text (ADR-018).
 * 3 = P2-03: running headers, page numbers and table interiors removed; line-end hyphens resolved, ligatures and
 *     junk characters folded, bullets and numeric citation markers dropped (ADR-020).
 * 4 = P3-03: Indic repair before text repair: placeholder glyphs inside a cluster dropped; on visual-order pages
 *     pre-base vowel signs and rephs moved to logical order, Bengali two-part vowels composed (ADR-021).
 * 5 = P3-04: text in Kruti Dev, DevLys, Chanakya and Bijoy fonts converted to Unicode; "অ্য" no longer counts as
 *     an impossible sequence (ADR-023).
 * 6 = P3-05: pages routed to OCR are read from Tesseract's text instead of their text layer (ADR-025).
 * 7 = P3-02: text in NFC; "|" after Indic text read as a danda; sentence breaks corrected after dandas, closing
 *     quotes, Hindi/Bengali abbreviations and initials, and verse numbers (ADR-028).
 * 8 = P2-04: each list item is a paragraph of its own (no longer run into the next item as one sentence); units
 *     carry their kind (body, heading, list item) and real pauses (ADR-029).
 * 9 = known gaps (ADR-032): single Chanakya glyphs inside Kruti Dev/DevLys words read as their conjuncts; pages set
 *     sideways laid out in their reading direction; scanned pages turned upright before OCR, running headers and
 *     page numbers on them dropped, and their words carry boxes for the highlight.
 * 10 = RI-7a: figure and table captions and display equations tagged (ADR-035); a display line of maths, or a
 *     caption joined to the paragraph above it, is a paragraph of its own, so the sentences around it end there.
 *     RI-4a: tables kept as a header unit and row units instead of dropped (same version: 10 had not shipped).
 *     RI-5a: note markers left out of the text, footnotes and "Note:" paragraphs tagged (same version).
 *     RI-3: on scanned pages, unsure lines read again with the other installed models, and OCR near-misses
 *     corrected against the word lists (same version).
 *     RI-4a part 2: tables on scanned pages read as a header unit and row units, ruled ones from the rules found in
 *     the page image, borderless ones from OCR's rows (same version).
 * 11 = RI-10 on scanned pages: headings from OCR's line sizes and list items from bullets OCR misread as letters,
 *     zeros or signs, which are no longer read aloud (ADR-035). RI-5a: note signs OCR glued to words left out
 *     (same version). RI-4a: two table columns set close together split (same version). RI-3: Bengali dandas and
 *     vowel signs OCR misread as "৷", "|" or "!" restored (same version). Review fixes: tables split at their intro
 *     line, side-by-side tables kept apart (also when layout joins their rows), one-character cells by a rule
 *     kept, `/Rotate` on scanned pages, exponents beside operators kept, retried margin strips (same version).
 *     RI-5a: footnotes at the foot of scanned pages tagged, their opening markers left out (same version).
 *     RI-4a: a table carried over a page break continues there; its first row of data is no longer a header row
 *     (same version).
 */
const val PIPELINE_VERSION: Int = 11
