// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/voices/CatalogLicencesTest.kt
// Purpose: The shipped catalogs offer no voice or data whose licence forbids commercial use (ADR-074).

package io.github.skm0078.audora.speech.voices

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogLicencesTest {
    private fun catalog(name: String): String = (File(name).takeIf { it.isFile } ?: File("speech/$name")).readText()

    private val catalogs = listOf("models.json", "bundled-voices.json").associateWith { catalog(it) }

    @Test
    fun `no licence in either catalog is non-commercial`() {
        val licence = Regex(""""licence(?:Url)?"\s*:\s*"([^"]*)"""")
        catalogs.forEach { (name, text) ->
            licence.findAll(text).map { it.groupValues[1] }.forEach {
                assertFalse("$name offers a non-commercial licence: $it", NON_COMMERCIAL.containsMatchIn(it))
            }
        }
    }

    @Test
    fun `withdrawn voices are gone from the catalogs, and each recommended voice is offered`() {
        catalogs.forEach { (name, text) ->
            WithdrawnVoices.IDS.forEach { id -> assertFalse("$name still lists $id", "\"$id\"" in text) }
        }
        val recommended = Regex(""""recommended"\s*:\s*\{([^}]*)}""").find(catalogs.getValue("models.json"))
        val ids = Regex(""":\s*"([^"]+)"""").findAll(recommended!!.groupValues[1]).map { it.groupValues[1] }.toList()
        assertTrue(ids.isNotEmpty())
        val offered = Regex(""""id"\s*:\s*"([^"]+)"""").findAll(catalogs.getValue("models.json"))
            .map { it.groupValues[1] }.toSet()
        ids.forEach { assertTrue("$it is recommended but not offered", it in offered) }
    }

    private companion object {
        /** CC's NC licences, by short name or by URL, and any licence that says so in words. */
        val NON_COMMERCIAL = Regex("""(?i)\bBY-NC\b|/by-nc|non-?commercial""")
    }
}
