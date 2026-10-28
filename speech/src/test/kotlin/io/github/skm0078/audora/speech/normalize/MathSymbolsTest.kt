// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/normalize/MathSymbolsTest.kt
// Purpose: Display equations read as written: maths and logic symbols as words, only for the equation role (RI-7b).

package io.github.skm0078.audora.speech.normalize

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.speech.route.speechNormalizers
import org.junit.Assert.assertEquals
import org.junit.Test

class MathSymbolsTest {
    private val normalizers = speechNormalizers()

    private fun spoken(text: String, lang: Lang = Lang.EN, role: TextRole = TextRole.EQUATION) =
        normalizers.getValue(lang).normalize(text, role)

    @Test
    fun `logic and maths symbols become words`() {
        val cases = listOf(
            "∀x (Fx → Gx)" to "for all x (Fx implies Gx)",
            "¬(P ∧ Q) ↔ (¬P ∨ ¬Q)" to "not (P and Q) if and only if ( not P or not Q)",
            "x ≤ 3" to "x at most three",
            "a - b = c" to "a minus b equals c",
            "α + β ≥ 2π" to "alpha plus beta at least two pi",
            "x² ∈ A" to "x squared in A",
        )
        for ((text, expected) in cases) assertEquals(text, expected, spoken(text))
    }

    @Test
    fun `Hindi and Bengali equations use their own words for the operators`() {
        assertEquals("क और ख", spoken("क ∧ ख", Lang.HI))
        assertEquals("ক অথবা খ", spoken("ক ∨ খ", Lang.BN))
    }

    @Test
    fun `body text reads its symbols with the running-text words, not the equation's`() {
        assertEquals("P to Q", spoken("P → Q", role = TextRole.BODY))
        assertEquals("well-known", spoken("well-known"))
    }

    @Test
    fun `a symbol the equation table lacks has its running-text word in the part's language`() {
        assertEquals("x proportional to y", spoken("x ∝ y"))
        assertEquals("अल्फ़ा बराबर दो", spoken("α = 2", Lang.HI))
        assertEquals("a times b", spoken("a * b"))
    }
}
