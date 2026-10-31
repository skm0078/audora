// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/sherpa/LetterVoicesTest.kt
// Purpose: Which voices read letters, and that only the Bengali ones among them are respelled and slowed.

package io.github.skm0078.audora.speech.sherpa

import io.github.skm0078.audora.core.model.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LetterVoicesTest {
    private val letters = LetterVoices(bengaliPace = 0.9f)
    private val syspin = spec("syspin-bn-female", Lang.BN).copy(dataDir = null)

    @Test
    fun `a VITS voice without espeak-ng data or a lexicon reads letters`() {
        assertTrue(syspin.readsLetters)
        assertFalse(syspin.copy(dataDir = "espeak-ng-data").readsLetters)
        assertFalse(syspin.copy(lexicon = "lexicon.txt").readsLetters)
        assertFalse(syspin.copy(family = SherpaFamily.SUPERTONIC).readsLetters)
    }

    @Test
    fun `a Bengali letter voice is respelled and slowed`() {
        assertEquals("সব্ ঠিক্", letters.text(syspin, "সব ঠিক"))
        assertEquals(0.9f, letters.rate(syspin, 1f))
    }

    @Test
    fun `a letter voice of another language, or a Bengali voice with espeak-ng, is left as it is`() {
        val hindi = syspin.copy(id = "mms-hi", lang = Lang.HI)
        val piper = syspin.copy(dataDir = "espeak-ng-data")

        listOf(hindi, piper).forEach { voice ->
            assertEquals("সব ঠিক", letters.text(voice, "সব ঠিক"))
            assertEquals(1.25f, letters.rate(voice, 1.25f))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a pace that is not a number is refused`() {
        LetterVoices(Float.NaN)
    }
}
