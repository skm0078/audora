// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/BetaScanNoticeTest.kt
// Purpose: Which pages say they are an early-beta scan, and when the phone's voice is offered for them (2026-10-31).

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.speech.voices.VoiceKind
import io.github.skm0078.audora.speech.voices.VoiceRow
import io.github.skm0078.audora.speech.voices.VoiceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BetaScanNoticeTest {
    private val beta = ReaderConfig().betaScanLanguages
    private val phone = VoiceId(EngineId.SYSTEM, "bn-in-x-ban-local")
    private val syspin = VoiceId(EngineId.SHERPA, "syspin-bn-male")

    private fun page(vararg parts: Pair<Lang, Int>): List<ReadingUnit> = parts.mapIndexed { index, (lang, chars) ->
        ReadingUnit(0, index, index * chars, (index + 1) * chars, "x".repeat(chars), lang, 0, null)
    }

    private fun row(id: VoiceId, reads: Boolean) =
        VoiceRow(id, id.name, Lang.BN, "MIT", 1, VoiceKind.CATALOG, VoiceStatus.Installed, readsLanguage = reads)

    @Test
    fun `Bengali is the beta language by default`() {
        assertEquals(setOf(Lang.BN), beta)
    }

    @Test
    fun `only a mostly Bengali page read by OCR is a beta scan`() {
        val bengali = page(Lang.BN to 300, Lang.EN to 40)
        assertEquals(Lang.BN, betaScanLanguage(PageOrigin.OCR, bengali, beta))
        assertNull("a Bengali text PDF reads its own letters", betaScanLanguage(PageOrigin.TEXT, bengali, beta))
        assertNull(betaScanLanguage(PageOrigin.OCR_FAILED, bengali, beta))
        assertNull("Hindi is not beta", betaScanLanguage(PageOrigin.OCR, page(Lang.HI to 300), beta))
        assertNull("an English scan", betaScanLanguage(PageOrigin.OCR, page(Lang.EN to 300, Lang.BN to 20), beta))
        assertNull("nothing on the page", betaScanLanguage(PageOrigin.OCR, emptyList(), beta))
        assertNull("a graduated language", betaScanLanguage(PageOrigin.OCR, bengali, emptySet()))
    }

    @Test
    fun `the phone's voice is offered while one of ours reads the language`() {
        assertTrue(offersPhoneVoice(Lang.BN, phone, emptyMap(), listOf(row(syspin, reads = true))))
        assertTrue("the book's own pick", offersPhoneVoice(Lang.BN, phone, mapOf(Lang.BN to syspin), emptyList()))
    }

    @Test
    fun `nothing is offered without a phone voice, or when the phone's voice already reads it`() {
        val ours = listOf(row(syspin, reads = true))
        assertFalse("no phone voice", offersPhoneVoice(Lang.BN, null, emptyMap(), ours))
        assertFalse("the book reads with the phone", offersPhoneVoice(Lang.BN, phone, mapOf(Lang.BN to phone), ours))
        val notReading = listOf(row(syspin, reads = false))
        assertFalse("the default is the phone's", offersPhoneVoice(Lang.BN, phone, emptyMap(), notReading))
        assertFalse("no voice of ours", offersPhoneVoice(Lang.BN, phone, emptyMap(), emptyList()))
    }
}
