// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/normalize/SpeakableTextTest.kt
// Purpose: What no voice can say (emoji, stars, broken-font glyphs, stray signs) never reaches a voice; all else stays.

package io.github.skm0078.audora.speech.normalize

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.speech.route.speechNormalizers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeakableTextTest {
    @Test
    fun `unsayable characters go and their pauses are tidied`() {
        val cases = listOf(
            "Great job 👍🎉 see you soon 😊" to "Great job see you soon",
            "Rated ★★★★☆ — well liked." to "Rated — well liked.",
            "A broken font: \uE000\uF0B7 and \uFFFD here." to "A broken font: and here.",
            "See note* and the dagger† mark." to "See note and the dagger mark.",
            "Red • Green • Blue" to "Red, Green, Blue",
            "Home | About | Help" to "Home, About, Help",
            "• First item" to "First item",
            "Flag 🇮🇳 and keycap 1️⃣ go." to "Flag and keycap 1 go.",
            "#hashtag @someone" to "hashtag someone",
        )
        for ((text, expected) in cases) assertEquals(text, expected, SpeakableText.clean(text))
    }

    @Test
    fun `letters of every script, their marks and joiners, digits and phrasing punctuation stay`() {
        val kept = listOf(
            "He said, “wait…” (then left) — well-known; fine? Yes! 3/4",
            "क्षेत्र में ज़्यादा लोग हैं ।",
            "র‍্যাব আর ক্ষমতা।",
            "Привет, Ἀθῆναι, 東京",
            "Kerning‍kept after a letter",
        )
        for (text in kept) assertEquals(text, text, SpeakableText.clean(text))
    }

    @Test
    fun `a text of only symbols becomes blank`() {
        assertEquals("", SpeakableText.clean("★★★ ✱ 👍"))
    }

    /**
     * The test pages built for this fix (P-symbols): arrows, maths, equations, Greek, emoji and broken-font glyphs in
     * the three languages. Before it, 49 of 53 such sentences reached the voice with a symbol it could not say.
     */
    @Test
    fun `no test page sentence reaches a voice with a symbol it cannot say`() {
        val normalizers = speechNormalizers()
        for ((lang, sentences) in TEST_PAGES) {
            for (text in sentences) {
                val spoken = normalizers.getValue(lang).normalize(text, TextRole.BODY)
                val unsayable = spoken.codePoints().toArray().filterNot(::sayable)
                assertTrue("$text -> $spoken", unsayable.isEmpty())
            }
        }
    }

    private fun sayable(cp: Int): Boolean = Character.isLetter(cp) || Character.isDigit(cp) ||
        Character.isWhitespace(cp) || Character.getType(cp) in MARKS || cp.toChar() in SAYABLE_PUNCTUATION

    private companion object {
        val MARKS = setOf(Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt())
        const val SAYABLE_PUNCTUATION = ".,;:!?'\"()[]-–—…।॥‘’“”/"

        val TEST_PAGES = mapOf(
            Lang.EN to listOf(
                "Input → Process → Output.", "If p ⇒ q and q ⇒ r, then p ⇒ r.", "Heat -> expansion -> pressure.",
                "Blood pressure ↑ and heart rate ↓ after exercise.", "Keep the dose ≤ 5 mg and the pH ≥ 7.",
                "x ≠ y, but x ≈ 3.14 and π ≈ 3.14159.", "The area is 25 m² and the volume 8 cm³.",
                "E = mc² is famous; a² + b² = c².", "Water is H₂O and carbon dioxide is CO₂.",
                "Use 10^6 samples with p < 0.05.", "The angle θ is 30° and Δt = 2 s.",
                "√2 is irrational and ∞ is not a number.", "Mix ½ cup of flour with 2¾ cups of milk.",
                "Total = Σ xᵢ for all i ∈ S.", "Results ★★★★☆ — highly rated ✓", "Red • Green • Blue",
                "Section § 4 ¶ 2 of the Act © 2024.", "See note* and the dagger† mark.",
                "Name: __________ Date: ______", "Great job 👍🎉 see you soon 😊",
                "Glyphs from a broken font: \uE000\uF0B7\uF0A7 and \uFFFD here.", "A ↔ B and C ⇔ D.",
                "The reaction N₂ + 3H₂ ⇌ 2NH₃ is reversible.", "* * *", "C++ and C# are languages; rate it A+.",
                "Temperature rose to 40℃ yesterday.", "2 × 3 = 6 and 6 ÷ 2 = 3, also 7 − 4 = 3.",
                "∴ the claim holds, ∵ x > 0.", "Doses of 5 μg and 2 µg were given.",
                "A ⊥ B and AB ∥ CD in △ABC with ∠A = 90°.",
            ),
            Lang.HI to listOf(
                "दिल्ली → मुंबई की यात्रा।", "गर्मी → पसीना → प्यास।", "2 + 3 = 5 होता है।",
                "तापमान 40° था और दबाव ↑ हुआ।", "x ≤ 5 और y ≥ 7 है।", "π का मान लगभग 3.14 है।",
                "जल का सूत्र H₂O है।", "∴ यह सिद्ध हुआ।", "अंक ★★★ मिले 👍",
                "फ़ॉन्ट के टूटे अक्षर \uF0B7 और \uFFFD यहाँ।",
                "क्षेत्रफल 25 मी² है।", "6 × 7 = 42 और 8 ÷ 2 = 4।",
            ),
            Lang.BN to listOf(
                "কলকাতা → দিল্লি যাত্রা।", "গরম → ঘাম → তৃষ্ণা।", "২ + ৩ = ৫ হয়।", "তাপমাত্রা ৪০° ছিল এবং চাপ ↑ হল।",
                "x ≤ 5 এবং y ≥ 7।", "π এর মান প্রায় ৩.১৪।", "জলের সংকেত H₂O।", "∴ প্রমাণিত হল।",
                "নম্বর ★★★ পেয়েছি 👍",
                "ভাঙা ফন্টের অক্ষর \uF0B7 এবং \uFFFD এখানে।", "৬ × ৭ = ৪২ এবং ৮ ÷ ২ = ৪।",
            ),
        )
    }
}
