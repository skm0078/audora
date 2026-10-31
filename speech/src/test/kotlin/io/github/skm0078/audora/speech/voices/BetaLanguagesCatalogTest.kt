// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/voices/BetaLanguagesCatalogTest.kt
// Purpose: The shipped catalog keeps Bengali in `beta` (2026-10-31) until it is taken out on purpose.

package io.github.skm0078.audora.speech.voices

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class BetaLanguagesCatalogTest {
    private val catalog = (File("models.json").takeIf { it.isFile } ?: File("speech/models.json")).readText()

    @Test
    fun `Bengali is the catalog's only beta language`() {
        val list = requireNotNull(Regex(""""beta"\s*:\s*\[([^\]]*)]""").find(catalog)) { "no beta list" }
        val langs = Regex(""""([^"]+)"""").findAll(list.groupValues[1]).map { it.groupValues[1] }.toList()
        assertEquals(listOf("bn"), langs)
    }
}
