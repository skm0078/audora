// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/route/ClauseSplitterTest.kt
// Purpose: Where a sentence is cut for pauses, and where it must not be (RI-6): every mark in en, hi and bn.

package io.github.skm0078.audora.speech.route

import io.github.skm0078.audora.core.model.ClauseMark
import io.github.skm0078.audora.core.model.ClauseMark.BRACKET
import io.github.skm0078.audora.core.model.ClauseMark.COLON
import io.github.skm0078.audora.core.model.Lang
import org.junit.Assert.assertEquals
import org.junit.Test

class ClauseSplitterTest {
    private val router = ScriptRouter()

    /** "text" for a piece, "|MARK|" before it, "(text)" for an aside. */
    private fun cut(text: String, lang: Lang = Lang.EN): String =
        ClauseSplitter.split(router.route(text, lang)).joinToString(" ") { clause ->
            val mark = clause.pauseBefore?.let { "|${it.name}| " }.orEmpty()
            mark + if (clause.aside) "(${clause.text})" else clause.text
        }

    @Test
    fun `each strong mark cuts with its own pause`() {
        val cases = listOf(
            "Wait — then go." to "Wait |DASH| then go.",
            "Wait -- then go." to "Wait |DASH| then go.",
            "Wait - then go." to "Wait |DASH| then go.",
            "Wait – then go." to "Wait |DASH| then go.",
            "Note: read this." to "Note |COLON| read this.",
            "First; second." to "First |SEMICOLON| second.",
            "A word (an aside) here." to "A word |BRACKET| (an aside) |BRACKET| here.",
            "A word [an aside] here." to "A word |BRACKET| (an aside) |BRACKET| here.",
            "He said “go now” and left." to "He said |QUOTE| go now |QUOTE| and left.",
            "He said \"go now\" and left." to "He said |QUOTE| go now |QUOTE| and left.",
            "So… we wait." to "So |ELLIPSIS| we wait.",
            "So... we wait." to "So |ELLIPSIS| we wait.",
        )
        for ((text, expected) in cases) assertEquals(text, expected, cut(text))
    }

    @Test
    fun `commas, ranges, times, links and lone closing brackets never cut`() {
        val cases = listOf(
            "Red, green, and blue.",
            "Pages 10–20 and 10 – 20 and 1990—2000.",
            "Meet at 10:30 today.",
            "See https://example.org now.",
            "COVID-19 and well-known words.",
            "a) first item",
            "Is it? Yes!",
            "Value 3.14 here.",
        )
        for (text in cases) assertEquals(text, text, cut(text))
    }

    @Test
    fun `marks at the ends add nothing, and touching marks give the stronger pause`() {
        assertEquals("As follows", cut("As follows:"))
        assertEquals("go now", cut("“go now”"))
        assertEquals("Wait", cut("Wait..."))
        assertEquals("One |COLON| (two) |BRACKET| three.", cut("One: (two) three."))
        assertEquals("One |ELLIPSIS| two.", cut("One — … two."))
    }

    @Test
    fun `Hindi and Bengali marks cut the same way, and a danda inside the text pauses`() {
        val cases = listOf(
            Lang.HI to ("नियम यह है: सभी को पढ़ना है।" to "नियम यह है |COLON| सभी को पढ़ना है।"),
            Lang.HI to ("पहला भाग — दूसरा भाग।" to "पहला भाग |DASH| दूसरा भाग।"),
            Lang.HI to ("राम (मोहन) आए।" to "राम |BRACKET| (मोहन) |BRACKET| आए।"),
            Lang.HI to ("पहली पंक्ति। दूसरी पंक्ति॥" to "पहली पंक्ति |DANDA| दूसरी पंक्ति॥"),
            Lang.BN to ("নিয়ম হল: সবাই পড়বে।" to "নিয়ম হল |COLON| সবাই পড়বে।"),
            Lang.BN to ("প্রথম অংশ; দ্বিতীয় অংশ।" to "প্রথম অংশ |SEMICOLON| দ্বিতীয় অংশ।"),
            Lang.BN to ("তিনি বললেন “এখন যাও” এবং গেলেন।" to "তিনি বললেন |QUOTE| এখন যাও |QUOTE| এবং গেলেন।"),
        )
        for ((lang, case) in cases) assertEquals(case.first, case.second, cut(case.first, lang))
    }

    @Test
    fun `a mixed sentence keeps its routing, and a bracket can span voices`() {
        val clauses = ClauseSplitter.split(router.route("Read this (इसी घोषणा को) now.", Lang.EN))

        assertEquals(listOf("Read this", "इसी घोषणा को", "now."), clauses.map { it.text })
        assertEquals(listOf(Lang.EN, Lang.HI, Lang.EN), clauses.map { it.lang })
        assertEquals(listOf(null, BRACKET, BRACKET), clauses.map { it.pauseBefore })
        assertEquals(listOf(false, true, false), clauses.map { it.aside })

        // A short English run inside Hindi stays with the Hindi voice, as without the cut.
        val hindi = ClauseSplitter.split(router.route("यह (UN) रिपोर्ट है।", Lang.HI))
        assertEquals(listOf(Lang.HI, Lang.HI, Lang.HI), hindi.map { it.lang })
    }

    @Test
    fun `a mark where the voice changes still pauses`() {
        val clauses = ClauseSplitter.split(router.route("The rule for everyone here: इसी को पढ़ें।", Lang.EN))

        assertEquals(listOf<ClauseMark?>(null, COLON), clauses.map { it.pauseBefore })
        assertEquals(listOf("The rule for everyone here", "इसी को पढ़ें।"), clauses.map { it.text })
        assertEquals(listOf(Lang.EN, Lang.HI), clauses.map { it.lang })
    }
}
