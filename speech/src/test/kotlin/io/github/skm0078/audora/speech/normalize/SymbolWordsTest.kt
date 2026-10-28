// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/normalize/SymbolWordsTest.kt
// Purpose: Arrows, maths signs, Greek letters and powers in sentences are read in the part's language, not skipped.

package io.github.skm0078.audora.speech.normalize

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.speech.route.speechNormalizers
import org.junit.Assert.assertEquals
import org.junit.Test

class SymbolWordsTest {
    private val normalizers = speechNormalizers()

    private fun assertSpoken(lang: Lang, vararg cases: Pair<String, String>) {
        for ((text, expected) in cases) {
            assertEquals(text, expected, normalizers.getValue(lang).normalize(text, TextRole.BODY))
        }
    }

    @Test
    fun `English arrows and comparisons read as words`() = assertSpoken(
        Lang.EN,
        "Input → Process → Output." to "Input to Process to Output.",
        "Heat -> expansion --> pressure." to "Heat to expansion to pressure.",
        "If p ⇒ q and p => q." to "If p implies q and p implies q.",
        "A ↔ B and C ⇔ D." to "A and B and C if and only if D.",
        "Blood pressure ↑ and heart rate ↓." to "Blood pressure up and heart rate down.",
        "Keep the dose ≤ 5 mg and the pH >= 7." to "Keep the dose at most five milligrams and the pH at least seven.",
        "x ≠ y but x ≈ 3." to "x not equal to y but x approximately three.",
        "Use p < 0.05 and n>10." to "Use p less than zero point zero five and n greater than ten.",
        "a != b" to "a not equal to b",
    )

    @Test
    fun `English maths signs, Greek letters and marks read as words`() = assertSpoken(
        Lang.EN,
        "The angle θ is 30° and Δt = 2 s." to "The angle theta is thirty degrees and delta t equals two s.",
        "√2 is irrational and ∞ is not a number." to
            "the square root of two is irrational and infinity is not a number.",
        "2 × 3 = 6, 6 ÷ 2 = 3 and 7 − 4 = 3." to
            "two by three equals six, six divided by two equals three and seven minus four equals three.",
        "∴ it holds, ∵ x > 0." to "therefore it holds, because x greater than zero.",
        "A ⊥ B and AB ∥ CD in △ABC." to "A perpendicular to B and AB parallel to CD in triangle ABC.",
        "Total = Σ xᵢ for all i ∈ S." to "Total equals sigma x i for all i in S.",
        "C++ and C# are languages; rate it A+." to "C plus plus and C sharp are languages; rate it A plus.",
        "It rose to 40℃." to "It rose to forty degrees Celsius.",
        "All done ✓ but one ✗." to "All done tick but one cross.",
    )

    @Test
    fun `English powers, subscripts, fractions and small units`() = assertSpoken(
        Lang.EN,
        "The area is 25 m² and the volume 8 cm³." to
            "The area is twenty-five square metres and the volume eight cubic centimetres.",
        "E = mc² and a² + b² = c²." to "E equals mc squared and a squared plus b squared equals c squared.",
        "Use 10^6 samples, or 10⁶." to "Use ten to the power six samples, or ten to the power six.",
        "A rate of 5 s⁻¹." to "A rate of five s to the power minus one.",
        "(a + b)² is a square." to "(a plus b) squared is a square.",
        "Water is H₂O." to "Water is H two O.",
        "Mix ½ cup with 2¾ cups." to "Mix one half cup with two and three quarters cups.",
        "Doses of 5 μg and 2 µg." to "Doses of five micrograms and two micrograms.",
        "A 10 Ω and a 4.7 kΩ resistor." to "A ten ohms and a four point seven kilohms resistor.",
        "3 * 4" to "three times four",
    )

    @Test
    fun `a superscript after a long word is a footnote mark and is left out`() = assertSpoken(
        Lang.EN,
        "As shown before¹ and later²." to "As shown before and later.",
    )

    @Test
    fun `rules of one repeated mark are a pause, not a list of symbols`() = assertSpoken(
        Lang.EN,
        "Name: __________ Date: ______" to "Name: Date:",
        "* * *" to "",
        "Part one ===== Part two" to "Part one, Part two",
    )

    @Test
    fun `Hindi reads symbols in Hindi`() = assertSpoken(
        Lang.HI,
        "दिल्ली → मुंबई की यात्रा।" to "दिल्ली से मुंबई की यात्रा।",
        "2 + 3 = 5 होता है।" to "दो धन तीन बराबर पाँच होता है।",
        "तापमान 40° था और दबाव ↑ हुआ।" to "तापमान चालीस डिग्री था और दबाव बढ़ा हुआ।",
        "तापमान 37°C था।" to "तापमान सैंतीस डिग्री सेल्सियस था।",
        "x ≤ 5 है।" to "x से कम या बराबर पाँच है।",
        "π का मान लगभग 3.14 है।" to "पाई का मान लगभग तीन दशमलव एक चार है।",
        "जल का सूत्र H₂O है।" to "जल का सूत्र H दो O है।",
        "∴ यह सिद्ध हुआ।" to "अतः यह सिद्ध हुआ।",
        "6 × 7 = 42 और 8 ÷ 2 = 4।" to "छह गुणा सात बराबर बयालीस और आठ भाग दो बराबर चार।",
        "~5 लोग आए।" to "लगभग पाँच लोग आए।",
    )

    @Test
    fun `Bengali reads symbols in Bengali`() = assertSpoken(
        Lang.BN,
        "কলকাতা → দিল্লি যাত্রা।" to "কলকাতা থেকে দিল্লি যাত্রা।",
        "২ + ৩ = ৫ হয়।" to "দুই যোগ তিন সমান পাঁচ হয়।",
        "তাপমাত্রা ৪০° ছিল এবং চাপ ↑ হল।" to "তাপমাত্রা চল্লিশ ডিগ্রি ছিল এবং চাপ বৃদ্ধি হল।",
        "π এর মান প্রায় ৩.১৪।" to "পাই এর মান প্রায় তিন দশমিক এক চার।",
        "জলের সংকেত H₂O।" to "জলের সংকেত H দুই O।",
        "∴ প্রমাণিত হল।" to "অতএব প্রমাণিত হল।",
    )

    @Test
    fun `a sign without a word in Hindi or Bengali is left out, never read in English`() {
        assertSpoken(Lang.HI, "A ← B है।" to "A B है।")
        assertSpoken(Lang.BN, "A ← B।" to "A B।")
    }

    @Test
    fun `display equations keep the equation words`() {
        assertEquals("p implies q", normalizers.getValue(Lang.EN).normalize("p → q", TextRole.EQUATION))
    }
}
