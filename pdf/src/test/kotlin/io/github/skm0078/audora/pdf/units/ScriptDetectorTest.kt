// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/units/ScriptDetectorTest.kt
// Purpose: Dominant-script language guess.

package io.github.skm0078.audora.pdf.units

import io.github.skm0078.audora.core.model.Lang
import org.junit.Assert.assertEquals
import org.junit.Test

class ScriptDetectorTest {
    @Test
    fun `table of texts`() {
        val cases = listOf(
            "Hello world" to Lang.EN,
            "मानव अधिकार" to Lang.HI,
            "মানবাধিকার" to Lang.BN,
            "Article 12: मानव अधिकारों की सार्वभौम घोषणा" to Lang.HI,
            "UDHR text मानव" to Lang.EN,
            "123 — 456" to null,
            "" to null,
        )
        cases.forEach { (text, lang) -> assertEquals(text, lang, ScriptDetector.dominantLang(text)) }
    }
}
