// File: app/src/test/kotlin/io/github/skm0078/audora/ui/whatsnew/WhatsNewEntriesTest.kt
// Purpose: Every shipped "What's new" file parses in en/hi/bn, and each new build shows only what is new, once.

package io.github.skm0078.audora.ui.whatsnew

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsNewEntriesTest {
    private val directory = File("src/main/assets/${WhatsNewEntries.DIRECTORY}")
    private val shipped = directory.listFiles().orEmpty().filter { WhatsNewEntries.isEntryFile(it.name) }

    private fun entry(name: String) = WhatsNewEntry(name, WhatsNewEntries.LANGUAGES.associateWith { "$name $it" })

    @Test
    fun `every file in the folder is a dated entry`() {
        val strays = directory.listFiles().orEmpty().map { it.name }.filterNot(WhatsNewEntries::isEntryFile)
        assertEquals("name files <yyyy-mm-dd>-<slug>.txt", emptyList<String>(), strays)
        assertTrue("no entries found in $directory", shipped.isNotEmpty())
    }

    @Test
    fun `every shipped entry has English, Hindi and Bengali`() {
        shipped.forEach { file ->
            val parsed = WhatsNewEntries.parse(file.nameWithoutExtension, file.readText())
            WhatsNewEntries.LANGUAGES.forEach { assertTrue(file.name, parsed.textIn(it).isNotBlank()) }
        }
    }

    @Test
    fun `the entries carried over from the numbered list all exist`() {
        val names = shipped.map { it.nameWithoutExtension }.toSet()
        assertEquals(emptySet<String>(), WhatsNewEntries.LEGACY_ENTRIES - names)
    }

    @Test
    fun `a missing language, a repeated one or a stray line is refused`() {
        val bad = listOf("en: a\nhi: b", "en: a\nhi: b\nbn: c\nen: d", "en: a\nhi: b\nbn: c\nhello", "fr: a")
        bad.forEach { content ->
            val failed = runCatching { WhatsNewEntries.parse("2026-01-01-x", content) }.isFailure
            assertTrue(content, failed)
        }
    }

    @Test
    fun `parsing keeps the words after the first colon`() {
        val parsed = WhatsNewEntries.parse("2026-01-01-x", "en: Settings: Voices\n\nhi: क\nbn: খ\n")
        assertEquals("Settings: Voices", parsed.textIn("en"))
        assertEquals("Settings: Voices", parsed.textIn("fr"))
    }

    @Test
    fun `a new build shows only its new entries, newest first, and then nothing`() {
        val before = listOf(entry("2026-10-01-a"), entry("2026-10-02-b"))
        val seen = before.map { it.name }.toSet()
        val after = before + entry("2026-10-03-c") + entry("2026-10-04-d")
        assertEquals(listOf("2026-10-04-d", "2026-10-03-c"), WhatsNewEntries.unseen(after, seen).map { it.name })
        val seenNow = seen + after.map { it.name }
        assertTrue(WhatsNewEntries.unseen(after, seenNow).isEmpty())
    }

    @Test
    fun `entries added by two branches in either order give the same list`() {
        val base = listOf(entry("2026-10-01-a"))
        val one = entry("2026-10-05-one")
        val two = entry("2026-10-05-two")
        val seen = setOf("2026-10-01-a")
        assertEquals(
            WhatsNewEntries.unseen(base + one + two, seen),
            WhatsNewEntries.unseen(base + two + one, seen),
        )
    }

    @Test
    fun `a long gap shows at most the newest few`() {
        val many = (1..20).map { entry("2026-10-%02d-x".format(it)) }
        val shown = WhatsNewEntries.unseen(many, emptySet())
        assertEquals(WhatsNewEntries.MAX_SHOWN, shown.size)
        assertEquals("2026-10-20-x", shown.first().name)
    }

    @Test
    fun `an update from a numbered build does not show the carried-over items again`() {
        val seen = WhatsNewEntries.seenEntries(stored = null, legacySeen = WhatsNewEntries.LEGACY_LAST_LIST)
        assertEquals(WhatsNewEntries.LEGACY_ENTRIES, seen)
        val shipped = WhatsNewEntries.LEGACY_ENTRIES.map(::entry) + entry("2099-01-01-new")
        assertEquals(listOf("2099-01-01-new"), WhatsNewEntries.unseen(shipped, seen).map { it.name })
    }

    @Test
    fun `someone who missed the last numbered list sees it, and stored names win once written`() {
        assertTrue(WhatsNewEntries.seenEntries(stored = null, legacySeen = 1).isEmpty())
        assertTrue(WhatsNewEntries.seenEntries(stored = null, legacySeen = 0).isEmpty())
        val stored = setOf("2026-10-01-a")
        assertEquals(stored, WhatsNewEntries.seenEntries(stored, legacySeen = WhatsNewEntries.LEGACY_LAST_LIST))
        assertTrue(WhatsNewEntries.seenEntries(emptySet(), WhatsNewEntries.LEGACY_LAST_LIST).isEmpty())
    }
}
