// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/web/CuratedCataloguesTest.kt
// Purpose: The curated catalogue list is well formed: https addresses the app fetches as they are, each listed once,
//          named, with its languages, and none removable.

package io.github.skm0078.audora.playback.web

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CuratedCataloguesTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `every address is https and already in the form the app fetches`() {
        CuratedCatalogue.entries.forEach { curated ->
            assertTrue(curated.name, curated.address.startsWith("https://"))
            assertEquals(curated.name, curated.address, WebAddress.normalize(curated.address))
        }
    }

    @Test
    fun `each catalogue is listed once, named, with the languages its books are in`() {
        val entries = CuratedCatalogue.entries
        assertEquals(entries.size, entries.map { it.address }.toSet().size)
        assertEquals(entries.size, entries.map { it.title }.toSet().size)
        entries.forEach { curated ->
            assertTrue(curated.name, curated.title.isNotBlank())
            assertTrue(curated.name, curated.languages.isNotEmpty())
            assertTrue(curated.name, curated.languages.all { it.matches(PRIMARY_TAG) })
        }
    }

    @Test
    fun `the curated catalogues come first, in order, and cannot be removed`() {
        val all = OpdsCatalogues(File(temp.root, "catalogues.txt")).all()

        assertEquals(CuratedCatalogue.entries.map { it.address }, all.map { it.address })
        assertTrue(all.all { it.curated != null })
        assertEquals(CuratedCatalogue.entries, all.map { it.curated })
    }

    private companion object {
        val PRIMARY_TAG = Regex("[a-z]{2,3}")
    }
}
