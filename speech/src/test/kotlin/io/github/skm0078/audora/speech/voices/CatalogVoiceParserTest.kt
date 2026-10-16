// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/voices/CatalogVoiceParserTest.kt
// Purpose: Table-driven checks of the catalog entry parser and the catalog's duplicate guard.

package io.github.skm0078.audora.speech.voices

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.getOrNull
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogVoiceParserTest {
    private val sha = "a".repeat(64)

    private fun entry(vararg overrides: Pair<String, String?>): Properties {
        val values = mutableMapOf<String, String?>(
            "id" to "piper-en-cori",
            "label" to "Cori",
            "lang" to "en",
            "family" to "vits",
            "model" to "model.onnx",
            "tokens" to "tokens.txt",
            "dataDir" to "espeak-ng-data",
            "licence" to "CC-BY-4.0",
            "contentVersion" to "ignored",
            "url" to "https://github.com/skm0078/audora/releases/download/voices/piper-en-cori-aaaaaaaaaaaa.zip",
            "sha256" to sha.uppercase(),
            "sizeBytes" to "1234",
        )
        overrides.forEach { (key, value) -> values[key] = value }
        return Properties().apply { values.forEach { (key, value) -> if (value != null) setProperty(key, value) } }
    }

    @Test
    fun `a valid entry parses and its content version is the pin`() {
        val voice = requireNotNull(CatalogVoiceParser.parse(entry()).getOrNull())

        assertEquals("piper-en-cori", voice.id)
        assertEquals(Lang.EN, voice.spec.lang)
        assertEquals(sha, voice.sha256)
        assertEquals(sha, voice.spec.contentVersion)
        assertEquals(1234L, voice.sizeBytes)
    }

    @Test
    fun `invalid entries are refused`() {
        val cases = listOf(
            entry("url" to "http://example.test/a.zip"),
            entry("url" to null),
            entry("sha256" to "abc"),
            entry("sha256" to null),
            entry("sizeBytes" to "0"),
            entry("sizeBytes" to "big"),
            entry("model" to null),
        )
        cases.forEachIndexed { index, properties ->
            assertNull("case $index", CatalogVoiceParser.parse(properties).getOrNull())
        }
    }

    @Test
    fun `the catalog finds voices by id and refuses duplicates`() {
        val voice = catalogVoice(ByteArray(3))
        val catalog = VoiceCatalog(listOf(voice))

        assertEquals(voice, catalog.find("piper-en-cori"))
        assertNull(catalog.find("other"))
        val duplicate = runCatching { VoiceCatalog(listOf(voice, voice)) }
        assertTrue(duplicate.isFailure)
        assertTrue(CatalogVoiceParser.parse(entry()) is Outcome.Success)
    }
}
