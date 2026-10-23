// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/document/IntentClassifierTest.kt
// Purpose: Signals and the decision list tell a story, storybook, poem, scripture, news, report and study (ADR-056).

package io.github.skm0078.audora.pdf.document

import io.github.skm0078.audora.core.model.ContentIntent
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.TextBox
import io.github.skm0078.audora.core.model.UnitKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IntentClassifierTest {
    private val classifier = IntentClassifier()

    private fun unit(
        text: String,
        kind: UnitKind = UnitKind.BODY,
        bounds: List<TextBox>? = null,
        lang: Lang = Lang.EN,
    ) = ReadingUnit(0, 0, 0, text.length, text, lang, 0, bounds, kind)

    /** [sentences] repeated until the page has at least [words] words. */
    private fun page(vararg sentences: String, words: Int = 400, lang: Lang = Lang.EN): List<ReadingUnit> {
        val out = mutableListOf<ReadingUnit>()
        var count = 0
        while (count < words) {
            sentences.forEach { text ->
                out.add(unit(text, lang = lang))
                count += text.split(' ').size
            }
        }
        return out
    }

    private fun intentOf(units: List<ReadingUnit>): ContentIntent? = classifier.classify(IntentSignals.of(units, 0f))

    @Test
    fun `too little text is not told yet`() {
        assertNull(intentOf(listOf(unit("A short page of a few words."))))
    }

    @Test
    fun `stories are told in English, Hindi and Bengali`() {
        val en = page(
            "“Where are you going?” asked Mira, pulling her coat tighter against the wind that came off the river.",
            "The old man looked at her for a long moment before he answered, and then he only shook his head.",
        )
        val hi = page(
            "“तुम कहाँ जा रहे हो?” मीरा ने पूछा और अपनी शॉल को कसकर लपेट लिया क्योंकि नदी से ठंडी हवा आ रही थी।",
            "बूढ़े आदमी ने उसे देर तक देखा और फिर धीरे से अपना सिर हिला दिया, पर उसने कोई उत्तर नहीं दिया।",
            lang = Lang.HI,
        )
        val bn = page(
            "“তুমি কোথায় যাচ্ছ?” মীরা জিজ্ঞেস করল আর নদীর দিক থেকে আসা ঠান্ডা হাওয়ায় চাদরটা আরও শক্ত করে জড়াল।",
            "বুড়ো লোকটা অনেকক্ষণ তার দিকে তাকিয়ে রইল, তারপর শুধু মাথা নাড়ল আর কোনো উত্তর দিল না।",
            lang = Lang.BN,
        )
        listOf(en, hi, bn).forEach { assertEquals(ContentIntent.STORY, intentOf(it)) }
    }

    @Test
    fun `storybooks are stories in short sentences or with a story opener`() {
        val short = page("“Hop, hop!” said the little rabbit.", "The fox ran to the tree.", "He was very sad.")
        assertEquals(ContentIntent.CHILDREN, intentOf(short))
        val hi = page(
            "एक बार की बात है, एक छोटे से गाँव में एक गरीब किसान अपने परिवार के साथ रहता था और खेतों में काम करता था।",
            "वह हर सुबह जल्दी उठता था और सूरज निकलने से पहले ही अपने बैलों को लेकर खेत की ओर चल देता था।",
            lang = Lang.HI,
        )
        assertEquals(ContentIntent.CHILDREN, intentOf(hi))
        val bn = page(
            "অনেক দিন আগে এক দেশে এক রাজা ছিলেন, তাঁর সাত ছেলে আর এক মেয়ে ছিল, আর তিনি প্রজাদের খুব ভালোবাসতেন।",
            "রাজার মেয়েটি ছিল সবার ছোট, আর সে প্রতিদিন বাগানে গিয়ে পাখিদের সঙ্গে গল্প করত আর গান গাইত।",
            lang = Lang.BN,
        )
        assertEquals(ContentIntent.CHILDREN, intentOf(bn))
    }

    @Test
    fun `scripture is told from words of worship and verse marks`() {
        val en = page(
            "And the LORD said unto Moses, Go in unto Pharaoh, and tell him, Thus saith the LORD God of the Hebrews.",
            "Blessed are they that mourn: for they shall be comforted, and thy kingdom shall have no end.",
        )
        assertEquals(ContentIntent.SCRIPTURE, intentOf(en))
        val hi = page(
            "धर्मक्षेत्रे कुरुक्षेत्रे समवेता युयुत्सवः। मामकाः पाण्डवाश्चैव किमकुर्वत सञ्जय॥ १॥",
            "श्री भगवान उवाच, हे अर्जुन, तुम उस आत्मा के लिए शोक करते हो जो शोक के योग्य नहीं है॥ २॥",
            lang = Lang.HI,
        )
        assertEquals(ContentIntent.SCRIPTURE, intentOf(hi))
        val bn = page(
            "হে ঈশ্বর, তুমি আমাদের প্রার্থনা শোনো, তোমার করুণা আমাদের সবার উপরে বর্ষিত হোক চিরকাল।",
            "সদাপ্রভু আমার পালক, আমার অভাব হবে না, তিনি আমাকে সবুজ চারণভূমিতে শয়ন করান।",
            lang = Lang.BN,
        )
        assertEquals(ContentIntent.SCRIPTURE, intentOf(bn))
    }

    @Test
    fun `a page of short ragged lines is a poem, a page of full lines is not`() {
        fun line(i: Int, width: Float) = TextBox(72f, 700f - i * 14f, 72f + width, 690f - i * 14f)
        // Twenty lines of six words, widths ragged between 150 and 300 points.
        val verse = (0 until 20).map { i ->
            unit("The woods are lovely, dark, deep,", bounds = listOf(line(i, if (i % 3 == 0) 300f else 150f)))
        }
        assertEquals(ContentIntent.POETRY, intentOf(verse + page("And miles to go before I sleep.", words = 40)))
        // The same lines set full width with twelve words each read as plain prose.
        val prose = (0 until 20).map { i ->
            unit("The woods are lovely dark and deep and I have promises to keep", bounds = listOf(line(i, 450f)))
        }
        assertEquals(ContentIntent.GENERAL, intentOf(prose))
    }

    @Test
    fun `news is told from agencies and reporting phrases before quotes make it a story`() {
        val en = page(
            "“We will rebuild the bridge,” the minister said on Monday, according to officials at the site (PTI).",
            "Police said the road would stay closed until the inspection was over, a spokesperson told reporters.",
        )
        assertEquals(ContentIntent.NEWS, intentOf(en))
        val hi = page(
            "नई दिल्ली, संवाददाता। पुलिस ने बताया कि सोमवार को हुई बारिश के कारण कई सड़कें बंद रहीं।",
            "सूत्रों के अनुसार प्रशासन ने राहत कार्य तेज कर दिया है और प्रवक्ता ने कहा कि स्थिति नियंत्रण में है।",
            lang = Lang.HI,
        )
        assertEquals(ContentIntent.NEWS, intentOf(hi))
        val bn = page(
            "নিজস্ব প্রতিবেদক, কলকাতা। পুলিশ জানিয়েছেন সোমবারের বৃষ্টিতে শহরের বেশ কিছু রাস্তা বন্ধ ছিল।",
            "সূত্র জানায় প্রশাসন ত্রাণের কাজ শুরু করেছে, আর এক মুখপাত্র বলেছেন পরিস্থিতি নিয়ন্ত্রণে আছে।",
            lang = Lang.BN,
        )
        assertEquals(ContentIntent.NEWS, intentOf(bn))
    }

    @Test
    fun `official text and lists of figures are reports`() {
        val hi = page(
            "भारत सरकार के मंत्रालय द्वारा जारी अधिसूचना के अनुसार धारा 4 के अधीन विनियम लागू होंगे।",
            "जिला कार्यालय के अधिकारी दिनांक 12 मार्च तक आदेश का पालन सुनिश्चित करेंगे।",
            lang = Lang.HI,
        )
        assertEquals(ContentIntent.REPORT, intentOf(hi))
        assertEquals(ContentIntent.REPORT, intentOf(page("2019 4,512 3,880 17.2 2020 4,901 4,102 19.6 2021 5,330")))
    }

    @Test
    fun `textbooks are told from structure and teaching words, papers from citations and furniture`() {
        val units = (0 until 6).flatMap { i ->
            listOf(
                unit("$i.1 Sound arguments", UnitKind.HEADING),
                unit("Example: an argument is valid when its premises cannot all be true and its conclusion false."),
                unit("We will look at several such arguments in this chapter and test each of them with a table."),
                unit("A premise is a claim we accept for the sake of the argument.", UnitKind.LIST_ITEM),
            )
        }
        assertEquals(ContentIntent.STUDY, intentOf(units))
        val paper =
            page("Earlier work [4] measured the dose at the surface [12, 13] and found it lower than predicted.")
        assertEquals(ContentIntent.STUDY, intentOf(paper))
    }

    @Test
    fun `plain prose with no cue is general`() {
        val en = page(
            "The valley lies between two ranges of low hills and has been farmed for at least three hundred years.",
            "Its soil is rich, and the river that runs through it floods every few summers after the monsoon.",
        )
        assertEquals(ContentIntent.GENERAL, intentOf(en))
    }

    @Test
    fun `junk below the readable score says nothing`() {
        val junk = page("“Hop!” said the rabbit.").map { it.copy(speakScore = 0.1f) }
        assertNull(classifier.classify(IntentSignals.of(junk, readableFrom = 0.35f)))
    }
}
