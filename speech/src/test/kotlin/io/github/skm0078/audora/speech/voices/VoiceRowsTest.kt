// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/voices/VoiceRowsTest.kt
// Purpose: The Voices screen's list: status per catalog voice, which voice reads each language, and ordering.

package io.github.skm0078.audora.speech.voices

import io.github.skm0078.audora.core.model.Lang
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceRowsTest {
    private val bundled =
        voiceSpec("kitten-en-nano-bundled", family = io.github.skm0078.audora.speech.sherpa.SherpaFamily.KITTEN)
    private val cori = catalogVoice(ByteArray(10), "piper-en-cori")
    private val hindi = catalogVoice(ByteArray(20), "piper-hi-test", Lang.HI)
    private val catalog = VoiceCatalog(listOf(cori, hindi))

    private fun installed(voice: CatalogVoice, version: String = voice.sha256) =
        InstalledVoice(voice.spec.copy(contentVersion = version), VoiceOrigin.CATALOG, File("x"), 99)

    private fun custom(id: String, lang: Lang) =
        InstalledVoice(voiceSpec(id, lang).copy(label = "Mine"), VoiceOrigin.CUSTOM, File("y"), 7)

    private fun rows(
        installed: List<InstalledVoice> = emptyList(),
        downloads: Map<String, DownloadState> = emptyMap(),
        preferred: Map<Lang, io.github.skm0078.audora.core.model.VoiceId> = emptyMap(),
    ) = VoiceRows.build(bundled, catalog, installed, downloads, preferred)

    @Test
    fun `nothing installed lists the built-in voice reading English and the catalog to download`() {
        val rows = rows()

        assertEquals(listOf(VoiceKind.BUILT_IN, VoiceKind.CATALOG, VoiceKind.CATALOG), rows.map { it.kind })
        assertEquals(listOf(true, false, false), rows.map { it.readsLanguage })
        assertEquals(VoiceStatus.NotDownloaded, rows[1].status)
        assertEquals(cori.sizeBytes, rows[1].sizeBytes)
        assertEquals(Lang.HI, rows[2].lang)
    }

    @Test
    fun `download states show on their voice`() {
        val rows = rows(
            downloads = mapOf(
                cori.id to DownloadState.Running(5, 10),
                hindi.id to DownloadState.Failed("no network"),
            ),
        )

        assertEquals(VoiceStatus.Downloading(5, 10), rows.single { it.id.name == cori.id }.status)
        assertEquals(VoiceStatus.Failed("no network"), rows.single { it.id.name == hindi.id }.status)
    }

    @Test
    fun `installed voices show installed, or an update when the pin moved, and a failed update keeps the copy`() {
        val rows = rows(
            installed = listOf(installed(cori), installed(hindi, version = "old")),
            downloads = mapOf(cori.id to DownloadState.Failed("x")),
        )

        assertEquals(VoiceStatus.Installed, rows.single { it.id.name == cori.id }.status)
        assertEquals(99L, rows.single { it.id.name == cori.id }.sizeBytes)
        assertEquals(VoiceStatus.UpdateAvailable, rows.single { it.id.name == hindi.id }.status)
    }

    @Test
    fun `the chosen voice reads its language instead of the default`() {
        val rows = rows(installed = listOf(installed(cori)), preferred = mapOf(Lang.EN to cori.spec.voiceId))

        assertEquals(listOf(false, true), rows.filter { it.lang == Lang.EN }.map { it.readsLanguage })
    }

    @Test
    fun `custom voices and voices dropped from the catalog are listed after the catalog`() {
        val dropped = InstalledVoice(voiceSpec("piper-en-old"), VoiceOrigin.CATALOG, File("z"), 5)
        val mine = custom("custom-mine-1", Lang.BN)

        val rows = rows(installed = listOf(dropped, mine), preferred = mapOf(Lang.BN to mine.spec.voiceId))

        assertEquals(VoiceStatus.Installed, rows.single { it.id.name == "piper-en-old" }.status)
        val custom = rows.last()
        assertEquals(VoiceKind.CUSTOM, custom.kind)
        assertEquals(true, custom.readsLanguage)
        assertEquals("CUSTOM:custom-mine-1", custom.key)
    }
}
