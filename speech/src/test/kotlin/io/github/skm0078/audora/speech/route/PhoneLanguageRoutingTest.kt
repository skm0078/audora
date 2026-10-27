// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/route/PhoneLanguageRoutingTest.kt
// Purpose: Which parts of a sentence the phone's own voices read: the book's language and other scripts (ADR
//          2026-10-26), table-driven, and that English, Hindi and Bengali books route exactly as before.

package io.github.skm0078.audora.speech.route

import io.github.skm0078.audora.core.config.ScriptRoutingConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.Lang.BN
import io.github.skm0078.audora.core.model.Lang.EN
import io.github.skm0078.audora.core.model.Lang.HI
import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneLanguageRoutingTest {
    private val router = ScriptRouter()

    private class Case(val text: String, val fallback: Lang, val book: String?, vararg val expected: ScriptSegment)

    private fun seg(text: String, lang: Lang, phone: String? = null) = ScriptSegment(text, lang, phone)

    @Test
    fun `table of sentences`() {
        val cases = listOf(
            Case("Le chat dort sur la table.", EN, "fr", seg("Le chat dort sur la table.", EN, "fr")),
            Case("Der Hund schläft.", EN, "de-DE", seg("Der Hund schläft.", EN, "de")),
            // A Marathi book: Devanagari in Marathi; a short English run stays with it, as with Hindi.
            Case("हे PDF पुस्तक आहे.", HI, "mr", seg("हे PDF पुस्तक आहे.", HI, "mr")),
            // A Hindi part inside a French book keeps the Hindi voice: only the book's own script is the phone's.
            Case(
                "Il a dit नमस्ते दोस्त आज.",
                EN,
                "fr",
                seg("Il a dit", EN, "fr"),
                seg("नमस्ते दोस्त आज.", HI),
            ),
            // Scripts none of the three is written in, with no book language.
            Case("வணக்கம் உலகம்.", EN, null, seg("வணக்கம் உலகம்.", EN, "ta")),
            Case(
                "The word is வணக்கம் here.",
                EN,
                null,
                seg("The word is", EN),
                seg("வணக்கம்", EN, "ta"),
                seg("here.", EN),
            ),
            Case("Привет, мир.", EN, null, seg("Привет, мир.", EN, "ru")),
            Case("Привіт, світе.", EN, "uk", seg("Привіт, світе.", EN, "uk")),
            Case("یہ کتاب ہے۔", EN, "ur", seg("یہ کتاب ہے۔", EN, "ur")),
            Case("هذا كتاب.", EN, null, seg("هذا كتاب.", EN, "ar")),
            Case("שלום עולם.", EN, "iw", seg("שלום עולם.", EN, "he")),
            // Han with kana is Japanese, and one run: kana never cut it apart; Han alone is Chinese.
            Case("東京はとても大きい都市です。", EN, null, seg("東京はとても大きい都市です。", EN, "ja")),
            Case("北京是一个大城市。", EN, null, seg("北京是一个大城市。", EN, "zh")),
            Case("北京是一个大城市。", EN, "yue", seg("北京是一个大城市。", EN, "yue")),
            Case("안녕하세요 세계.", EN, null, seg("안녕하세요 세계.", EN, "ko")),
            // Greek in a book not in Greek is maths, read as before; a Greek book reads it in Greek.
            Case("The angle αβγ is small.", EN, null, seg("The angle αβγ is small.", EN)),
            Case("Ο λόγος.", EN, "el", seg("Ο λόγος.", EN, "el")),
            // A short run of another script stays with the voice before it; a word gets its own voice.
            Case("Set Жк here.", EN, null, seg("Set Жк here.", EN)),
            Case(
                "The word слово means word.",
                EN,
                null,
                seg("The word", EN),
                seg("слово", EN, "ru"),
                seg("means word.", EN),
            ),
            // No letters: the book's language reads the numbers, when its script fits the fallback's.
            Case("12.", EN, "fr", seg("12.", EN, "fr")),
            Case("12.", EN, "ta", seg("12.", EN, "ta")),
            Case("१२", HI, "fr", seg("१२", HI)),
            Case("12.", EN, null, seg("12.", EN)),
        )
        for (case in cases) {
            assertEquals(case.text, case.expected.toList(), router.route(case.text, case.fallback, case.book))
        }
    }

    @Test
    fun `English, Hindi, Bengali or no book language route exactly as before`() {
        val sentences = listOf(
            "Hello world." to EN,
            "मानव अधिकारों की घोषणा ।" to HI,
            "মানবাধিকার সবার জন্য।" to BN,
            "यह PDF फ़ाइल है ।" to HI,
            "Read the world, इसी घोषणा को ।" to EN,
            "The angle αβγ is small." to EN,
            "12." to EN,
        )
        for ((text, fallback) in sentences) {
            val before = router.route(text, fallback)
            listOf("en", "hi", "bn", "EN-gb", "", null).forEach { book ->
                assertEquals("$text / $book", before, router.route(text, fallback, book))
            }
            assertEquals(text, true, before.all { it.phone == null })
        }
    }

    @Test
    fun `the other-script threshold is configurable`() {
        val strict = ScriptRouter(ScriptRoutingConfig(minOtherScriptLetters = 1))

        assertEquals(
            listOf(seg("Set", EN), seg("Ж", EN, "ru"), seg("here.", EN)),
            strict.route("Set Ж here.", EN),
        )
    }

    @Test
    fun `parts always add up to the sentence`() {
        listOf(
            "Il a dit नमस्ते दोस्त आज.",
            "The word слово means word.",
            "東京はとても大きい都市です。",
        ).forEach { text ->
            val joined = router.route(text, EN, "fr").joinToString("") { it.text }
            assertEquals(text.filterNot(Char::isWhitespace), joined.filterNot(Char::isWhitespace))
        }
    }
}
