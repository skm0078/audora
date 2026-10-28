// File: app/src/test/kotlin/io/github/skm0078/audora/ui/search/FoundIndexTest.kt
// Purpose: The library search finds actions and settings from the app's own words (ADR 2026-10-28-action-search):
//          "audiobook" offers Save as for the book being read, "dark" the theme with where it lives and its value,
//          and Hindi and Bengali find them by their own words and by the English ones.

package io.github.skm0078.audora.ui.search

import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookAvailability
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.LibraryQuery
import io.github.skm0078.audora.core.search.ActionSearch
import io.github.skm0078.audora.ui.AppDestination
import io.github.skm0078.audora.ui.library.BookAction
import io.github.skm0078.audora.ui.library.LibraryItem
import io.github.skm0078.audora.ui.library.LibraryListState
import io.github.skm0078.audora.ui.settings.SettingKey
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class FoundIndexTest {
    private val gora = item("gora", "Gora")
    private val chokher = item("chokher", "Chokher Bali")
    private val english = XmlTexts("values", "values")

    @Test
    fun `audiobook offers saving the book being read`() {
        val rows = find("audiobook", english, library(latest = gora))
        val first = rows.first()
        assertEquals(FoundTarget.ForBook(gora.book, BookPick.Audiobook), first.target)
        assertEquals("Save Gora as an audiobook", first.title)
        assertEquals("The book you're reading · Save as", first.where)
    }

    @Test
    fun `a typed title names another book`() {
        val rows = find("chokher audiobook", english, library(latest = gora))
        assertEquals(FoundTarget.ForBook(chokher.book, BookPick.Audiobook), rows.first().target)
        assertEquals("From your library · Save as", rows.first().where)
    }

    @Test
    fun `a title alone leaves the books to the grid`() {
        assertTrue(find("chokher", english, library(latest = gora)).none { it.target is FoundTarget.ForBook })
    }

    @Test
    fun `dark offers the theme with where it lives and its value`() {
        val values = mapOf(SettingKey.THEME to "System")
        val rows = find("dark", english, library(latest = gora), values)
        assertEquals(FoundTarget.Setting(SettingKey.THEME), rows.first().target)
        assertEquals("Theme", rows.first().title)
        assertEquals("You › Reading and look · now System", rows.first().where)
        assertTrue(rows.any { it.target == FoundTarget.Setting(SettingKey.TRUE_BLACK) })
    }

    @Test
    fun `a setting on Advanced says so in its path`() {
        val row = find("volume", english, library(latest = null)).first()
        assertEquals(FoundTarget.Setting(SettingKey.VOLUME_KEYS), row.target)
        assertEquals("You › Advanced › More reading options", row.where)
    }

    @Test
    fun `places and the library's tools are found too`() {
        assertEquals(FoundTarget.Screen(AppDestination.Voices()), find("voices", english, library(null)).first().target)
        val stats = find("stats", english, library(null)).map { it.target }
        assertTrue(stats.toString(), stats.any { it is FoundTarget.Library })
    }

    @Test
    fun `Hindi and Bengali find the theme by their own word and by the English one`() {
        val hindi = XmlTexts("values-hi", "values")
        val bengali = XmlTexts("values-bn", "values")
        assertEquals(FoundTarget.Setting(SettingKey.THEME), find("थीम", hindi, library(gora)).first().target)
        assertEquals(FoundTarget.Setting(SettingKey.THEME), find("dark", hindi, library(gora)).first().target)
        assertEquals(FoundTarget.Setting(SettingKey.THEME), find("থিম", bengali, library(gora)).first().target)
        val saved = find("অডিওবুক", bengali, library(gora)).first()
        assertEquals(FoundTarget.ForBook(gora.book, BookPick.Audiobook), saved.target)
        assertTrue(saved.title, saved.title.contains("Gora"))
    }

    @Test
    fun `a book whose file is lost offers nothing`() {
        val lost = gora.copy(availability = BookAvailability.MISSING)
        assertTrue(find("audiobook", english, library(latest = lost)).none { it.target is FoundTarget.ForBook })
    }

    @Test
    fun `a finished book is not offered as finished again`() {
        val rows = find("finished", english, library(latest = gora)).map { it.target }
        assertTrue(rows.toString(), FoundTarget.ForBook(gora.book, BookPick.Action(BookAction.MARK_FINISHED)) in rows)
        assertTrue(FoundTarget.ForBook(gora.book, BookPick.Action(BookAction.MARK_NOT_STARTED)) !in rows)
    }

    @Test
    fun `every setting has its words in all three languages`() {
        listOf("values", "values-hi", "values-bn").forEach { folder ->
            val texts = XmlTexts(folder, "values")
            SettingKey.entries.forEach { key ->
                assertTrue("$folder $key", texts.text(key.title).isNotBlank())
                key.terms?.let { assertTrue("$folder $key terms", texts.text(it).isNotBlank()) }
            }
        }
    }

    private fun find(
        query: String,
        texts: FoundTexts,
        list: LibraryListState.Loaded,
        values: Map<SettingKey, String> = emptyMap(),
    ): List<FoundRow> {
        val shown = list.copy(query = LibraryQuery(text = query))
        return ActionSearch.find(query, FoundIndex.entries(texts, FoundIndex.bookFor(query, shown), values))
    }

    private fun library(latest: LibraryItem?) =
        LibraryListState.Loaded(items = listOfNotNull(latest), all = listOf(gora, chokher), latest = latest)

    private fun item(id: String, title: String) =
        LibraryItem(Book(BookId(id), "content://$id", title, 1L, "fp-$id", 10, 0L, null), BookAvailability.AVAILABLE)

    /** The app's strings read from its resource files, by their R ids; unit tests run in the module's folder. */
    private class XmlTexts(local: String, englishFolder: String) : FoundTexts {
        private val local = stringsIn(local)
        private val englishOnes = stringsIn(englishFolder)

        override fun text(id: Int): String = lookUp(local, id) ?: lookUp(englishOnes, id).orEmpty()

        override fun english(id: Int): String = lookUp(englishOnes, id).orEmpty()

        override fun format(id: Int, vararg args: String): String = String.format(Locale.ROOT, text(id), *args)

        private fun lookUp(strings: Map<String, String>, id: Int): String? = strings[NAMES.getValue(id)]

        companion object {
            private val NAMES: Map<Int, String> = R.string::class.java.fields.associate { it.getInt(null) to it.name }

            private fun stringsIn(folder: String): Map<String, String> =
                File("src/main/res/$folder").listFiles { file -> file.extension == "xml" }.orEmpty()
                    .flatMap { file ->
                        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
                        val nodes = root.getElementsByTagName("string")
                        (0 until nodes.length).map { nodes.item(it) as Element }
                    }
                    .associate { it.getAttribute("name") to unescape(it.textContent) }

            private fun unescape(text: String) = text.replace("\\'", "'").replace("\\\"", "\"")
        }
    }
}
