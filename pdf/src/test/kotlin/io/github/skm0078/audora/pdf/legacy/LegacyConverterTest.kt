// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/legacy/LegacyConverterTest.kt
// Purpose: Table-driven checks of the Kruti Dev, DevLys, Chanakya and Bijoy converters on words from real files.

package io.github.skm0078.audora.pdf.legacy

import io.github.skm0078.audora.core.config.LegacyEncoding
import io.github.skm0078.audora.core.config.QualityConfig
import io.github.skm0078.audora.pdf.cleanup.MappedText
import io.github.skm0078.audora.pdf.extract.PageTextAssembler
import io.github.skm0078.audora.pdf.extract.RawPageFixture
import java.text.Normalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyConverterTest {
    private fun convert(text: String, encoding: LegacyEncoding): LegacyConversion =
        LegacyConverter.convert(MappedText.plain(text), LegacyRuns(listOf(LegacyRun(0, text.length, encoding)), 0))

    private fun check(encoding: LegacyEncoding, cases: List<Pair<String, String>>) {
        for ((legacy, unicode) in cases) {
            val converted = convert(legacy, encoding)
            // Nukta letters (ड़, য়) may come precomposed or not; NFC is P3-02's, so compare normalized.
            assertEquals("\"$legacy\"", nfc(unicode), nfc(converted.text.text))
            assertEquals("\"$legacy\" has no unknown codes", 0, converted.unknown)
        }
    }

    private fun nfc(s: String) = Normalizer.normalize(s, Normalizer.Form.NFC)

    /** Phrases as PDFium extracts them from the S2 legacy-font files (test-pdfs/MANIFEST.md), spaces included. */
    @Test
    fun `Kruti Dev`() = check(
        LegacyEncoding.KRUTIDEV,
        listOf(
            "izsl foKfIr" to "प्रेस विज्ञप्ति",
            "vFkZ ,oa la[;k izHkkx" to "अर्थ एवं संख्या प्रभाग",
            // PDFium's space between i and z (्र drawn apart) is dropped; ” is श in Kruti Dev 010.
            "mRrj i zns”k ljdkj" to "उत्तर प्रदेश सरकार",
            "vk/kkj o’kZ" to "आधार वर्ष",
            "fnukad% ebZ" to "दिनांकः मई",
            "vkS|ksfxd mRiknu lwpdkad" to "औद्योगिक उत्पादन सूचकांक",
            "rRdky dkjZokbZ" to "तत्काल कार्रवाई",
            "okÆ\"kd" to "वार्षिक",
            "dk;ksaZ" to "कार्यों",
            "fLFkfr" to "स्थिति",
            "NŸkhlx<+" to "छत्तीसगढ़",
            "ih0,l0;w0" to "पी॰एस॰यू॰",
            "vuqØef.kdk" to "अनुक्रमणिका",
            "rS;kj fd;s tkrs gSaA" to "तैयार किये जाते हैं।",
        ),
    )

    @Test
    fun `signs typed out of order are put in Unicode order`() = check(
        LegacyEncoding.KRUTIDEV,
        listOf(
            // ं typed before ु, nukta after the vowel sign, ्र after the vowel sign.
            "uiaqld" to "नपुंसक",
            "vkdMs+" to "आकड़े",
            "Vsªu" to "ट्रेन",
            // A ृ drawn under व is extracted between ि and the cluster it belongs before.
            "of`)" to "वृद्धि",
        ),
    )

    @Test
    fun `DevLys draws श at 0x92`() = check(
        LegacyEncoding.DEVLYS,
        listOf(
            "fo’ks\"k" to "विशेष",
            "eq[;ea=h dh v/;{krk" to "मुख्यमंत्री की अध्यक्षता",
            "d‚y lsaVj" to "कॉल सेंटर",
            "g qbZ" to "हुई",
            "mUgk sau s fun sZ'k fn;k" to "उन्होंने निर्देश दिया",
            "O;fä" to "व्यक्ति",
        ),
    )

    @Test
    fun `Chanakya, as the Gazette of India sets it`() = check(
        LegacyEncoding.CHANAKYA,
        listOf(
            "jftLVªh laö Mhö ,yö&33004@99" to "रजिस्ट्री सं. डी. एल.-33004/99",
            "Hkkjr dk jkti=k" to "भारत का राजपत्र",
            "izkf/dkj ls izdkf'kr" to "प्राधिकार से प्रकाशित",
            "vlk/kj.k" to "असाधारण",
            "ubZ fnYyh] 'kqØokj" to "नई दिल्ली, शुक्रवार",
        ),
    )

    @Test
    fun `Bijoy (SutonnyMJ)`() = check(
        LegacyEncoding.BIJOY,
        listOf(
            "MYcÖRvZš¿x evsjv‡`k miKvi" to "গণপ্রজাতন্ত্রী বাংলাদেশ সরকার",
            "Aa¨‡ÿi Kvh©vjq" to "অধ্যক্ষের কার্যালয়",
            "RvZxq ï×vPvi †KŠkj Kg©-cwiKíbv" to "জাতীয় শুদ্ধাচার কৌশল কর্ম-পরিকল্পনা",
            "Øv`k †kÖwYi wbev©Pbx cixÿv" to "দ্বাদশ শ্রেণির নির্বাচনী পরীক্ষা",
            "cÖvß e¨w³/c`" to "প্রাপ্ত ব্যক্তি/পদ",
            "cÖwZkÖæwZ" to "প্রতিশ্রুতি",
            "m¤úbœ" to "সম্পন্ন",
            "‡UÛv‡ii gva¨‡g" to "টেন্ডারের মাধ্যমে",
            "†gvt" to "মোঃ",
            "2021 mv‡ji" to "২০২১ সালের",
            "A¨vmvBb‡g›U" to "অ্যাসাইনমেন্ট",
        ),
    )

    @Test
    fun `text outside legacy runs passes through and offsets stay ascending`() {
        val text = "Press izsl foKfIr 2021"
        val runs = LegacyRuns(listOf(LegacyRun(6, 17, LegacyEncoding.KRUTIDEV)), 0)
        val converted = LegacyConverter.convert(MappedText.plain(text), runs).text
        assertEquals("Press प्रेस विज्ञप्ति 2021", converted.text)
        assertTrue(converted.sources.toList().zipWithNext().all { (a, b) -> a <= b })
        // Every converted letter points into the legacy run it came from.
        assertTrue(converted.sources.slice(6 until converted.text.length - 5).all { it in 6 until 17 })
    }

    @Test
    fun `a word split across a Kruti Dev and a Chanakya run is converted as one`() {
        // Real files switch fonts inside a word; the pre-base ि of one run must reach the cluster in the next.
        val text = "fLFkfr"
        val runs = LegacyRuns(
            listOf(LegacyRun(0, 1, LegacyEncoding.DEVLYS), LegacyRun(1, text.length, LegacyEncoding.CHANAKYA)),
            0,
        )
        assertEquals("स्थिति", LegacyConverter.convert(MappedText.plain(text), runs).text.text)
    }

    /** [text] as PDFium extracts it: the characters in [chanakya] set in a Chanakya font, the rest in DevLys. */
    private fun mixedFonts(text: String, chanakya: Set<Int>): LegacyConversion {
        val chars = RawPageFixture.chars(text).mapIndexed { i, c -> c.copy(fontId = if (i in chanakya) 1 else 0) }
        val page = PageTextAssembler().assemble(
            RawPageFixture.page(chars, fontNames = listOf("WSUMMA+DevLys010", "IKWZYE+Chanakya")),
        )
        return LegacyConverter.convert(MappedText.plain(page.text), LegacyRuns.of(page, QualityConfig()))
    }

    @Test
    fun `a Chanakya glyph inside a DevLys word is a supplement conjunct, not Gazette Chanakya`() {
        // The Rajasthan Board paper (ADR-032): "f" and "V" set alone in Chanakya draw द्ध and ङ्क.
        val siddhi = mixedFonts("flffdk;aZ", chanakya = setOf(3))
        assertEquals(nfc("सिद्धिकार्यं"), nfc(siddhi.text.text))
        assertEquals(0, siddhi.unknown)
        assertEquals(nfc("निम्नांङ्कितेषु"), nfc(mixedFonts("fuEukafVrs\"kq", chanakya = setOf(7)).text.text))
    }

    @Test
    fun `whole words in Chanakya keep the Gazette table`() {
        // Gazette pages switch between Chanakya fonts and set whole words in them: "f" stays the pre-base ि.
        assertEquals("स्थिति", mixedFonts("fLFkfr", chanakya = setOf(1, 2, 3, 4, 5)).text.text)
        assertEquals("प्राधिकार", mixedFonts("izkf/dkj", chanakya = (0 until 8).toSet()).text.text)
    }

    @Test
    fun `unknown codes are counted and dropped, an unsupported layout is all unknown`() {
        val known = convert("izsl ©", LegacyEncoding.KRUTIDEV)
        assertEquals("प्रेस ", known.text.text)
        assertEquals(1, known.unknown)
        val unsupported = convert("vkt dh rkt[kcj", LegacyEncoding.UNSUPPORTED)
        assertEquals(unsupported.letters, unsupported.unknown)
        assertTrue(unsupported.letters > 0)
    }
}
