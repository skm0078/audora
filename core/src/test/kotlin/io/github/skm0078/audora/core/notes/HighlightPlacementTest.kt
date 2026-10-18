// File: core/src/test/kotlin/io/github/skm0078/audora/core/notes/HighlightPlacementTest.kt
// Purpose: Highlights stay on their sentences when a new pipeline merges, splits or moves them, and never jump.

package io.github.skm0078.audora.core.notes

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.resume.ResumeAnchor
import io.github.skm0078.audora.core.resume.SentenceFingerprint
import io.github.skm0078.audora.core.resume.unitsOf
import org.junit.Assert.assertEquals
import org.junit.Test

class HighlightPlacementTest {
    private val fingerprint = SentenceFingerprint()
    private val placement = HighlightPlacement(V2)
    private val page = unitsOf(PAGE, FIRST, DR, SMITH, THIRD)

    private fun highlightOn(unit: ReadingUnit, id: Long = 1) = Highlight(
        id,
        ResumeAnchor(
            BookId("b"),
            unit.page,
            unit.charStart,
            fingerprint.head(unit.text),
            fingerprint.tail(unit.text),
            V1,
            0L,
        ),
        unit.page,
        unit.charEnd,
        unit.text,
    )

    private fun coveredTexts(highlight: Highlight, units: List<ReadingUnit>): List<String> {
        val placed = placement.place(listOf(highlight), units)
        return units.filter { it.charStart in placed }.map { it.text }
    }

    @Test
    fun `the same text covers exactly the saved sentence`() {
        assertEquals(listOf(SMITH), coveredTexts(highlightOn(page[2]), page))
    }

    @Test
    fun `a sentence merged into its neighbour keeps its band on the merged sentence`() {
        val merged = unitsOf(PAGE, FIRST, "$DR $SMITH", THIRD)

        assertEquals(listOf("$DR $SMITH"), coveredTexts(highlightOn(page[2]), merged))
        assertEquals(listOf("$DR $SMITH"), coveredTexts(highlightOn(page[1]), merged))
    }

    @Test
    fun `a sentence split in two is covered to its end, also after offsets moved`() {
        val joined = unitsOf(PAGE, FIRST, "$DR $SMITH", THIRD)
        val shifted = unitsOf(PAGE, FIRST, DR, SMITH, THIRD, startOffset = SLUG)

        assertEquals(listOf(DR, SMITH), coveredTexts(highlightOn(joined[1]), page))
        assertEquals(listOf(DR, SMITH), coveredTexts(highlightOn(joined[1]), shifted))
    }

    @Test
    fun `a split found by its second part covers only what is still the saved sentence`() {
        val joined = unitsOf(PAGE, FIRST, "$DR $SMITH", THIRD)
        val changed = unitsOf(PAGE, FIRST, "Then I met Dr.", SMITH, THIRD)

        assertEquals(listOf(SMITH), coveredTexts(highlightOn(joined[1]), changed))
    }

    @Test
    fun `short sentences merged or split are found by their quote`() {
        val short = unitsOf(PAGE, FIRST, DR, HELLO, THIRD)
        val merged = unitsOf(PAGE, FIRST, "$DR $HELLO", THIRD)
        val movedMerge = unitsOf(PAGE, FIRST, "$DR $HELLO", THIRD, startOffset = SLUG)
        val movedSplit = unitsOf(PAGE, FIRST, DR, HELLO, THIRD, startOffset = SLUG)

        assertEquals(listOf("$DR $HELLO"), coveredTexts(highlightOn(short[2]), merged))
        assertEquals(listOf("$DR $HELLO"), coveredTexts(highlightOn(short[1]), movedMerge))
        assertEquals(listOf(DR, HELLO), coveredTexts(highlightOn(merged[1]), movedSplit))
        // Too short to trust away from its offset: "Hello." could be any greeting.
        assertEquals(emptyList<String>(), coveredTexts(highlightOn(short[2]), movedMerge))
    }

    @Test
    fun `two highlights merged into one sentence both stay`() {
        val short = unitsOf(PAGE, FIRST, DR, HELLO, THIRD)
        val merged = unitsOf(PAGE, FIRST, "$DR $HELLO", THIRD)
        val both = listOf(highlightOn(short[1], 1), highlightOn(short[2], 2))

        assertEquals(listOf(1L, 2L), placement.place(both, merged)[merged[1].charStart]?.map { it.id })
    }

    @Test
    fun `a gone sentence never lands on a lookalike`() {
        val yeses = unitsOf(PAGE, FIRST, "Yes.", SECOND, "Yes.", THIRD)
        val oneGone = unitsOf(PAGE, FIRST, SECOND, "Yes.", THIRD)
        val eyes = unitsOf(PAGE, FIRST, "Her eyes closed.", THIRD)
        val tables = unitsOf(PAGE, FIRST, "Table 2.", "Table 21.")
        val table21 = unitsOf(PAGE, FIRST, "Table 21.")

        assertEquals(emptyList<String>(), coveredTexts(highlightOn(yeses[1]), oneGone))
        assertEquals(emptyList<String>(), coveredTexts(highlightOn(yeses[1]), eyes))
        assertEquals(emptyList<String>(), coveredTexts(highlightOn(tables[1]), table21))
        assertEquals(emptyList<String>(), coveredTexts(highlightOn(page[2]), unitsOf(PAGE, "Something else.")))
    }

    @Test
    fun `a sentence with no letters is never placed and never joins a split`() {
        val stars = unitsOf(PAGE, FIRST, "* * *", THIRD)
        val joined = unitsOf(PAGE, FIRST, "$DR $HELLO", THIRD)
        val withStars = unitsOf(PAGE, FIRST, DR, "* * *", HELLO, THIRD)

        assertEquals(emptyList<String>(), coveredTexts(highlightOn(stars[1]), stars))
        assertEquals(emptyList<String>(), coveredTexts(highlightOn(joined[1]), withStars))
    }

    @Test
    fun `two equally near matches place nothing`() {
        val saved = unitsOf(PAGE, FIRST, "$DR $HELLO", THIRD)
        // The saved sentence is gone; a copy sits on each side of where it started (offset 53), 32 characters away.
        val twins = unitsOf(PAGE, "x".repeat(BEFORE), "$DR $HELLO", "y".repeat(BETWEEN), "$DR $HELLO", THIRD)

        assertEquals(emptyList<String>(), coveredTexts(highlightOn(saved[1]), twins))
    }

    @Test
    fun `only the page's highlights are placed`() {
        val elsewhere = unitsOf(PAGE + 1, FIRST, DR, SMITH, THIRD)

        assertEquals(emptyList<String>(), coveredTexts(highlightOn(page[2]), elsewhere))
        assertEquals(emptyMap<Int, List<Highlight>>(), placement.place(listOf(highlightOn(page[2])), emptyList()))
    }

    private companion object {
        const val PAGE = 3
        const val V1 = 1
        const val V2 = 2
        const val SLUG = 30
        const val BEFORE = 20
        const val BETWEEN = 46
        const val FIRST = "It was the best of times, it was the worst of times."
        const val SECOND = "It was the age of wisdom, it was the age of foolishness."
        const val DR = "I met Dr."
        const val HELLO = "Hello."
        const val SMITH = "Smith at the station that evening, just as the last train pulled away."
        const val THIRD = "It was the epoch of belief, it was the epoch of incredulity."
    }
}
