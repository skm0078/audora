// File: core/src/test/kotlin/io/github/skm0078/audora/core/notes/HighlightPlacementTest.kt
// Purpose: Highlights stay on their sentences when a new pipeline merges, splits or moves them.

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
    private val placement = HighlightPlacement(fingerprint)
    private val page = unitsOf(PAGE, FIRST, DR, SMITH, THIRD)

    private fun highlightOn(unit: ReadingUnit) = Highlight(
        1,
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
    fun `the same pipeline covers exactly the saved sentence`() {
        assertEquals(listOf(SMITH), coveredTexts(highlightOn(page[2]), page))
    }

    @Test
    fun `a sentence merged into the one before keeps its band on the merged sentence`() {
        val merged = unitsOf(PAGE, FIRST, "$DR $SMITH", THIRD)

        assertEquals(listOf("$DR $SMITH"), coveredTexts(highlightOn(page[2]), merged))
        assertEquals(listOf("$DR $SMITH"), coveredTexts(highlightOn(page[1]), merged))
    }

    @Test
    fun `a sentence split in two is covered to its end`() {
        val joined = unitsOf(PAGE, FIRST, "$DR $SMITH", THIRD)

        assertEquals(listOf(DR, SMITH), coveredTexts(highlightOn(joined[1]), page))
    }

    @Test
    fun `moved offsets still find the sentence and its split parts`() {
        val joined = unitsOf(PAGE, FIRST, "$DR $SMITH", THIRD)
        val shifted = unitsOf(PAGE, FIRST, DR, SMITH, THIRD, startOffset = SLUG)

        assertEquals(listOf(DR, SMITH), coveredTexts(highlightOn(joined[1]), shifted))
    }

    @Test
    fun `a split found by its second part still covers the first`() {
        val joined = unitsOf(PAGE, FIRST, "$DR $SMITH", THIRD)
        // The first part is shorter than a fingerprint and changed, so only the tail finds the sentence.
        val changed = unitsOf(PAGE, FIRST, "Then I met Dr.", SMITH, THIRD)

        assertEquals(listOf(SMITH), coveredTexts(highlightOn(joined[1]), changed))
        assertEquals(listOf(DR, SMITH), coveredTexts(highlightOn(joined[1]), page))
    }

    @Test
    fun `a short sentence merged and moved is found by its quote`() {
        val short = unitsOf(PAGE, FIRST, DR, "Hello.", THIRD)
        val merged = unitsOf(PAGE, FIRST, "$DR Hello.", THIRD, startOffset = SLUG)

        assertEquals(listOf("$DR Hello."), coveredTexts(highlightOn(short[1]), merged))
        assertEquals(listOf("$DR Hello."), coveredTexts(highlightOn(short[2]), merged))
    }

    @Test
    fun `a short sentence split and moved is found by its quote`() {
        val joined = unitsOf(PAGE, FIRST, "$DR Hello.", THIRD)
        val split = unitsOf(PAGE, FIRST, DR, "Hello.", THIRD, startOffset = SLUG)

        assertEquals(listOf(DR, "Hello."), coveredTexts(highlightOn(joined[1]), split))
    }

    @Test
    fun `a highlight whose text is gone is not shown`() {
        val other = unitsOf(PAGE, "Something else entirely.", "And more of it.")

        assertEquals(emptyList<String>(), coveredTexts(highlightOn(page[2]), other))
        assertEquals(emptyMap<Int, Highlight>(), placement.place(listOf(highlightOn(page[2])), emptyList()))
    }

    @Test
    fun `only highlights of the page are placed`() {
        val elsewhere = unitsOf(PAGE + 1, FIRST, DR, SMITH, THIRD)

        assertEquals(emptyList<String>(), coveredTexts(highlightOn(page[2]), elsewhere))
    }

    private companion object {
        const val PAGE = 3
        const val V1 = 1
        const val SLUG = 30
        const val FIRST = "It was the best of times, it was the worst of times."
        const val DR = "I met Dr."
        const val SMITH = "Smith at the station that evening, just as the last train pulled away."
        const val THIRD = "It was the epoch of belief, it was the epoch of incredulity."
    }
}
