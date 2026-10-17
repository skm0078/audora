// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/normalize/MathStructureTest.kt
// Purpose: Equations are read as a teacher reads them: powers, roots, fractions, derivatives, functions (plan item 66).

package io.github.skm0078.audora.speech.normalize

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.speech.route.speechNormalizers
import org.junit.Assert.assertEquals
import org.junit.Test

class MathStructureTest {
    private val normalizers = speechNormalizers()

    private fun spoken(text: String, lang: Lang = Lang.EN) =
        normalizers.getValue(lang).normalize(text, TextRole.EQUATION).replace(SPACES, " ").trim()

    @Test
    fun `English equations read as a teacher reads them`() {
        val cases = listOf(
            "x² + y² = z²" to "x squared plus y squared equals z squared",
            "x^2 + y^3" to "x squared plus y cubed",
            "e^{i\u03c0} + 1 = 0" to "e to the power i pi plus one equals zero",
            "x^{n+1}" to "x to the power n plus one,",
            "a_{n-1} + a_n" to "a sub n minus one plus a sub n",
            "x₁ + x₂" to "x sub one plus x sub two",
            "√(x+1)" to "the square root of x plus one,",
            "∛8 = 2" to "the cube root of eight equals two",
            "(a+b)/(c+d)" to "the fraction a plus b over c plus d,",
            "dy/dx = 2x" to "d y by d x equals two x",
            "d²y/dx² + y = 0" to "d squared y by d x squared plus y equals zero",
            "∂f/∂x" to "partial f by partial x",
            "|x - 3| < 1" to "the absolute value of x minus three, is less than one",
            "n! = n(n-1)!" to "n factorial equals n(n minus one) factorial",
            "sin²x + cos²x = 1" to "sine squared x plus cosine squared x equals one",
            "ln(xy) = ln x + ln y" to "natural log of (x y) equals natural log x plus natural log y",
            "lim x→0 f(x) = L" to "the limit as x approaches zero f of x equals L",
            "f′(x) = 3x² - 2" to "f prime of x equals three x squared minus two",
            "1 + 2 + … + n" to "one plus two plus and so on plus n",
            "½mv²" to "one half m v squared",
            "-x + y" to "minus x plus y",
            "sin(x) + g(t)" to "sine of x plus g of t",
        )
        for ((text, expected) in cases) assertEquals(text, expected, spoken(text))
    }

    @Test
    fun `Hindi and Bengali equations use their own words for the structure`() {
        val cases = listOf(
            Triple("x² + y² = z²", Lang.HI, "x का वर्ग धन y का वर्ग बराबर z का वर्ग"),
            Triple("a/b", Lang.HI, "a बटा b"),
            Triple("√(x+1)", Lang.HI, "वर्गमूल x धन एक,"),
            Triple("x^{n}", Lang.HI, "x की घात n"),
            Triple("n!", Lang.HI, "n फ़ैक्टोरियल"),
            Triple("x² + y² = z²", Lang.BN, "x এর বর্গ যোগ y এর বর্গ সমান z এর বর্গ"),
            Triple("a/b", Lang.BN, "a বাই b"),
            Triple("x₁ = ½", Lang.BN, "x সাব এক সমান অর্ধেক"),
            Triple("dy/dx", Lang.BN, "d y বাই d x"),
        )
        for ((text, lang, expected) in cases) assertEquals(text, expected, spoken(text, lang))
    }

    @Test
    fun `a hyphen between words stays and body text is untouched`() {
        assertEquals("well-known", spoken("well-known"))
        val body = normalizers.getValue(Lang.EN).normalize("Solve dy/dx first.", TextRole.BODY)
        assertEquals(false, " by " in body)
    }

    private companion object {
        val SPACES = Regex("""\s+""")
    }
}
