// File: core/src/test/kotlin/io/github/skm0078/audora/core/guide/CharacterGuideTest.kt
// Purpose: The guide shows only characters met so far, and each book's list survives in its own file.

package io.github.skm0078.audora.core.guide

import io.github.skm0078.audora.core.config.CharacterGuideConfig
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterGuideTest {
    private val guide = CharacterGuide(CharacterGuideConfig(maxNameChars = 10, maxEntries = 3))
    private val folder = Files.createTempDirectory("guide").toFile()
    private val store = CharacterGuideStore(folder)

    @After
    fun cleanUp() {
        folder.deleteRecursively()
    }

    @Test
    fun `only characters met so far are named, by name, the rest are counted`() {
        val entries = listOf(
            GuideEntry(1, "ravi", "", page = 3),
            GuideEntry(2, "Anita", "his sister", page = 1),
            GuideEntry(3, "The stranger", "", page = 40),
        )

        val view = guide.viewAt(entries, page = 3)

        assertEquals(listOf("Anita", "ravi"), view.shown.map { it.name })
        assertEquals(1, view.later)
    }

    @Test
    fun `saving trims, cuts, numbers new entries, replaces edited ones and refuses blanks and overflow`() {
        val one = guide.saved(emptyList(), GuideEntry(0, "  Meenakshi Iyer ", " a judge ", 2))!!
        val edited = guide.saved(one, one.single().copy(note = "the judge"))!!
        val full = listOf(GuideEntry(0, "B", "", 0), GuideEntry(0, "C", "", 0))
            .fold(edited) { list, entry -> guide.saved(list, entry)!! }

        assertEquals(GuideEntry(1, "Meenakshi", "a judge", 2), one.single())
        assertEquals("the judge", edited.single().note)
        assertEquals(listOf(1L, 2L, 3L), full.map { it.id })
        assertNull(guide.saved(full, GuideEntry(0, "D", "", 0)))
        assertNull(guide.saved(edited, GuideEntry(0, "   ", "", 0)))
        assertEquals(listOf(2L, 3L), guide.removed(full, 1).map { it.id })
    }

    @Test
    fun `each book's guide round-trips with tabs and line breaks, an empty one deletes its file`() {
        val entries = listOf(GuideEntry(1, "A\tB", "line one\nline two \\ end", 5))

        store.save("book-1", entries)

        assertEquals(entries, store.load("book-1"))
        assertEquals(emptyList<GuideEntry>(), store.load("book-2"))
        store.save("book-1", emptyList())
        assertEquals(0, folder.listFiles()!!.size)
    }

    @Test
    fun `on the listener's own page only those met before their place show`() {
        val entries = listOf(GuideEntry(1, "Early", "", page = 3, charStart = 10), GuideEntry(2, "Late", "", 3, 500))

        val view = guide.viewAt(entries, page = 3, charStart = 200)

        assertEquals(listOf("Early"), view.shown.map { it.name })
        assertEquals(1, view.later)
    }

    @Test
    fun `a name is cut between whole characters, never inside a surrogate pair or a conjunct`() {
        val cut = guide.saved(emptyList(), GuideEntry(0, "abcdefghi\uD83D\uDE00", "", 0))!!.single().name
        assertEquals("abcdefghi", cut)
        val hindi = CharacterGuide(CharacterGuideConfig(maxNameChars = 3))
        assertEquals(
            "\u0905",
            hindi.saved(emptyList(), GuideEntry(0, "\u0905\u0915\u094D\u0937", "", 0))!!.single().name,
        )
    }

    @Test
    fun `places on the page round-trip, and a version 1 file reads with the page's start`() {
        val entries = listOf(GuideEntry(1, "A", "", 5, charStart = 120))
        store.save("book-1", entries)
        assertEquals(entries, store.load("book-1"))

        val file = folder.listFiles()!!.single()
        file.writeText("audora-character-guide 1\n7\t2\tOld\tnote\nbroken line\n")
        assertEquals(listOf(GuideEntry(7, "Old", "note", 2, 0)), store.load("book-1"))
    }

    @Test
    fun `a guide from a newer format is set aside, not overwritten by the next save`() {
        store.save("book-1", listOf(GuideEntry(1, "A", "", 0)))
        val file = folder.listFiles()!!.single()
        file.writeText("audora-character-guide 9\nfuture data\n")

        assertEquals(emptyList<GuideEntry>(), store.load("book-1"))
        assertTrue(store.save("book-1", listOf(GuideEntry(1, "B", "", 0))))

        val aside = folder.listFiles()!!.single { it.name.contains(".unreadable-") }
        assertEquals("audora-character-guide 9\nfuture data\n", aside.readText())
        assertEquals("B", store.load("book-1").single().name)
    }

    @Test
    fun `a save that cannot be written keeps the old file`() {
        store.save("book-1", listOf(GuideEntry(1, "A", "", 0)))
        val file = folder.listFiles()!!.single()
        java.io.File(file.path + ".tmp").mkdirs() // A folder where the temp file must go.

        assertFalse(store.save("book-1", listOf(GuideEntry(1, "B", "", 0))))

        assertEquals("A", store.load("book-1").single().name)
    }
}
