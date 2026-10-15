// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/PipelineVersion.kt
// Purpose: Version of the :pdf output; resume re-anchors by fingerprint when it changes (Build Spec §6, C8).

package io.github.skm0078.audora.pdf

/**
 * Bump whenever any :pdf change can move a ReadingUnit's charStart or change its text (AGENTS.md).
 * 1 = P1-02: PDFium text page + `/ActualText` rebuild, sentence split by BreakIterator, no layout.
 */
const val PIPELINE_VERSION: Int = 1
