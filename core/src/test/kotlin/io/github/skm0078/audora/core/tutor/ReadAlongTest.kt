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
            Case("short words must match", "It is in.", "it is on", listOf(READ, READ, MISREAD)),
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

    @Test
    fun `going back over what was read changes nothing`() {
        val twoSentences = page("The cat sat on the mat.", "Then the dog sat on the rug.")
        val reread = twoSentences.say("the cat sat on the mat").say("the cat sat on the mat")
        assertEquals(List(6) { READ } + List(7) { UNREAD }, reread.marks)
        assertEquals(6, reread.next)

        val walked = page("He walked into the dark room.", "The floor was cold.")
            .say("he walked into the")
            .say("and walked into the")
            .say("dark room the floor was cold")
        assertEquals(List(10) { READ }, walked.marks)

        val hindi = page("वह थक गया और वह सो गया।", "फिर सुबह हुई।", lang = Lang.HI)
            .say("वह थक गया और वह सो गया")
            .say("और वह सो गया")
            .say("फिर सुबह हुई")
        assertEquals(List(10) { READ }, hindi.marks)
    }

    @Test
    fun `a page ending in an unjudged word or a misread word still finishes`() {
        assertTrue(page("He was born in 1995.").say("he was born in").finished)
        assertTrue(page("मैंने यह किताब Kindle", lang = Lang.HI).say("मैंने यह किताब").finished)
        assertTrue(page("We met at 5 p.m.").say("we met at").finished)

        val misreadLast = page("The cat sat on the mat.").say("the cat sat on the map")
        assertEquals(List(5) { READ } + MISREAD, misreadLast.marks)
        assertTrue(misreadLast.finished)
        val filler = page("The cat sat on the mat.").say("the cat sat on the the")
        assertEquals(List(5) { READ } + UNREAD, filler.marks)
        assertFalse(filler.finished)
        assertTrue(filler.say("mat").finished)
        assertFalse(page("The cat sat on the mat.").say("um").finished)
        assertEquals(List(6) { UNREAD }, page("The cat sat on the mat.").say("um").marks)
    }

    @Test
    fun `joiners and soft hyphens keep a word whole`() {
        val cases = listOf(
            "র\u200D্যাব" to Lang.BN,
            "উত্\u200Dসব" to Lang.BN,
            "क्\u200Dष" to Lang.HI,
            "read\u00ADing" to Lang.EN,
        )
        cases.forEach { (word, lang) ->
            val words = PageWords.of(listOf(word), lang)
            assertEquals(word, 1, words.size)
            assertEquals(word, listOf(READ), ReadAlong.start(words).heard(listOf(words[0].key), final = true).marks)
        }
        assertEquals(PageWords.key("উৎসব"), PageWords.key("উত্\u200Dসব"))
        assertEquals(PageWords.key("reading"), PageWords.key("read\u00ADing"))
    }

    @Test
    fun `spelling variants read alike`() {
        listOf(
            "हिन्दी" to "हिंदी",
            "सम्बन्ध" to "संबंध",
            "गङ्गा" to "गंगा",
            "गये" to "गए",
            "नयी" to "नई",
            "लिये" to "लिए",
            "चाहिये" to "चाहिए",
            "कि" to "की",
            "কী" to "কি",
            "হঠাত্\u200D" to "হঠাৎ",
            "হঠাত্" to "হঠাৎ",
        ).forEach { (a, b) -> assertEquals("$a / $b", PageWords.key(a), PageWords.key(b)) }
        assertEquals(PageWords.key("ये"), "ये")
    }

    @Test
    fun `short abbreviations are read any way`() {
        val words = PageWords.of(listOf("Mr. Smith met Dr. Rao at 5 p.m. in St. Mary"), Lang.EN)
        assertEquals(
            listOf("mr", "smith", "met", "dr", "rao", "at", "5", "pm", "in", "st", "mary"),
            words.map { it.key },
        )
        assertEquals(
            listOf(false, true, true, false, true, true, false, false, true, false, true),
            words.map { it.checked },
        )
        assertTrue(PageWords.of(listOf("The cat sat."), Lang.EN).all { it.checked })
        // A word after the dot: an "up to three letters and a dot" pattern would have left Tom and Dad unjudged.
        assertTrue(PageWords.of(listOf("I saw Tom. He waved."), Lang.EN).all { it.checked })
        assertTrue(PageWords.of(listOf("Ask Dad. Then go."), Lang.EN).all { it.checked })
    }

    @Test
    fun `a stretch mostly in another script is recognised`() {
        assertTrue(PageWords.mostlyIn("वह सो गया", Lang.HI))
        assertFalse(PageWords.mostlyIn("وہ سو گیا", Lang.HI))
        assertTrue(PageWords.mostlyIn("मैंने Kindle पर पढ़ा", Lang.HI))
        assertTrue(PageWords.mostlyIn("", Lang.HI))
    }
}
