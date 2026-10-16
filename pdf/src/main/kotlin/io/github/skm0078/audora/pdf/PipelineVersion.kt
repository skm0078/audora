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
 */
const val PIPELINE_VERSION: Int = 4
