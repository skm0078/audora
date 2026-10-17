// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/normalize/IndicNormalizerTest.kt
// Purpose: Table-driven cases for the Hindi and Bengali normalizers (P3-02): digits in three scripts, number words.

package io.github.skm0078.audora.speech.normalize

import io.github.skm0078.audora.core.model.Lang
import java.text.Normalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class IndicNormalizerTest {
    private data class Case(val group: String, val written: String, val spoken: String)

    private val hindi = IndicNormalizer.forLang(Lang.HI)!!
    private val bengali = IndicNormalizer.forLang(Lang.BN)!!

    private val hindiCases = listOf(
        Case("number", "मेरे पास 3 किताबें हैं।", "मेरे पास तीन किताबें हैं।"),
        Case("devanagari digits", "मेरे पास ३ किताबें हैं।", "मेरे पास तीन किताबें हैं।"),
        Case("number", "कुल ४७ लोग", "कुल सैंतालीस लोग"),
        Case("number", "79 और 99", "उन्यासी और निन्यानबे"),
        Case("zero", "० अंक", "शून्य अंक"),
        Case("hundreds", "३४५ पृष्ठ", "तीन सौ पैंतालीस पृष्ठ"),
        Case("hundred", "100 रुपये", "एक सौ रुपये"),
        Case("thousand", "2,500 लोग", "दो हज़ार पाँच सौ लोग"),
        Case("lakh, Indian grouping", "1,50,000 मतदाता", "एक लाख पचास हज़ार मतदाता"),
        Case("lakh, international grouping", "150,000 मतदाता", "एक लाख पचास हज़ार मतदाता"),
        Case("crore", "१,००,००,००० वोट", "एक करोड़ वोट"),
        Case("crores above 99", "1000 करोड़", "एक हज़ार करोड़"),
        Case("large", "12,34,56,789", "बारह करोड़ चौंतीस लाख छप्पन हज़ार सात सौ नवासी"),
        Case("year", "सन् १९४७ में", "सन् उन्नीस सौ सैंतालीस में"),
        Case("round year", "1900 में", "उन्नीस सौ में"),
        Case("year after 1999", "२०२४ में", "दो हज़ार चौबीस में"),
        Case("decimal", "३.५ किलो", "तीन दशमलव पाँच किलो"),
        Case("percent", "७५% छात्र", "पचहत्तर प्रतिशत छात्र"),
        Case("percent with space", "75 % छात्र", "पचहत्तर प्रतिशत छात्र"),
        Case("rupees", "₹500 का नोट", "पाँच सौ रुपये का नोट"),
        Case("one rupee", "₹1 का सिक्का", "एक रुपये का सिक्का"),
        Case("rupees and paise", "₹12.50 दें", "बारह रुपये पचास पैसे दें"),
        Case("rupees, zero paise", "₹12.00 दें", "बारह रुपये दें"),
        Case("range", "पृष्ठ 10-12 देखें", "पृष्ठ दस से बारह देखें"),
        Case("financial year", "वर्ष २०१९-२० में", "वर्ष दो हज़ार उन्नीस से बीस में"),
        Case("leading zero", "कोड 007", "कोड शून्य शून्य सात"),
        Case("phone number", "फ़ोन 98765-43210", "फ़ोन नौ आठ सात छह पाँच, चार तीन दो एक शून्य"),
        Case("too long", "खाता 1234567890123456", "खाता एक दो तीन चार पाँच छह सात आठ नौ शून्य एक दो तीन चार पाँच छह"),
        Case("list", "1,2,3", "एक,दो,तीन"),
        Case("no digits", "कोई अंक नहीं।", "कोई अंक नहीं।"),
        Case("slash", "हाँ/नहीं चुनें।", "हाँ या नहीं चुनें।"),
        Case("ampersand", "राम & श्याम", "राम और श्याम"),
        Case("short slash stays", "क/ख", "क/ख"),
        Case("question kept", "क्या आप आएँगे? हाँ!", "क्या आप आएँगे? हाँ!"),
        Case("range and slash", "पृष्ठ 10–12 देखें/पढ़ें", "पृष्ठ दस से बारह देखें या पढ़ें"),
    )

    private val bengaliCases = listOf(
        Case("number", "আমার ৩টি বই আছে।", "আমার তিনটি বই আছে।"),
        Case("ascii digits", "আমার 3 টি বই", "আমার তিন টি বই"),
        Case("number", "৪৭ জন", "সাতচল্লিশ জন"),
        Case("number", "৮৯ আর ৯৯", "ঊননব্বই আর নিরানব্বই"),
        Case("hundreds", "৩৪৫ পৃষ্ঠা", "তিনশো পঁয়তাল্লিশ পৃষ্ঠা"),
        Case("hundred", "১০০ টাকা", "একশো টাকা"),
        Case("thousand", "২,৫০০ জন", "দুই হাজার পাঁচশো জন"),
        Case("lakh", "১,৫০,০০০ ভোটার", "এক লাখ পঞ্চাশ হাজার ভোটার"),
        Case("crore", "১,০০,০০,০০০", "এক কোটি"),
        Case("year", "১৯৪৭ সালে", "উনিশশো সাতচল্লিশ সালে"),
        Case("year after 1999", "২০২৪ সালে", "দুই হাজার চব্বিশ সালে"),
        Case("decimal", "৩.৫ কেজি", "তিন দশমিক পাঁচ কেজি"),
        Case("percent", "৭৫% ছাত্র", "পঁচাত্তর শতাংশ ছাত্র"),
        Case("rupees", "₹৫০০ দিন", "পাঁচশো টাকা দিন"),
        Case("rupees and paise", "₹১২.৫০", "বারো টাকা পঞ্চাশ পয়সা"),
        Case("range", "পৃষ্ঠা ১০-১২", "পৃষ্ঠা দশ থেকে বারো"),
        Case("leading zero", "০০৭", "শূন্য শূন্য সাত"),
        Case("no digits", "কোনো সংখ্যা নেই।", "কোনো সংখ্যা নেই।"),
        Case("slash", "হ্যাঁ/না বলুন।", "হ্যাঁ বা না বলুন।"),
        Case("ampersand", "রাম & শ্যাম", "রাম এবং শ্যাম"),
        Case("question kept", "আপনি আসবেন? হ্যাঁ!", "আপনি আসবেন? হ্যাঁ!"),
    )

    private fun check(normalizer: IndicNormalizer, cases: List<Case>) = cases.forEach { case ->
        assertEquals(
            "${case.group}: ${case.written}",
            nfc(case.spoken),
            normalizer.normalize(case.written, TextRole.BODY),
        )
    }

    @Test
    fun `Hindi numbers are read as Hindi words`() = check(hindi, hindiCases)

    @Test
    fun `Bengali numbers are read as Bengali words`() = check(bengali, bengaliCases)

    @Test
    fun `every number below a hundred has its own word`() {
        listOf(IndicNumberWords.HINDI, IndicNumberWords.BENGALI).forEach { words ->
            val all = (0L until 100L).map(words::cardinal)
            assertEquals(100, all.toSet().size)
            assertFalse(all.any { it.isBlank() || it.contains(' ') })
        }
    }

    @Test
    fun `output has no digits and normalizing twice changes nothing`() {
        val text = "₹१२.५० और 1,50,000 तथा ७५% (२०१९-२०) 007"
        val once = hindi.normalize(text, TextRole.BODY)
        assertFalse(once.any(Char::isDigit))
        assertEquals(once, hindi.normalize(once, TextRole.BODY))
    }

    @Test
    fun `English has no Indic normalizer`() {
        assertNull(IndicNormalizer.forLang(Lang.EN))
    }

    private fun nfc(text: String) = Normalizer.normalize(text, Normalizer.Form.NFC)
}
