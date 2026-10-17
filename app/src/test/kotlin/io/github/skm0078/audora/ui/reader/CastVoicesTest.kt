// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/CastVoicesTest.kt
// Purpose: Switching a cast role on picks, per language, a voice other than the usual one (plan item 7).

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.speech.voices.VoiceKind
import io.github.skm0078.audora.speech.voices.VoiceRow
import io.github.skm0078.audora.speech.voices.VoiceStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class CastVoicesTest {
    private fun row(name: String, lang: Lang, usual: Boolean = false) = VoiceRow(
        VoiceId(EngineId.SHERPA, name),
        name,
        lang,
        "MIT",
        0,
        VoiceKind.CATALOG,
        VoiceStatus.Installed,
        readsLanguage = usual,
    )

    @Test
    fun `the first voice that is not the language's usual one`() {
        val choices = mapOf(
            Lang.EN to listOf(row("cori", Lang.EN), row("kitten", Lang.EN, usual = true)),
            Lang.HI to listOf(row("pratham", Lang.HI, usual = true), row("priyamvada", Lang.HI)),
        )

        assertEquals(
            mapOf(Lang.EN to VoiceId(EngineId.SHERPA, "cori"), Lang.HI to VoiceId(EngineId.SHERPA, "priyamvada")),
            firstCast(choices),
        )
    }
}
