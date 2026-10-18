// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/units/IndicSentenceSplitterTest.kt
// Purpose: Table-driven sentence splitting tests: danda, Indic and English abbreviations and initials, verses.

package io.github.skm0078.audora.pdf.units

import io.github.skm0078.audora.core.model.Lang
import org.junit.Assert.assertEquals
import org.junit.Test

class IndicSentenceSplitterTest {
    private val splitter = BreakIteratorSentenceSplitter()

    private data class Case(val name: String, val lang: Lang, val sentences: List<String>)

    /** The sentences are written with their trailing spaces, so joined they are the input. */
    private fun check(cases: List<Case>) = cases.forEach { case ->
        val text = case.sentences.joinToString("")
        val actual = splitter.split(text, case.lang).map { text.substring(it.first, it.last + 1) }
        assertEquals(case.name, case.sentences, actual)
    }

    @Test
    fun `the danda ends a sentence`() = check(
        listOf(
            Case("Hindi", Lang.HI, listOf("यह पहला वाक्य है। ", "यह दूसरा है।")),
            Case("Bengali", Lang.BN, listOf("এটা প্রথম বাক্য। ", "এটা দ্বিতীয়।")),
            Case("no space after the danda", Lang.HI, listOf("पहला वाक्य।", "दूसरा वाक्य।")),
            Case("double danda", Lang.HI, listOf("पहला श्लोक॥ ", "दूसरा श्लोक॥")),
            Case("question mark", Lang.BN, listOf("তুমি কেমন আছ? ", "আমি ভালো আছি।")),
            Case("danda in an English-detected paragraph", Lang.EN, listOf("Title: यह वाक्य है। ", "अगला।")),
            Case("comma after the danda continues", Lang.HI, listOf("एक।, दो।")),
        ),
    )

    @Test
    fun `closing quotes stay with their sentence`() = check(
        listOf(
            Case("straight quote", Lang.HI, listOf("उसने कहा, \"मैं आऊँगा।\" ", "फिर वह चला गया।")),
            Case("curly quote", Lang.BN, listOf("সে বলল, “আমি যাব।” ", "তারপর সে গেল।")),
            Case("opening quote starts the next sentence", Lang.HI, listOf("वह आया। ", "\"अब चलें,\" उसने कहा।")),
            Case("bracket", Lang.HI, listOf("(यह टिप्पणी है।) ", "अगला वाक्य।")),
        ),
    )

    @Test
    fun `abbreviations and initials do not end a sentence`() = check(
        listOf(
            Case("Hindi doctor", Lang.HI, listOf("डॉ. शर्मा कल आएँगे। ", "वे दिल्ली में हैं।")),
            Case("Hindi initials", Lang.HI, listOf("ए. पी. जे. अब्दुल कलाम राष्ट्रपति थे। ", "वे वैज्ञानिक थे।")),
            Case("Hindi unit with inner stop", Lang.HI, listOf("दूरी ५ कि.मी. है। ", "चलिए।")),
            Case("abbreviation in brackets", Lang.HI, listOf("लेखक (प्रो. वर्मा) ने लिखा। ", "ठीक है।")),
            Case("Bengali doctor", Lang.BN, listOf("ডা. রায় আসবেন। ", "তিনি কলকাতায় থাকেন।")),
            Case("Bengali initials", Lang.BN, listOf("এ. কে. ফজলুল হক বক্তৃতা দেন। ", "সবাই শুনল।")),
            Case("Bengali Mohammad", Lang.BN, listOf("মো. রহিম এসেছেন। ", "বসুন।")),
            Case("Hindi titles", Lang.HI, listOf("श्री. राम और डा. सीता आए। ", "बैठिए।")),
            // Before a word: the JDK breaks there without the entries, where before a digit it does not.
            Case("Hindi references", Lang.HI, listOf("सं. रामचंद्र शुक्ल, पृ. सं. १२, ५०० ई.पू. में। ", "बस।")),
            Case("Bengali titles", Lang.BN, listOf("ড. রহমান, ডঃ. সেন ও শ্রী. বসু এলেন। ", "বসুন।")),
            Case("Bengali era", Lang.BN, listOf("খ্রি.পূ. যুগে এটি তৈরি। ", "বসুন।")),
            // A full stop used as the sentence end after an ordinary word still splits.
            Case("full stop as sentence end", Lang.HI, listOf("यह वाक्य है. ", "दूसरा वाक्य है.")),
        ),
    )

    @Test
    fun `verse numbers are read with their verse`() = check(
        listOf(
            Case("spaced", Lang.HI, listOf("धर्मक्षेत्रे कुरुक्षेत्रे समवेता युयुत्सवः॥ १ ॥ ", "अगला श्लोक॥ २ ॥")),
            Case("unspaced", Lang.HI, listOf("पहला श्लोक॥१॥ ", "दूसरा॥")),
            Case("Bengali digits", Lang.BN, listOf("প্রথম পদ॥ ১ ॥ ", "দ্বিতীয় পদ॥ ২ ॥")),
            Case("a numbered point is not a verse", Lang.HI, listOf("पहली बात। ", "2. ", "दूसरी बात।")),
        ),
    )

    @Test
    fun `English splits as before`() = check(
        listOf(
            Case("two sentences", Lang.EN, listOf("It was late. ", "We left.")),
            Case("quote after the full stop", Lang.EN, listOf("He said \"Stop.\" ", "Then he left.")),
            Case("decimal", Lang.EN, listOf("Pi is 3.14 roughly. ", "Yes.")),
        ),
    )

    @Test
    fun `English titles, Latin abbreviations and initials do not end a sentence`() = check(
        listOf(
            Case("doctor", Lang.EN, listOf("Dr. Smith went home. ", "He slept.")),
            Case("Mr and Mrs", Lang.EN, listOf("Mr. and Mrs. Rao came. ", "Then they left.")),
            Case("professor", Lang.EN, listOf("Prof. Iyer et al. found it. ", "Yes.")),
            Case("Indian titles", Lang.EN, listOf("Shri Ram and Smt. Sita spoke. ", "Sh. Gupta agreed.")),
            Case("ranks", Lang.EN, listOf("Lt. Col. Singh and Capt. Rao met Gen. Bose. ", "Fine.")),
            Case("saint", Lang.EN, listOf("St. Xavier's College is old. ", "It is in Mumbai.")),
            Case("initials", Lang.EN, listOf("A. P. J. Abdul Kalam spoke. ", "M. K. Gandhi listened.")),
            Case("dotted initialism", Lang.EN, listOf("The U.S. Army left. ", "It rained.")),
            Case("Latin", Lang.EN, listOf("Fruit, e.g. Apples, i.e. Red ones, viz. Fuji. ", "Done.")),
            Case("bracketed title", Lang.EN, listOf("The author (Dr. Rao) wrote it. ", "Good.")),
            Case("author list", Lang.EN, listOf("By Abbott B. P., Smith, J. A. Jones and Dr. A. Rao. ", "Fine.")),
            Case("reference", Lang.EN, listOf("See Fig. (A) and No. 5 on p. 7. ", "Done.")),
            Case("rupees", Lang.EN, listOf("Pay Rs. 500 (Rupees Five Hundred) and Re. 1 only. ", "Done.")),
            Case(
                "initials after a small word",
                Lang.EN,
                listOf("Written by S. K. Mukherjee and A. P. J. Kalam. ", "Fine."),
            ),
            Case(
                "offices",
                Lang.EN,
                listOf(
                    "The Govt. Order went to Dy. Director, Addl. Secretary and Asstt. Engineer of X Pvt. Ltd. ",
                    "Ok.",
                ),
            ),
            // Ordinary words, a lone lower-case letter and "etc." still end a sentence.
            Case("etc ends", Lang.EN, listOf("We bought pens, paper etc. ", "Then we went home.")),
            Case("No as an answer", Lang.EN, listOf("Was it him? ", "No. ", "It was her.")),
            // A capital letter that ends a sentence is not an initial: a pronoun, a variable, a label.
            Case("pronoun", Lang.EN, listOf("So says I. ", "Where now?")),
            Case("variable", Lang.EN, listOf("Take the matrix Q. ", "The keys follow.")),
            Case("symbol", Lang.EN, listOf("Write B\u2227 A. ", "But not B.")),
            Case("label", Lang.EN, listOf("See Appendix A. ", "Annexure B. ", "Class X. ", "Plan B. ", "Then stop.")),
            Case(
                "labels shared with the voice",
                Lang.EN,
                listOf("See Article V. ", "Team A. ", "World War I. ", "Then."),
            ),
            // A lone initial after a small word reads like a variable ending a sentence, so it still splits.
            Case("lone initial after a small word", Lang.EN, listOf("Work with K. ", "Rao agreed.")),
        ),
    )

    @Test
    fun `ranges cover the text in order`() {
        val text = "डॉ. शर्मा आए। \"ठीक है।\" उन्होंने कहा॥ १ ॥ अगला।"
        val ranges = splitter.split(text, Lang.HI)
        assertEquals(0, ranges.first().first)
        assertEquals(text.length, ranges.last().last + 1)
        ranges.zipWithNext().forEach { (a, b) -> assertEquals(a.last + 1, b.first) }
        assertEquals(emptyList<IntRange>(), splitter.split("", Lang.HI))
    }
}
