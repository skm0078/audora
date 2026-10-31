// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/normalize/BengaliSpellingTest.kt
// Purpose: Bengali words are respelled the way they are said, rule by rule; anything unsure stays as written.

package io.github.skm0078.audora.speech.normalize

import java.text.Normalizer
import org.junit.Assert.assertEquals
import org.junit.Test

class BengaliSpellingTest {
    private fun assertSaid(cases: Map<String, String>) {
        val said = cases.keys.associateWith(BengaliSpelling::respell)
        assertEquals(cases, said)
    }

    private fun assertAsWritten(words: List<String>) = assertSaid(words.associateWith { it })

    @Test
    fun `the words a listener heard wrong are respelled as said`() = assertSaid(
        mapOf(
            "সব" to "সব্", // "shob", not "sobo"
            "চোর" to "চোর্", // "chor", not "choro"
            "কাণ্ড" to "কাণ্ডো", // "kando", not "kand"
            "এমন" to "অ্যামন্", // "æmon"
            "গেল" to "গ্যালো", // "gælo"
            "দেখা" to "দ্যাখা", // "dækha"
        ),
    )

    @Test
    fun `a single consonant at the end is silent where Bengali is regular`() = assertSaid(
        mapOf(
            "দিন" to "দিন্",
            "মাঠ" to "মাঠ্",
            "সুন্দর" to "সুন্দর্",
            "মানুষ" to "মানুষ্",
            "পাহাড়" to "পাহাড়্",
            "রাত" to "রাত্", // ত is listed word by word
            "জল" to "জল্", // and ল
        ),
    )

    @Test
    fun `a cluster at the end keeps its vowel`() = assertSaid(
        mapOf(
            "গল্প" to "গল্পো",
            "শব্দ" to "শব্দো",
            "কষ্ট" to "কষ্টো",
            "আনন্দ" to "আনন্দো",
            "ধর্ম" to "ধর্মো",
            "স্বপ্ন" to "স্বপ্নো",
        ),
    )

    @Test
    fun `a past, future or habitual verb is spoken to its end`() = assertSaid(
        mapOf(
            "করল" to "করলো",
            "বলব" to "বলবো",
            "চলত" to "চলতো",
            "তাকাল" to "তাকালো",
            "দাঁড়াল" to "দাঁড়ালো",
            "বলছিল" to "বলছিলো",
            "করিল" to "করিলো",
            "করিয়াছ" to "করিয়াছো",
            "হল" to "হলো",
            "যাব" to "যাবো",
        ),
    )

    @Test
    fun `visarga, anusvara, ha and chha endings follow their own rules`() = assertSaid(
        mapOf(
            "দুঃখ" to "দুঃখো",
            "অংশ" to "অংশো",
            "দেহ" to "দেহো",
            "মাছ" to "মাছ্", // after "আ", silent
            "করছ" to "করছো", // a "tumi" verb, spoken
        ),
    )

    @Test
    fun `old spellings of eleven to eighteen keep their vowel`() = assertSaid(
        mapOf("এগার" to "এগারো", "তের" to "তেরো", "পনের" to "পনেরো", "ষোল" to "ষোলো", "সতের" to "সতেরো"),
    )

    @Test
    fun `what the rules cannot be sure of stays as written`() = assertAsWritten(
        listOf(
            "কর", // a bare verb: "kor" or "koro"
            "আসল", // "ashol" (real) or "ashlo" (came)
            "মানব", // "manob" (human) or "manbo" (will accept)
            "বার", // "bar" (times) or "baro" (twelve)
            "কোন", // "kon" (which) or "kono" (any)
            "টেবিল", // an unlisted ল ending
            "সত্য", // য-ফলা
            "লাস্ট", "পার্ক", "ফর্ম", // English loans
            "আল্লাহ", "শাহ", // হ after "আ"
            "ব্যাংক", // ং before ক
            "ক", // one letter
            "জগৎ", // khanda ta is already closed
            "নদী", "কথা", "ভালো", // a vowel at the end
        ),
    )

    @Test
    fun `only Bengali words change, with every mark and space between them kept`() {
        val text = "সব ঠিক আছে, তাই না? \"চোর-ডাকাত\" ধরল! Page 12, ১২ আর 3.5"

        assertEquals("সব্ ঠিক্ আছে, তাই না? \"চোর্-ডাকাত্\" ধরলো! Page 12, ১২ আর্ 3.5", BengaliSpelling.respell(text))
    }

    @Test
    fun `text with no Bengali letters is returned as it is`() {
        val text = "Hello, world. नमस्ते"

        assertEquals(text, BengaliSpelling.respell(text))
    }

    @Test
    fun `a nukta letter matches however the page composed it`() {
        val composed = "\u09AA\u09BE\u09B9\u09BE\u09DC" // পাহাড় with ড় as one character
        val decomposed = Normalizer.normalize(composed, Normalizer.Form.NFC)

        assertEquals(composed + HASANTA, BengaliSpelling.respell(composed))
        assertEquals(decomposed + HASANTA, BengaliSpelling.respell(decomposed))
        // বড় with ড় as one character, found in the list all the same.
        assertEquals("\u09AC\u09A1\u09BC\u09CB", BengaliSpelling.respell("\u09AC\u09DC"))
    }

    @Test
    fun `a word joined by a zero-width joiner stays one word`() {
        val word = "\u09B0\u200D\u09AF\u09BE\u09AC" // র, a joiner, যাব: one word, not র and যাব ("যাবো")

        assertEquals(word, BengaliSpelling.respell(word))
    }

    @Test
    fun `respelling twice changes nothing more`() {
        val words = BengaliSpellingWords.said.keys + listOf("সব", "চোর", "কাণ্ড", "করল", "দুঃখ", "দেহ", "মাছ")
        val once = words.map(BengaliSpelling::respell)

        assertEquals(once, once.map(BengaliSpelling::respell))
    }

    @Test
    fun `a listed word keeps its consonants, gaining only marks or the ya of a wide e`() {
        val consonants = { text: String -> text.filter { it in '\u0995'..'\u09B9' && it != '\u09AF' } } // ক to হ, not য
        BengaliSpellingWords.said.forEach { (word, said) ->
            assertEquals("$word is said $said", consonants(word), consonants(said))
        }
    }

    @Test
    fun `no word is both respelled by name and left as written`() {
        // A word moved to the as-written lists but left in a said list would still be respelled, as said wins.
        assertEquals(emptySet<String>(), BengaliSpellingWords.said.keys intersect BengaliSpellingWords.asWritten)
    }

    private companion object {
        const val HASANTA = '\u09CD'
    }
}
