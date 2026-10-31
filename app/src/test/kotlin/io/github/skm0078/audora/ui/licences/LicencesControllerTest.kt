// File: app/src/test/kotlin/io/github/skm0078/audora/ui/licences/LicencesControllerTest.kt
// Purpose: Licences screen rules: the list loads once, a failure says so, only installed voices open their files, and
//          the app's own licence texts open under their title.

package io.github.skm0078.audora.ui.licences

import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.speech.voices.LicenceKind
import io.github.skm0078.audora.speech.voices.LicenceText
import io.github.skm0078.audora.speech.voices.VoiceLicence
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LicencesControllerTest {
    private val source = FakeLicenceSource()

    private fun TestScope.controller() = LicencesController(source, backgroundScope).also { it.start() }

    @Test
    fun `the list loads once`() = runTest(UnconfinedTestDispatcher()) {
        val controller = controller()
        controller.start()

        assertEquals(listOf(INSTALLED, NOT_INSTALLED), controller.state.value.voices)
        assertFalse(controller.state.value.loadFailed)
        assertEquals(1, source.loads)
    }

    @Test
    fun `a list that cannot load says so`() = runTest(UnconfinedTestDispatcher()) {
        source.failLoad = true

        val state = controller().state.value
        assertTrue(state.loadFailed)
        assertNull(state.voices)
    }

    @Test
    fun `an installed voice opens its files and closes again`() = runTest(UnconfinedTestDispatcher()) {
        val controller = controller()

        controller.openTexts(INSTALLED)
        assertEquals(OpenLicence(INSTALLED.label, listOf(CARD)), controller.state.value.open)

        controller.closeTexts()
        assertNull(controller.state.value.open)
    }

    @Test
    fun `a voice not on the phone has no files to open`() = runTest(UnconfinedTestDispatcher()) {
        val controller = controller()

        controller.openTexts(NOT_INSTALLED)

        assertNull(controller.state.value.open)
        assertEquals(emptyList<String>(), source.textRequests)
    }

    @Test
    fun `unreadable files open an empty list rather than nothing`() = runTest(UnconfinedTestDispatcher()) {
        source.failTexts = true
        val controller = controller()

        controller.openTexts(INSTALLED)

        assertEquals(OpenLicence(INSTALLED.label, emptyList()), controller.state.value.open)
    }

    @Test
    fun `the app's licence texts open under their title and close again`() = runTest(UnconfinedTestDispatcher()) {
        val controller = controller()

        controller.openNotices("All licence texts")
        assertEquals(OpenLicence("All licence texts", listOf(NOTICES, GPL)), controller.state.value.open)

        controller.closeTexts()
        assertNull(controller.state.value.open)
    }

    @Test
    fun `unreadable app licence texts open an empty list`() = runTest(UnconfinedTestDispatcher()) {
        source.failTexts = true
        val controller = controller()

        controller.openNotices("All licence texts")

        assertEquals(OpenLicence("All licence texts", emptyList()), controller.state.value.open)
    }

    private class FakeLicenceSource : LicenceSource {
        var failLoad = false
        var failTexts = false
        var loads = 0
        val textRequests = mutableListOf<String>()

        override suspend fun licences(): List<VoiceLicence> {
            loads++
            if (failLoad) throw IOException("disk")
            return listOf(INSTALLED, NOT_INSTALLED)
        }

        override suspend fun licenceTexts(id: String): List<LicenceText> {
            textRequests += id
            if (failTexts) throw IOException("disk")
            return listOf(CARD)
        }

        override suspend fun noticeTexts(): List<LicenceText> {
            if (failTexts) throw IOException("asset")
            return listOf(NOTICES, GPL)
        }
    }

    private companion object {
        val CARD = LicenceText("MODEL_CARD", "card")
        val NOTICES = LicenceText("THIRD-PARTY-NOTICES.txt", "notices")
        val GPL = LicenceText("listenlock-GPL-3.0.txt", "GPL")
        val INSTALLED = licence("supertonic-hi", installed = true)
        val NOT_INSTALLED = licence("piper-bn-google", installed = false)

        fun licence(id: String, installed: Boolean) = VoiceLicence(
            VoiceId(EngineId.SHERPA, id),
            id,
            Lang.HI,
            "CC BY-NC-SA 4.0",
            null,
            LicenceKind.DOWNLOAD,
            installed,
        )
    }
}
