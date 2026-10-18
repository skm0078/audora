// File: core/src/main/kotlin/io/github/skm0078/audora/core/guide/CharacterGuide.kt
// Purpose: The listener's own list of who is who in a book, shown only up to where they are (plan item 80).

package io.github.skm0078.audora.core.guide

import io.github.skm0078.audora.core.config.CharacterGuideConfig

/**
 * One character as the listener wrote them down: [name], an optional [note] ("Ravi's sister, a doctor") and
 * [page], the 0-based page where they first meet them. The listener's own material: stored and backed up, never
 * logged.
 */
data class GuideEntry(val id: Long, val name: String, val note: String, val page: Int) {
    init {
        require(page >= 0) { "page must be >= 0, was $page" }
    }
}

/** What the guide shows at a page: the characters met so far, by name, and how many come later (not named). */
data class GuideView(val shown: List<GuideEntry>, val later: Int)

/** Pure rules of the guide; [CharacterGuideStore] keeps the entries. */
class CharacterGuide(private val config: CharacterGuideConfig = CharacterGuideConfig()) {
    /**
     * Spoiler-free: only characters met at or before [page] are shown, sorted by name; those met later are only
     * counted, so a re-listen or a jump back never reveals who comes next.
     */
    fun viewAt(entries: List<GuideEntry>, page: Int): GuideView {
        val (shown, later) = entries.partition { it.page <= page }
        return GuideView(shown.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }), later.size)
    }

    /**
     * [entries] with [entry] added, or replacing the one with its id (id 0 means new); null when the name is blank
     * or a new one would pass [CharacterGuideConfig.maxEntries]. Name and note are trimmed and cut to their limits.
     */
    fun saved(entries: List<GuideEntry>, entry: GuideEntry): List<GuideEntry>? {
        val name = entry.name.trim().take(config.maxNameChars).trimEnd()
        val isNew = entries.none { it.id == entry.id }
        if (name.isEmpty() || (isNew && entries.size >= config.maxEntries)) return null
        val id = if (isNew) (entries.maxOfOrNull { it.id } ?: 0L) + 1 else entry.id
        val clean = entry.copy(id = id, name = name, note = entry.note.trim().take(config.maxNoteChars).trimEnd())
        return entries.filterNot { it.id == id } + clean
    }

    fun removed(entries: List<GuideEntry>, id: Long): List<GuideEntry> = entries.filterNot { it.id == id }
}
