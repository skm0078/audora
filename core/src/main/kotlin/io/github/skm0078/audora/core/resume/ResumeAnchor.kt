// File: core/src/main/kotlin/io/github/skm0078/audora/core/resume/ResumeAnchor.kt
// Purpose: Where to resume a book; survives pipeline changes by re-anchoring on offset, then fingerprints (C8).

package io.github.skm0078.audora.core.resume

import io.github.skm0078.audora.core.library.BookId

/**
 * The start of the sentence that was being read, one per book (Build Spec §4, ADR-009, ADR-015).
 *
 * Only offsets and hashes, never text: a saved position must not leak the user's PDF into the database or logs.
 *
 * @property page zero-based page index.
 * @property charStart the sentence's start offset into the page's extracted text (`ReadingUnit.charStart`).
 * @property fingerprint [SentenceFingerprint.head] of the sentence.
 * @property tailFingerprint [SentenceFingerprint.tail] of the sentence: still matches when a later pipeline
 *   changes how the sentence starts (a running header no longer glued to it, two sentences merged).
 * @property pipelineVersion the :pdf `PIPELINE_VERSION` that produced [charStart].
 * @property savedAtMillis wall-clock time of the save.
 */
data class ResumeAnchor(
    val bookId: BookId,
    val page: Int,
    val charStart: Int,
    val fingerprint: Long,
    val tailFingerprint: Long,
    val pipelineVersion: Int,
    val savedAtMillis: Long,
) {
    init {
        require(page >= 0) { "page must be >= 0, was $page" }
        require(charStart >= 0) { "charStart must be >= 0, was $charStart" }
        require(pipelineVersion > 0) { "pipelineVersion must be > 0, was $pipelineVersion" }
    }

    /** Log form: identifiers only. */
    val logLabel: String get() = "book=${bookId.logLabel} page=$page char=$charStart pipeline=$pipelineVersion"
}
