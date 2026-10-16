// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/voices/VoiceLicencesTest.kt
// Purpose: The "Voices & licences" list (every voice, in order, with credits) and reading a voice's licence files.

package io.github.skm0078.audora.speech.voices

import io.github.skm0078.audora.core.model.Lang
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VoiceLicencesTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val credits = VoiceCredits("By someone", "https://licence.test", "https://source.test")
    private val bundled = voiceSpec("piper-en-ljspeech")
    private val cori = catalogVoice(ByteArray(10), "piper-en-cori").copy(credits = credits)
    private val pratham = catalogVoice(ByteArray(20), "piper-hi-pratham", Lang.HI).copy(credits = credits)
    private val bengali = catalogVoice(ByteArray(30), "piper-bn-google", Lang.BN).copy(credits = credits)
    private val catalog = VoiceCatalog(listOf(bengali, pratham, cori))

    @Test
    fun `every voice is listed by language, built in first, with downloads whether installed or not`() {
        val installed = listOf(
            InstalledVoice(pratham.spec, VoiceOrigin.CATALOG, File("p"), 1),
            InstalledVoice(voiceSpec("custom-hi-1", Lang.HI), VoiceOrigin.CUSTOM, File("c"), 1),
        )

        val list = VoiceLicences.build(bundled, credits, catalog, installed)

        assertEquals(
            listOf("piper-en-ljspeech", "piper-en-cori", "piper-hi-pratham", "custom-hi-1", "piper-bn-google"),
            list.map { it.id.name },
        )
        assertEquals(
            listOf(LicenceKind.BUILT_IN, LicenceKind.DOWNLOAD, LicenceKind.DOWNLOAD, LicenceKind.IMPORTED),
            list.take(4).map { it.kind },
        )
        assertEquals(listOf(true, false, true, true, false), list.map { it.installed })
        assertEquals(listOf(credits, credits, credits, null, credits), list.map { it.credits })
    }

    @Test
    fun `a missing bundled voice leaves only the catalog`() {
        val list = VoiceLicences.build(null, null, catalog, emptyList())

        assertEquals(3, list.size)
        assertNull(list.firstOrNull { it.kind == LicenceKind.BUILT_IN })
    }

    @Test
    fun `licence files are read in a fixed order, other files ignored, long ones cut`() {
        val dir = temp.newFolder("voice")
        dir.write("MODEL_CARD", "card".toByteArray())
        dir.write("LICENSE", "x".repeat(LicenceTexts.MAX_CHARS + 10).toByteArray())
        dir.write("model.onnx", ByteArray(4))

        val texts = LicenceTexts.read(dir)

        assertEquals(listOf("LICENSE", "MODEL_CARD"), texts.map { it.name })
        assertEquals(LicenceTexts.MAX_CHARS, texts[0].text.length)
        assertEquals("card", texts[1].text)
    }

    @Test
    fun `a folder without licence files has none`() {
        assertEquals(emptyList<LicenceText>(), LicenceTexts.read(temp.newFolder("empty")))
        assertEquals(emptyList<LicenceText>(), LicenceTexts.read(File(temp.root, "missing")))
    }
}
