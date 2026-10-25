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

    /** A passage from [first] to [last], as the reader saves a range; [version] the pipeline it was saved with. */
    private fun rangeOf(first: ReadingUnit, last: ReadingUnit, quote: String, version: Int = V1) = Highlight(
        1,
        ResumeAnchor(
            BookId("b"),
            first.page,
            first.charStart,
            fingerprint.head(first.text),
            fingerprint.tail(first.text),
            version,
            0L,
        ),
        last.page,
        last.charEnd,
        quote,
    )

    private fun coveredTexts(highlight: Highlight, units: List<ReadingUnit>): List<String> {
        val placed = placement.place(listOf(highlight), units)
        return units.filter { it.index in placed }.map { it.text }
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

        assertEquals(listOf(1L, 2L), placement.place(both, merged)[merged[1].index]?.map { it.id })
    }

    @Test
    fun `a gone sentence does not land on a lookalike a word or the offset tells apart`() {
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
    fun `a tie does not fall through to a merged sentence further away`() {
        val saved = unitsOf(PAGE, FIRST, "$DR $HELLO", THIRD)
        val twins = unitsOf(
            PAGE,
            "x".repeat(BEFORE),
            "$DR $HELLO",
            "y".repeat(BETWEEN),
            "$DR $HELLO",
            "$DR $HELLO She waved.",
            THIRD,
        )

        assertEquals(emptyList<String>(), coveredTexts(highlightOn(saved[1]), twins))
    }

    @Test
    fun `a merged sentence that only contains the moved offset does not win`() {
        // A margin slug dropped: every sentence moved 30 characters back, so the old start of "Thank you." now lies
        // inside the next sentence, which merely begins with the same words.
        val slugged = unitsOf(PAGE, FIRST, THANKS, COMING, THIRD, startOffset = SLUG)
        val unslugged = unitsOf(PAGE, FIRST, THANKS, COMING, THIRD)
        // Everything moved 5 forward: the sentence before now spans the old start and ends with the same words.
        val plain = unitsOf(PAGE, FIRST, SAID, THANKS, THIRD)
        val nudged = unitsOf(PAGE, FIRST, SAID, THANKS, THIRD, startOffset = NUDGE)

        assertEquals(listOf(THANKS), coveredTexts(highlightOn(slugged[1]), unslugged))
        assertEquals(listOf(THANKS), coveredTexts(highlightOn(plain[2]), nudged))
    }

    @Test
    fun `a merged sentence covering the offset beats an exact copy further on`() {
        val before = unitsOf(PAGE, FIRST, "He turned to Mr.", "Smith said.", FILL, "Smith said.")
        val merged = unitsOf(PAGE, FIRST, "He turned to Mr. Smith said.", FILL, "Smith said.")

        assertEquals(listOf("He turned to Mr. Smith said."), coveredTexts(highlightOn(before[2]), merged))
    }

    @Test
    fun `a merged sentence whose offsets also moved yields to an identical copy within reach`() {
        // Documented limit: once the quote's side moved, the merge looks like the slug case, so the copy wins.
        val before = unitsOf(PAGE, FIRST, "He turned to Mr.", "Smith said.", FILL, "Smith said.")
        val mergedAndMoved = unitsOf(PAGE, FIRST, "He turned to Mr. Smith said.", FILL, "Smith said.", startOffset = 1)

        assertEquals(listOf("Smith said."), coveredTexts(highlightOn(before[2]), mergedAndMoved))
    }

    @Test
    fun `a split sentence whose offsets also moved yields to an identical copy within reach`() {
        val before = unitsOf(PAGE, FIRST, "$DR $HELLO", FILL, "$DR $HELLO")
        val splitAndMoved = unitsOf(PAGE, FIRST, DR, HELLO, FILL, "$DR $HELLO", startOffset = 1)

        assertEquals(listOf("$DR $HELLO"), coveredTexts(highlightOn(before[1]), splitAndMoved))
    }

    @Test
    fun `a long sentence's fingerprint is refused with an equally near rival and trusted when unique`() {
        val saved = unitsOf(PAGE, "p".repeat(PREFACE), LONG, THIRD)
        // Gone from offset 201; a copy 100 characters before it and one 100 after.
        val rivals = unitsOf(PAGE, "a".repeat(BEFORE_LONG), LONG, "b".repeat(BETWEEN_LONG), LONG, THIRD)
        // Columns reordered: the only copy now sits far beyond the shift limit.
        val reordered = unitsOf(PAGE, "c".repeat(FAR), FIRST, THIRD, LONG)

        assertEquals(emptyList<String>(), coveredTexts(highlightOn(saved[1]), rivals))
        assertEquals(listOf(LONG), coveredTexts(highlightOn(saved[1]), reordered))
    }

    @Test
    fun `a range on one page covers every sentence in it, one without letters too`() {
        val stars = unitsOf(PAGE, FIRST, DR, "* * *", HELLO, THIRD)
        val range = rangeOf(stars[1], stars[3], "$DR $HELLO", version = V2)

        assertEquals(listOf(DR, "* * *", HELLO), coveredTexts(range, stars))
        assertEquals(listOf(FIRST, DR, SMITH), coveredTexts(rangeOf(page[0], page[2], "$FIRST $DR $SMITH"), page))
    }

    @Test
    fun `a range whose later words changed keeps only the sentence it starts on`() {
        val range = rangeOf(page[1], page[2], "$DR $SMITH", version = V2)
        // The PDF was replaced in place: the first sentence is still there, the rest is new text.
        val changed = unitsOf(PAGE, FIRST, DR, FILL, THIRD)

        assertEquals(listOf(DR), coveredTexts(range, changed))
    }

    @Test
    fun `a range over pages covers the end of the first, the pages between and the start of the last`() {
        val first = unitsOf(PAGE, FIRST, DR)
        val between = unitsOf(PAGE + 1, SMITH, FILL)
        val last = unitsOf(PAGE + 2, LONG, THIRD)
        val range = rangeOf(first[1], last[0], "$DR $SMITH $FILL $LONG")
        // A new pipeline moved every offset (a slug dropped): the words still place it.
        val shifted = unitsOf(PAGE, FIRST, DR, startOffset = SLUG)

        assertEquals(listOf(DR), coveredTexts(range, first))
        assertEquals(listOf(DR), coveredTexts(range, shifted))
        assertEquals(listOf(SMITH, FILL), coveredTexts(range, between))
        assertEquals(listOf(LONG), coveredTexts(range, last))
    }

    @Test
    fun `a page that no longer holds its part of a range shows none of it`() {
        val range = rangeOf(unitsOf(PAGE, FIRST, DR)[1], unitsOf(PAGE + 2, LONG)[0], "$DR $SMITH $LONG")

        assertEquals(emptyList<String>(), coveredTexts(range, unitsOf(PAGE, FIRST, THIRD)))
        assertEquals(emptyList<String>(), coveredTexts(range, unitsOf(PAGE + 1, FILL)))
        assertEquals(emptyList<String>(), coveredTexts(range, unitsOf(PAGE + 2, THIRD, LONG)))
        assertEquals(emptyList<String>(), coveredTexts(range, unitsOf(PAGE + 3, LONG)))
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
        const val PREFACE = 200
        const val BEFORE_LONG = 100
        const val BETWEEN_LONG = 113
        const val FAR = 600
        const val BETWEEN = 46
        const val NUDGE = 5
        const val FIRST = "It was the best of times, it was the worst of times."
        const val SECOND = "It was the age of wisdom, it was the age of foolishness."
        const val DR = "I met Dr."
        const val HELLO = "Hello."
        const val SMITH = "Smith at the station that evening, just as the last train pulled away."
        const val FILL = "Then the evening went on without them for a while."
        const val LONG = "It was the season of Light, it was the season of Darkness, it was the spring of hope."
        const val THANKS = "Thank you."
        const val COMING = "Thank you for coming all this way."
        const val SAID = "I said thank you."
        const val THIRD = "It was the epoch of belief, it was the epoch of incredulity."
    }
}
