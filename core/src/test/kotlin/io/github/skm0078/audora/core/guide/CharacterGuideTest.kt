// File: core/src/test/kotlin/io/github/skm0078/audora/core/guide/CharacterGuideTest.kt
// Purpose: The guide shows only characters met so far, and each book's list survives in its own file.

package io.github.skm0078.audora.core.guide

import io.github.skm0078.audora.core.config.CharacterGuideConfig
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
