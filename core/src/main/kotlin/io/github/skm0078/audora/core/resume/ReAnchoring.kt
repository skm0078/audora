// File: core/src/main/kotlin/io/github/skm0078/audora/core/resume/ReAnchoring.kt
// Purpose: Finds the saved sentence among a page's current units, even after the :pdf pipeline changed (C8).

package io.github.skm0078.audora.core.resume

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.ReadingUnit
import kotlin.math.abs

/** How a saved anchor was found again; logged with every resume so a wrong resume can be explained. */
enum class AnchorMatch {
    /** Same pipeline, a unit starts at the saved offset and its fingerprint matches. */
    EXACT,

    /** Pipeline changed; the unit at the saved offset still has the saved head fingerprint. */
    OFFSET_VERIFIED,

    /** The saved head fingerprint was found elsewhere on the page (text moved, e.g. columns reordered). */
    HEAD_FINGERPRINT,

    /** Only the saved tail fingerprint was found (the sentence's start changed). */
    TAIL_FINGERPRINT,

    /** Nothing matched; the unit at or before the saved offset, so at worst a little is read again. */
    OFFSET_UNVERIFIED,

    /** The page has no units now, or no longer exists: start of the page. */
    PAGE_START,
}

/** Where playback should start: a page and the `charStart` of a unit on it (0 for the top of the page). */
data class ResumePoint(val page: Int, val charStart: Int, val match: AnchorMatch)

/**
 * Pure re-anchoring rules (Build Spec §6 "State"; ADR-015). Order:
 * 1. same pipeline and a unit at the saved offset with the saved fingerprint → [AnchorMatch.EXACT];
 * 2. the unit containing the saved offset has the saved head fingerprint → [AnchorMatch.OFFSET_VERIFIED];
 * 3. a unit with the saved head fingerprint, nearest to the saved offset → [AnchorMatch.HEAD_FINGERPRINT];
 * 4. the same with the tail fingerprint → [AnchorMatch.TAIL_FINGERPRINT];
 * 5. the unit containing (or last before) the saved offset → [AnchorMatch.OFFSET_UNVERIFIED].
 *
 * Fingerprints are checked even when the pipeline is unchanged: a PDF overwritten in place keeps its book
 * (P1-01) but not its sentences.
 */
class ReAnchoring(private val fingerprint: SentenceFingerprint = SentenceFingerprint()) {
    /**
     * Where [unit], a sentence of [book] read a moment ago, is among [units]: the same page read again with other
     * text settings (citation numbers skipped or kept), so its offsets may have moved but its words have not (ADR-064).
     */
    fun relocate(book: BookId, unit: ReadingUnit, pipelineVersion: Int, units: List<ReadingUnit>): ResumePoint {
        val anchor = ResumeAnchor(
            book,
            unit.page,
            unit.charStart,
            fingerprint.head(unit),
            fingerprint.tail(unit),
            pipelineVersion,
            savedAtMillis = 0L,
        )
        return locate(anchor, pipelineVersion, units)
    }

    /** [units] are the current units of [ResumeAnchor.page], in reading order. */
    fun locate(anchor: ResumeAnchor, currentPipelineVersion: Int, units: List<ReadingUnit>): ResumePoint {
        if (units.isEmpty()) return ResumePoint(anchor.page, 0, AnchorMatch.PAGE_START)
        val heads = units.map { fingerprint.head(it) }
        val atOffset = atOffset(anchor, heads, units)

        fun point(index: Int, match: AnchorMatch) = ResumePoint(anchor.page, units[index].charStart, match)

        val sameText = heads[atOffset] == anchor.fingerprint && anchor.fingerprint != SentenceFingerprint.NONE
        return when {
            sameText && currentPipelineVersion == anchor.pipelineVersion &&
                units[atOffset].charStart == anchor.charStart -> point(atOffset, AnchorMatch.EXACT)

            sameText -> point(atOffset, AnchorMatch.OFFSET_VERIFIED)

            else -> nearestWith(anchor.fingerprint, heads, units, anchor.charStart)
                ?.let { point(it, AnchorMatch.HEAD_FINGERPRINT) }
                ?: nearestWith(anchor.tailFingerprint, units.map { fingerprint.tail(it) }, units, anchor.charStart)
                    ?.let { point(it, AnchorMatch.TAIL_FINGERPRINT) }
                ?: point(atOffset, AnchorMatch.OFFSET_UNVERIFIED)
        }
    }

    /**
     * The unit containing (or last before) the saved offset. Units can share an offset (a zero-length table summary
     * and the row after it): among them, the one with the saved fingerprint, else the last.
     */
    private fun atOffset(anchor: ResumeAnchor, heads: List<Long>, units: List<ReadingUnit>): Int {
        val last = units.indexOfLast { it.charStart <= anchor.charStart }.coerceAtLeast(0)
        val offset = units[last].charStart
        val matching = units.indices.firstOrNull {
            units[it].charStart == offset && heads[it] == anchor.fingerprint &&
                anchor.fingerprint != SentenceFingerprint.NONE
        }
        return matching ?: last
    }

    /** Index of the unit with [wanted] closest to [charStart]; a repeated sentence ("Yes.") picks the right one. */
    private fun nearestWith(wanted: Long, fingerprints: List<Long>, units: List<ReadingUnit>, charStart: Int): Int? {
        if (wanted == SentenceFingerprint.NONE) return null
        return fingerprints.indices
            .filter { fingerprints[it] == wanted }
            .minByOrNull { abs(units[it].charStart - charStart) }
    }
}
