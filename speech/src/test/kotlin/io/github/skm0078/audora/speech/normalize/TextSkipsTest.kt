// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/normalize/TextSkipsTest.kt
// Purpose: The skip switches (plan item 2), table-driven: what each leaves out and what it must keep.

package io.github.skm0078.audora.speech.normalize

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSkipsTest {
    private val all = TextSkips(parentheses = true, brackets = true, links = true)

    @Test
    fun `each switch leaves out its own kind and keeps the rest`() {
        val cases = listOf(
            Triple(TextSkips(true, false, false), "The river (see map 2) rose.", "The river rose."),
            Triple(TextSkips(true, false, false), "Solve f(x) and 2(a) first.", "Solve f(x) and 2(a) first."),
            Triple(TextSkips(true, false, false), "A (nested (deep) aside) here.", "A here."),
            Triple(TextSkips(true, false, false), "(An aside at the start) then text.", "then text."),
            Triple(TextSkips(true, false, false), "नदी (नक्शा देखें) बढ़ी।", "नदी बढ़ी।"),
            Triple(TextSkips(false, true, false), "He wrote [sic] this[3].", "He wrote this."),
            Triple(TextSkips(false, true, false), "Keep (this) one.", "Keep (this) one."),
            Triple(TextSkips(false, false, true), "See https://india.gov.in/x, then go.", "See, then go."),
            Triple(TextSkips(false, false, true), "Mail a.b@example.com or visit india.gov.in.", "Mail or visit."),
            Triple(TextSkips(false, false, true), "e.g. Fig.3 stays", "e.g. Fig.3 stays"),
            Triple(all, "Text (aside) [1] at www.x.org.", "Text at."),
        )
        cases.forEach { (skips, input, expected) -> assertEquals(input, expected, skips.apply(input)) }
    }

    @Test
    fun `no switch on returns the text untouched`() {
        val off = TextSkips(parentheses = false, brackets = false, links = false)
        val text = "Odd  spacing (kept) [kept] www.x.org ."

        assertTrue(off.isNone)
        assertEquals(text, off.apply(text))
    }

    @Test
    fun `a sentence that is only an aside becomes empty`() {
        assertEquals("", all.apply("(Photo courtesy of the archive.)"))
    }
}
