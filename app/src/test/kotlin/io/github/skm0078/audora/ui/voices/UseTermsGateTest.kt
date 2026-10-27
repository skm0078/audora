// File: app/src/test/kotlin/io/github/skm0078/audora/ui/voices/UseTermsGateTest.kt
// Purpose: A voice with use terms downloads only after the user agrees; every other voice downloads at once.

package io.github.skm0078.audora.ui.voices

import android.net.Uri
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.speech.voices.VoiceKind
import io.github.skm0078.audora.speech.voices.VoiceRow
import io.github.skm0078.audora.speech.voices.VoiceStatus
import io.github.skm0078.audora.speech.voices.VoiceUseTerms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UseTermsGateTest {
    private val downloaded = mutableListOf<String>()
    private val gate = UseTermsGate { downloaded += it.id.name }

    private fun row(id: String, terms: VoiceUseTerms? = null) = VoiceRow(
        VoiceId(EngineId.SHERPA, id),
        id,
        Lang.HI,
        "licence",
        1,
        VoiceKind.CATALOG,
        VoiceStatus.NotDownloaded,
        readsLanguage = false,
        useTerms = terms,
    )

    @Test
    fun `a voice without terms downloads at once`() {
        gate.request(row("syspin-bn-male"))

        assertEquals(listOf("syspin-bn-male"), downloaded)
        assertNull(gate.pending)
    }

    @Test
    fun `a voice with terms waits for the user to agree`() {
        val supertonic = row("supertonic-hi", VoiceUseTerms.OPENRAIL_M)

        gate.request(supertonic)
        assertEquals(supertonic, gate.pending)
        assertEquals(emptyList<String>(), downloaded)

        gate.agree()
        assertEquals(listOf("supertonic-hi"), downloaded)
        assertNull(gate.pending)
        gate.agree()
        assertEquals(listOf("supertonic-hi"), downloaded)
    }

    @Test
    fun `turning the terms down downloads nothing`() {
        gate.request(row("supertonic-hi", VoiceUseTerms.OPENRAIL_M))
        gate.dismiss()

        assertNull(gate.pending)
        assertEquals(emptyList<String>(), downloaded)
    }

    @Test
    fun `the Voices screen's downloads and updates go through the gate`() {
        val direct = mutableListOf<String>()
        val gated = termsGated(RecordingActions(direct), gate)

        gated.onDownload(row("supertonic-hi", VoiceUseTerms.OPENRAIL_M))

        assertEquals("supertonic-hi", gate.pending?.id?.name)
        assertEquals(emptyList<String>(), downloaded + direct)
    }

    /** Records the downloads it is asked for directly; the rest do nothing. */
    private class RecordingActions(private val downloads: MutableList<String>) : VoicesActions {
        override fun onBack() = Unit

        override fun onDownload(row: VoiceRow) {
            downloads += row.id.name
        }

        override fun onCancel(row: VoiceRow) = Unit

        override fun onUse(row: VoiceRow) = Unit

        override fun onUseDefault(lang: Lang) = Unit

        override fun onRequestDelete(row: VoiceRow) = Unit

        override fun onConfirmDelete() = Unit

        override fun onCancelDelete() = Unit

        override fun onImport(treeUri: Uri, lang: Lang, label: String) = Unit

        override fun onMessageShown() = Unit
    }
}
