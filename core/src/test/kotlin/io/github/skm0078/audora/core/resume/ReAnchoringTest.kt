// File: core/src/test/kotlin/io/github/skm0078/audora/core/resume/ReAnchoringTest.kt
// Purpose: The saved sentence is found again on the same pipeline, after offsets move, and after its text changes.

package io.github.skm0078.audora.core.resume

import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.TableRowInfo
import io.github.skm0078.audora.core.model.UnitKind
import org.junit.Assert.assertEquals
import org.junit.Test

class ReAnchoringTest {
    private val fingerprint = SentenceFingerprint()
    private val reAnchoring = ReAnchoring(fingerprint)

    private val page = unitsOf(4, FIRST, SECOND, THIRD)

    private fun anchorAt(index: Int, units: List<ReadingUnit> = page) = units[index].let {
        ResumeAnchor(BOOK, it.page, it.charStart, fingerprint.head(it), fingerprint.tail(it), V1, 0L)
    }

    /** A continued table: a zero-length "Table continues" summary sharing its offset with row 0, then row 1. */
    private fun continuedTable(startOffset: Int): List<ReadingUnit> {
        val rows = unitsOf(4, FIRST, SECOND, startOffset = startOffset)
        val info = TableRowInfo(0, null, 2, 1, listOf("Year"), emptyList(), continued = true, rowsBefore = 5)
        val summary = rows[0].copy(
            index = 0,
            charEnd = startOffset,
            text = "",
            kind = UnitKind.TABLE_SUMMARY,
            table = info,
        )
        return listOf(summary) + rows.mapIndexed { i, row ->
            row.copy(index = i + 1, kind = UnitKind.TABLE_ROW, table = info.copy(row = i, cells = emptyList()))
        }
    }

    @Test
    fun `same pipeline resumes exactly`() {
        val point = reAnchoring.locate(anchorAt(1), V1, page)

        assertEquals(ResumePoint(4, page[1].charStart, AnchorMatch.EXACT), point)
    }

    @Test
    fun `a new pipeline with the same offsets is verified by fingerprint`() {
        val point = reAnchoring.locate(anchorAt(2), V2, page)

        assertEquals(ResumePoint(4, page[2].charStart, AnchorMatch.OFFSET_VERIFIED), point)
    }

    @Test
    fun `moved text is found by its head fingerprint`() {
        // A later pipeline drops a 30-character running header: every offset moves.
        val shifted = unitsOf(4, FIRST, SECOND, THIRD, startOffset = 30)
        val anchor = anchorAt(1)

        val point = reAnchoring.locate(anchor, V2, shifted)

        assertEquals(ResumePoint(4, shifted[1].charStart, AnchorMatch.HEAD_FINGERPRINT), point)
    }

    @Test
    fun `a sentence whose start changed is found by its tail fingerprint`() {
        // A later pipeline splits differently, so the saved sentence now starts with words it did not have.
        val before = unitsOf(4, FIRST, LONG, THIRD)
        val anchor = anchorAt(1, before)
        val changed = unitsOf(4, FIRST, "Indeed, $LONG", THIRD, startOffset = 7)

        val point = reAnchoring.locate(anchor, V2, changed)

        assertEquals(ResumePoint(4, changed[1].charStart, AnchorMatch.TAIL_FINGERPRINT), point)
    }

    @Test
    fun `a repeated sentence resolves to the copy nearest the saved offset`() {
        val repeated = unitsOf(4, "Yes.", FIRST, "Yes.", SECOND, "Yes.", THIRD)
        // Saved just before the third "Yes.", so the unit at the offset is SECOND.
        val anchor = anchorAt(4, repeated).copy(charStart = repeated[4].charStart - 2)

        val point = reAnchoring.locate(anchor, V2, repeated)

        assertEquals(repeated[4].charStart, point.charStart)
        assertEquals(AnchorMatch.HEAD_FINGERPRINT, point.match)
    }

    @Test
    fun `same pipeline but the file was replaced falls back to fingerprints`() {
        val replaced = unitsOf(4, "A wholly new opening sentence here.", FIRST, SECOND, THIRD)

        val point = reAnchoring.locate(anchorAt(1), V1, replaced)

        assertEquals(ResumePoint(4, replaced[2].charStart, AnchorMatch.HEAD_FINGERPRINT), point)
    }

    @Test
    fun `nothing matches - the unit at or before the saved offset is used`() {
        val other = unitsOf(4, "Alpha one is here.", "Beta two is here.", "Gamma three is here.")
        val anchor = anchorAt(1).copy(charStart = other[1].charStart + 5)

        val point = reAnchoring.locate(anchor, V2, other)

        assertEquals(ResumePoint(4, other[1].charStart, AnchorMatch.OFFSET_UNVERIFIED), point)
    }

    @Test
    fun `an offset before the first unit starts at the first unit`() {
        val late = unitsOf(4, "Alpha one is here.", startOffset = 50)
        val anchor = anchorAt(0).copy(charStart = 3)

        val point = reAnchoring.locate(anchor, V2, late)

        assertEquals(ResumePoint(4, 50, AnchorMatch.OFFSET_UNVERIFIED), point)
    }

    @Test
    fun `an empty page starts at its top`() {
        assertEquals(ResumePoint(4, 0, AnchorMatch.PAGE_START), reAnchoring.locate(anchorAt(1), V1, emptyList()))
    }

    @Test
    fun `an anchor without fingerprints never matches by fingerprint`() {
        val symbols = unitsOf(4, "* * *", "— — —")
        val anchor = anchorAt(0, symbols)

        val point = reAnchoring.locate(anchor.copy(charStart = symbols[1].charStart), V1, symbols)

        assertEquals(AnchorMatch.OFFSET_UNVERIFIED, point.match)
        assertEquals(symbols[1].charStart, point.charStart)
    }

    @Test
    fun `an anchor on a zero-length table summary is found again after a pipeline change`() {
        val units = continuedTable(startOffset = 10)
        val anchor = anchorAt(0, units)

        assertEquals(ResumePoint(4, 10, AnchorMatch.EXACT), reAnchoring.locate(anchor, V1, units))
        assertEquals(ResumePoint(4, 10, AnchorMatch.OFFSET_VERIFIED), reAnchoring.locate(anchor, V2, units))
        // A new sentence now sits at the saved offset and the table moved after it.
        val moved = unitsOf(4, THIRD) + continuedTable(startOffset = THIRD.length + 1)
        val point = reAnchoring.locate(anchor, V2, moved)
        assertEquals(ResumePoint(4, THIRD.length + 1, AnchorMatch.HEAD_FINGERPRINT), point)
    }

    @Test
    fun `among units sharing an offset, the one with the saved fingerprint is picked`() {
        val units = continuedTable(startOffset = 10)

        assertEquals(AnchorMatch.EXACT, reAnchoring.locate(anchorAt(0, units), V1, units).match)
        assertEquals(AnchorMatch.EXACT, reAnchoring.locate(anchorAt(1, units), V1, units).match)
    }

    private companion object {
        const val V1 = 1
        const val V2 = 2
        const val FIRST = "It was the best of times, it was the worst of times."
        const val SECOND = "It was the age of wisdom, it was the age of foolishness."
        const val THIRD = "It was the epoch of belief, it was the epoch of incredulity."

        // More than 48 letters, so its head and tail fingerprints cover different words.
        const val LONG = "It was the season of Light, it was the season of Darkness, it was the spring of hope."
    }
}
