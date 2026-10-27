// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/voices/LanguagePacksTest.kt
// Purpose: One download per language from the recommended voice; a simple build hides only voices nobody chose.

package io.github.skm0078.audora.speech.voices

import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguagePacksTest {
    @Suppress("LongParameterList") // Every field a test row varies, each with a default.
    private fun row(
        name: String,
        lang: Lang,
        status: VoiceStatus = VoiceStatus.NotDownloaded,
        kind: VoiceKind = VoiceKind.CATALOG,
        recommended: Boolean = false,
        reads: Boolean = false,
    ) = VoiceRow(VoiceId(EngineId.SHERPA, name), name, lang, "MIT", 1, kind, status, reads, recommended = recommended)

    private val english = row("piper-en-ljspeech", Lang.EN, VoiceStatus.BuiltIn, VoiceKind.BUILT_IN, reads = true)
    private val supertonic = row("supertonic-hi", Lang.HI, recommended = true)
    private val syspin = row("syspin-bn-male", Lang.BN, recommended = true)
    private val google = row("piper-bn-google", Lang.BN)

    @Test
    fun `each language without a built-in voice gets its recommended download`() {
        val packs = LanguagePacks.of(listOf(english, supertonic, syspin, google))
        assertEquals(listOf(Lang.HI, Lang.BN), packs.map { it.lang })
        assertEquals(listOf("supertonic-hi", "syspin-bn-male"), packs.map { it.voice.id.name })
        assertTrue(packs.none { it.ready })
        assertNull(LanguagePacks.pack(listOf(english), Lang.EN))
    }

    @Test
    fun `a language is ready with any neural voice of its own, and shows a running download`() {
        val other = google.copy(status = VoiceStatus.Installed)
        assertTrue(LanguagePacks.pack(listOf(syspin, other), Lang.BN)!!.ready)
        val running = supertonic.copy(status = VoiceStatus.Downloading(1, 2))
        val pack = LanguagePacks.pack(listOf(running), Lang.HI)!!
        assertTrue(pack.downloading)
        assertFalse(pack.ready)
    }

    @Test
    fun `a simple build hides unsettled voices unless they are already chosen or on the phone`() {
        val simple = VoiceListConfig(simple = true, hiddenIds = setOf("piper-bn-google"))
        val rows = listOf(english, supertonic, syspin, google)
        assertEquals(listOf(english, supertonic, syspin), simple.visible(rows))
        val kept = google.copy(status = VoiceStatus.Installed)
        assertTrue(kept in simple.visible(listOf(kept)))
        assertFalse(simple.canImport)
        assertTrue(VoiceListConfig(simple = true).hiddenIds.isEmpty())
        assertEquals(rows, VoiceListConfig().visible(rows))
        assertTrue(VoiceListConfig().canImport)
    }
}
