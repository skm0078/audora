// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/quality/PageQualityScorerTest.kt
// Purpose: Page routing by the quality scorer on synthetic pages, and the junk and invalid-sequence counters.

package io.github.skm0078.audora.pdf.quality

import io.github.skm0078.audora.core.config.QualityConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.pdf.extract.PageTextAssembler
import io.github.skm0078.audora.pdf.extract.RawChars
import io.github.skm0078.audora.pdf.extract.RawPageFixture
import io.github.skm0078.audora.pdf.extract.SyntheticChar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageQualityScorerTest {
    private val scorer = PageQualityScorer()

    private fun score(chars: List<SyntheticChar>, fontNames: List<String> = listOf("Body", "Heading")): PageQuality =
        scorer.score(PageTextAssembler().assemble(RawPageFixture.page(chars, fontNames = fontNames)))

    private fun score(text: String) = score(RawPageFixture.chars(text))

    private fun repeat(sentence: String, times: Int = 12) = List(times) { sentence }.joinToString(" ")

    @Test
    fun `clean Unicode Hindi, Bengali and English stay on the text layer`() {
        val hindi = score(repeat("मानव अधिकारों की सार्वभौम घोषणा को स्वीकृत और घोषित किया।"))
        val bengali = score(repeat("মানবাধিকারের সার্বজনীন ঘোষণাপত্র বোঝা কৌশল।"))
        val english = score(repeat("The quick brown fox reads a book aloud."))
        listOf(hindi, bengali, english).forEach {
            assertEquals(PageRoute.TEXT_LAYER, it.route)
            assertEquals(RouteReason.TEXT_LAYER_OK, it.reason)
        }
        assertTrue(hindi.isIndic && bengali.isIndic)
        assertFalse(english.isIndic)
    }

    @Test
    fun `a page with almost no text is a scan`() {
        val quality = score("Page 3")
        assertEquals(PageRoute.OCR, quality.route)
        assertEquals(RouteReason.TOO_LITTLE_TEXT, quality.reason)
    }

    @Test
    fun `a mostly invisible text layer is replaced by our own OCR`() {
        val chars = RawPageFixture.chars(repeat("Scanned words under an image.")).map {
            it.copy(renderMode = RawChars.RENDER_MODE_INVISIBLE)
        }
        assertEquals(RouteReason.HIDDEN_TEXT_LAYER, score(chars).reason)
    }

    @Test
    fun `Unicode-map errors above 2 percent go to OCR`() {
        val chars = RawPageFixture.chars(repeat("Words with a few broken glyphs.")).mapIndexed { i, c ->
            if (i % 20 == 0) c.copy(mapError = true) else c
        }
        assertEquals(RouteReason.UNICODE_MAP_ERRORS, score(chars).reason)
    }

    @Test
    fun `legacy-font runs go to the converter, a Unicode font with a legacy name does not`() {
        val legacy = RawPageFixture.chars(repeat("vkt dh rkt[kcj gS"))
        val quality = score(legacy, listOf("ABCDEF+KrutiDev010", "Body"))
        assertEquals(RouteReason.LEGACY_FONT, quality.reason)
        assertEquals(PageRoute.LEGACY_FONT, quality.route)
        // Judged on its converted text: a Hindi page, OCR'd in Hindi if it ever needs OCR.
        assertTrue(quality.isIndic)
        assertEquals(setOf(Lang.HI), quality.ocrLangs)
        assertEquals(0f, quality.signals.legacyUnknownRate)
        val unicode = RawPageFixture.chars(repeat("मानव अधिकारों की घोषणा।"))
        assertEquals(RouteReason.TEXT_LAYER_OK, score(unicode, listOf("ChanakyaUni", "Body")).reason)
    }

    @Test
    fun `legacy text the converter cannot read goes to OCR`() {
        // ©, ®, Ð and þ are no Kruti Dev codes: a font variant the tables do not know.
        val variant = RawPageFixture.chars(repeat("vkt dh ©®Ðþ ©®Ðþ gS"))
        assertEquals(RouteReason.LEGACY_UNKNOWN_GLYPHS, score(variant, listOf("KrutiDev010", "Body")).reason)
        val shusha = RawPageFixture.chars(repeat("vkt dh rkt[kcj gS"))
        val quality = score(shusha, listOf("Shusha02", "Body"))
        assertEquals(RouteReason.LEGACY_UNKNOWN_GLYPHS, quality.reason)
        assertEquals(PageRoute.OCR, quality.route)
    }

    @Test
    fun `Bengali a-phala after a vowel is valid`() {
        assertEquals(0, TextDefects.invalidIndicChars("অ্যাসাইনমেন্ট এ্যাসিড"))
        assertEquals(1, TextDefects.invalidIndicChars("অ্ক"))
    }

    @Test
    fun `junk glued inside Indic words goes to OCR`() {
        assertEquals(RouteReason.GARBAGE, score(repeat("घोषि0त कि2या और ऐति? हासिAक कार्य।")).reason)
    }

    @Test
    fun `mis-mapped Indic text with impossible sequences goes to OCR`() {
        // Office exports map र to ि and drop conjuncts: "परिचर्याकारीदेर" comes out "পরিচর্যাকািীদেি".
        val quality = score(repeat("প্রাপক ও পরিচর্যাকািীদেি জন্য তথ্যপত্র"))
        assertEquals(RouteReason.INVALID_INDIC, quality.reason)
        assertEquals(PageRoute.OCR, quality.route)
    }

    @Test
    fun `a long Indic page that lost its conjuncts goes to OCR`() {
        // A publisher font whose map leaves every conjunct glyph unmapped: "সাহিত্য" extracts as "সাহিত", "প্রথম
        // প্রকাশ" as "থম কাশ". Every letter left is possible, so only the missing virama shows it.
        val lost = score(repeat("শিশু সাহিত সংসদের থম কাশ হল জানুয়ারি মাসে, তার বই পড়ে সবাই খুশি হল৷"))
        assertEquals(RouteReason.LOST_CONJUNCTS, lost.reason)
        assertEquals(PageRoute.OCR, lost.route)
        assertEquals(setOf(Lang.BN), lost.ocrLangs)
        val hindi = score(repeat("बालक ने पहली बार साहित सभा में भाग लिया और सब लोग बहुत खुश हुए।"))
        assertEquals(RouteReason.LOST_CONJUNCTS, hindi.reason)
    }

    @Test
    fun `the same page with its conjuncts, or too short to tell, stays on the text layer`() {
        val whole = score(repeat("শিশু সাহিত্য সংসদের প্রথম প্রকাশ হল জানুয়ারি মাসে, তার বই পড়ে সবাই খুশি হল।"))
        assertEquals(RouteReason.TEXT_LAYER_OK, whole.reason)
        val short = score(repeat("শিশু সাহিত সংসদের থম কাশ হল জানুয়ারি মাসে৷", times = 3))
        assertEquals(RouteReason.TEXT_LAYER_OK, short.reason)
        assertTrue(short.signals.indicLetters < QualityConfig().minConjunctCheckLetters)
    }

    @Test
    fun `visual-order Indic text stays on the text layer because the reorder pass fixes it`() {
        val quality = score(repeat("िकताब और िकसान ने मानिसक ढंग से हाउिसंग पर बात की।"))
        assertEquals(RouteReason.TEXT_LAYER_OK, quality.reason)
        assertTrue(quality.indicOrder.wordInitialPrebase > 0)
    }

    @Test
    fun `garbage counter`() {
        val cases = listOf(
            "घोषि0त" to 1,
            "सदस्ये\$%र" to 2,
            "हाउजिंग-एर" to 0,
            "कोविड-19 का टीका" to 0,
            "PDF फ़ाइल" to 0,
            "a�bc" to 2,
            "line-end\u0002hyphen\ttab" to 0,
            "bell\u0007" to 1,
        )
        cases.forEach { (text, expected) -> assertEquals(text, expected, TextDefects.garbageChars(text)) }
    }

    @Test
    fun `invalid Indic sequence counter`() {
        val cases = listOf(
            "किताब" to 0,
            "िकताब" to 1,
            "কািী" to 2,
            "्क" to 1,
            "ं" to 1,
            "अं" to 0,
            "र‍्य" to 0,
            "़क" to 1,
        )
        cases.forEach { (text, expected) -> assertEquals(text, expected, TextDefects.invalidIndicChars(text)) }
    }
}
