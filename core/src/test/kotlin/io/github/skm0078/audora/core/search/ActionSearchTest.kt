// File: core/src/test/kotlin/io/github/skm0078/audora/core/search/ActionSearchTest.kt
// Purpose: Search finds actions and settings by their names and the words people type for them, best first, in
//          English, Hindi and Bengali (ADR 2026-10-28-action-search).

package io.github.skm0078.audora.core.search

import io.github.skm0078.audora.core.config.ActionSearchConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class ActionSearchTest {
    private val entries = listOf(
        ActionEntry("audiobook", "Save Gora as an audiobook", listOf("export", "mp3")),
        ActionEntry("made", "Your audiobooks", listOf("made by you")),
        ActionEntry("theme", "Theme", listOf("dark", "light", "night")),
        ActionEntry("true-black", "True black at night", listOf("dark", "oled")),
        ActionEntry("voices", "Voices", listOf("download voices")),
        ActionEntry("theme-bn", "থিম", listOf("theme", "dark")),
        ActionEntry("language-hi", "भाषा", listOf("language")),
    )

    private fun find(query: String, config: ActionSearchConfig = ActionSearchConfig()) =
        ActionSearch.find(query, entries, config)

    @Test
    fun `audiobook finds saving the book first, then the place they land`() {
        assertEquals(listOf("audiobook", "made"), find("audiobook"))
    }

    @Test
    fun `dark finds the theme before true black, keeping the list's order on a tie`() {
        assertEquals(listOf("theme", "true-black", "theme-bn"), find("dark"))
    }

    @Test
    fun `a title word beats a word it only answers to`() {
        assertEquals(listOf("true-black", "theme"), find("night"))
    }

    @Test
    fun `every typed word must match`() {
        assertEquals(listOf("true-black"), find("dark black"))
        assertEquals(emptyList<String>(), find("dark spaceship"))
    }

    @Test
    fun `case, accents and punctuation do not matter`() {
        assertEquals(listOf("theme", "theme-bn"), find("THÉME!"))
    }

    @Test
    fun `a word inside a longer word counts only from three letters`() {
        assertEquals(listOf("audiobook", "made"), find("book"))
        assertEquals(emptyList<String>(), find("oo"))
    }

    @Test
    fun `Hindi and Bengali titles are found, signs and all`() {
        assertEquals(listOf("theme-bn"), find("থিম"))
        assertEquals(listOf("language-hi"), find("भाषा"))
        assertEquals(listOf("language-hi"), find("language"))
    }

    @Test
    fun `one letter, blanks and punctuation offer nothing`() {
        assertEquals(emptyList<String>(), find("d"))
        assertEquals(emptyList<String>(), find("   "))
        assertEquals(emptyList<String>(), find("?!"))
    }

    @Test
    fun `results stop at the configured count`() {
        assertEquals(listOf("theme"), find("dark", ActionSearchConfig(maxResults = 1)))
    }

    @Test
    fun `a book's title narrows its actions but never finds them alone`() {
        val book = listOf(
            ActionEntry("play-gora", "Play", context = listOf("Gora")),
            ActionEntry("save-gora", "Save as audiobook", context = listOf("Gora")),
        )
        assertEquals(listOf("save-gora"), ActionSearch.find("gora audiobook", book))
        assertEquals(emptyList<String>(), ActionSearch.find("gora", book))
        assertEquals(emptyList<String>(), ActionSearch.find("chokher audiobook", book))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a config with no rows is refused`() {
        ActionSearchConfig(maxResults = 0)
    }
}
