// File: core/src/main/kotlin/io/github/skm0078/audora/core/resume/ResumeLocator.kt
// Purpose: Turns a book's saved anchor into the page and sentence to start reading from, with a logged reason.

package io.github.skm0078.audora.core.resume

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.ReadingUnit

/**
 * Used by every way back into a book: opening it in the reader, "Resume" after an unexpected stop, and the
 * system's playback resumption (Bluetooth play button after a reboot). One place, so all three agree.
 */
class ResumeLocator(
    private val store: ResumeAnchorStore,
    private val pipelineVersion: Int,
    private val reAnchoring: ReAnchoring = ReAnchoring(),
) {
    /**
     * Null when the book has never been read (start at page 1), or has no pages at all (a damaged file: nothing to
     * resume into, and never a crash).
     *
     * @param pageCount the document's current page count (it can shrink if the file was replaced).
     * @param unitsOf the current units of a page, or null when that page cannot be extracted right now.
     */
    suspend fun locate(
        bookId: BookId,
        pageCount: Int,
        unitsOf: suspend (page: Int) -> List<ReadingUnit>?,
    ): ResumePoint? {
        val anchor = (if (pageCount > 0) store.get(bookId) else noPages(bookId)) ?: return null
        val point = when {
            anchor.page >= pageCount -> ResumePoint(pageCount - 1, 0, AnchorMatch.PAGE_START)

            else -> unitsOf(anchor.page)
                ?.let { reAnchoring.locate(anchor, pipelineVersion, it) }
                ?: ResumePoint(anchor.page, 0, AnchorMatch.PAGE_START)
        }
        val level = if (point.match.isTrusted) "" else " (approximate)"
        AudoraLog.i(
            TAG,
            "Resume ${anchor.logLabel} -> page=${point.page} char=${point.charStart} " +
                "match=${point.match} pipelineNow=$pipelineVersion$level",
        )
        return point
    }

    private fun noPages(bookId: BookId): ResumeAnchor? {
        AudoraLog.w(TAG, "Resume skipped, ${bookId.logLabel} has no pages")
        return null
    }

    private val AnchorMatch.isTrusted: Boolean
        get() = this == AnchorMatch.EXACT || this == AnchorMatch.OFFSET_VERIFIED || this == AnchorMatch.HEAD_FINGERPRINT

    private companion object {
        const val TAG = "Audora.Resume"
    }
}
