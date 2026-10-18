// File: core/src/test/kotlin/io/github/skm0078/audora/core/tutor/ReadAlongTest.kt
// Purpose: The reading tutor marks read, misread, skipped and unjudged words from growing and final stretches.

package io.github.skm0078.audora.core.tutor

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.tutor.WordMark.MISREAD
import io.github.skm0078.audora.core.tutor.WordMark.PASSED
import io.github.skm0078.audora.core.tutor.WordMark.READ
import io.github.skm0078.audora.core.tutor.WordMark.SKIPPED
import io.github.skm0078.audora.core.tutor.WordMark.UNREAD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadAlongTest {
    private fun page(vararg sentences: String, lang: Lang = Lang.EN) =
        ReadAlong.start(PageWords.of(sentences.toList(), lang))

    private fun ReadAlong.say(text: String, final: Boolean = true) = heard(PageWords.heard(text), final)

    private data class Case(val name: String, val page: String, val heard: String, val marks: List<WordMark>)

    @Test
    fun `one stretch is marked word by word`() {
        val cases = listOf(
            Case("as written", "The cat sat on the mat.", "THE CAT SAT ON THE MAT", List(6) { READ }),
            Case(
                "cut mid-sentence",
                "The cat sat on the mat.",
                "the cat sat",
                listOf(READ, READ, READ, UNREAD, UNREAD, UNREAD),
            ),
            Case(
                "a word misread",
                "The cat sat on the mat.",
                "the cat sit on the mat",
                listOf(READ, READ, MISREAD, READ, READ, READ),
            ),
            Case(
                "a word skipped",
                "The cat sat on the mat.",
                "the cat on the mat",
                listOf(READ, READ, SKIPPED, READ, READ, READ),
            ),
            Case(
                "a repeat and a filler",
                "The cat sat on the mat.",
                "the cat um the cat sat on the mat",
                List(6) {
                    READ
                },
            ),
            Case("a near spelling", "Tomorrow they travelled.", "tomorrow they traveled", List(3) { READ }),
            Case(
                "a number read as words",
                "In 1995 we left.",
                "in nineteen ninety five we left",
                listOf(READ, PASSED, READ, READ),
            ),
            Case("a number left out", "In 1995 we left.", "in we left", listOf(READ, PASSED, READ, READ)),
            Case("short words must match", "It is in.", "it is on", listOf(READ, READ, UNREAD)),
        )
        cases.forEach { case -> assertEquals(case.name, case.marks, page(case.page).say(case.heard).marks) }
    }

    @Test
    fun `a skipped line is found by looking ahead`() {
        val along = page("One two three four five six seven eight.", "Nine ten eleven twelve.")
            .say("one two nine ten eleven twelve")

        assertEquals(List(2) { READ } + List(6) { SKIPPED } + List(4) { READ }, along.marks)
        assertTrue(along.finished)
    }

    @Test
    fun `a growing stretch is re-marked, and only a final one moves the start`() {
        val start = page("The cat sat on the mat.")
        val partial = start.say("the cap", final = false)
        assertEquals(listOf(READ, UNREAD, UNREAD, UNREAD, UNREAD, UNREAD), partial.marks)

        val corrected = partial.say("the cat sat", final = false)
        assertEquals(3, corrected.next)

        val settled = corrected.say("the cat sat", final = true).say("on the mat", final = false)
        assertEquals(List(6) { READ }, settled.marks)
        assertTrue(settled.finished)
    }

    @Test
    fun `noise moves nothing and the report counts judged words only`() {
        val along = page("The year 2024 was long.").say("um").say("the year twenty twenty four is long")

        assertEquals(listOf(READ, READ, PASSED, MISREAD, READ), along.marks)
        val report = along.report()
        assertEquals(3, report.read)
        assertEquals(listOf(3), report.toCheck)
        assertEquals(4, report.judged)
        assertEquals(75, report.accuracyPercent)
    }

    @Test
    fun `hindi and bengali keys ignore nukta, joiners and candrabindu`() {
        assertEquals(PageWords.key("पढ़ना"), PageWords.key("पढना"))
        assertEquals(PageWords.key("हँसना"), PageWords.key("हंसना"))
        assertEquals(PageWords.key("র\u200D্যাব"), PageWords.key("র্যাব"))
        val along = page("আমি বই পড়ি", lang = Lang.BN).say("আমি বই পড়ি")
        assertEquals(List(3) { READ }, along.marks)
    }

    @Test
    fun `words in another script are not judged`() {
        val words = PageWords.of(listOf("मैंने Kindle पर पढ़ा"), Lang.HI)

        assertEquals(listOf(true, false, true, true), words.map { it.checked })
        assertFalse(
            page("मैंने Kindle पर पढ़ा", lang = Lang.HI).say("मैंने किंडल पर पढ़ा").report().toCheck.isNotEmpty(),
        )
    }
}
