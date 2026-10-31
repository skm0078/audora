// File: app/src/test/kotlin/io/github/skm0078/audora/ui/licences/AppNoticesTest.kt
// Purpose: The APK's licence folder stays complete: the app's GPL matches the repository's, the notices index and the
//          folder name the same files, every library the app depends on is credited, and the reading order holds.

package io.github.skm0078.audora.ui.licences

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNoticesTest {
    private val folder = File("src/main/assets/${AppNotices.ASSET_DIR}")
    private val index = File(folder, AppNotices.INDEX).readText()

    @Test
    fun `the app's licence is the repository's GPL, word for word`() {
        assertEquals(File("../LICENSE").readText(), File(folder, AppNotices.APP_LICENCE).readText())
    }

    @Test
    fun `the index lists every licence file and only files that exist`() {
        val files = folder.list().orEmpty().filter { it != AppNotices.INDEX }.toSortedSet()
        val listed = index.substringAfter(LIST_HEADING).lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
            .filterNot { it.all { char -> char == '-' } }.toSortedSet()

        assertEquals(files, listed)
    }

    @Test
    fun `no licence file is empty`() {
        val empty = folder.listFiles().orEmpty().filter { it.readText().isBlank() }.map { it.name }
        assertEquals(emptyList<String>(), empty)
    }

    @Test
    fun `every library the app depends on is credited`() {
        val uncredited = runtimeLibraryGroups().filterNot { group -> credited(group) }
        assertEquals("Add these to ${AppNotices.INDEX}", emptyList<String>(), uncredited)
    }

    @Test
    fun `the statements the IJG and FreeType licences ask for are there`() {
        assertTrue(index.contains("based in part on the work of the Independent JPEG Group"))
        assertTrue(index.contains("Portions of this software are copyright (c)"))
        assertTrue(index.contains("The FreeType Project (https://freetype.org)"))
    }

    @Test
    fun `the index reads first, the app's licence second, then the rest by name`() {
        val order = AppNotices.order(listOf("zlib.txt", AppNotices.APP_LICENCE, "Apache.txt", AppNotices.INDEX))
        assertEquals(listOf(AppNotices.INDEX, AppNotices.APP_LICENCE, "Apache.txt", "zlib.txt"), order)
    }

    @Test
    fun `a licence text splits at its blank lines into paragraphs`() {
        val text = "Title\r\n\r\n  Clause one,\n  still one.\n \nClause two.\n\n\n"
        assertEquals(listOf("Title", "  Clause one,\n  still one.", "Clause two."), AppNotices.paragraphs(text))
    }

    /** A group is credited by name, or by a parent of at least two parts ("androidx.compose" for its UI group). */
    private fun credited(group: String): Boolean {
        val parts = group.split('.')
        return (parts.size downTo MIN_GROUP_PARTS).any { index.contains(parts.take(it).joinToString(".")) }
    }

    /** Every library in the version catalog that ends up in the APK: build tools, tests and compilers are left out. */
    private fun runtimeLibraryGroups(): List<String> {
        val catalog = File("../gradle/libs.versions.toml").readText()
        val libraries = catalog.substringAfter("[libraries]").substringBefore("\n[")
        return LIBRARY.findAll(libraries)
            .filterNot { match -> NOT_IN_APK.any { match.groupValues[1].contains(it) } }
            .map { it.groupValues[2] }
            .distinct()
            .toList()
    }

    private companion object {
        const val LIST_HEADING = "Licence texts in this folder"
        const val MIN_GROUP_PARTS = 2
        val LIBRARY = Regex("""^([a-z0-9-]+)\s*=\s*\{\s*module\s*=\s*"([^:"]+):""", RegexOption.MULTILINE)
        val NOT_IN_APK = listOf("test", "junit", "benchmark", "ktlint", "detekt", "gradle-plugin", "compiler")
    }
}
