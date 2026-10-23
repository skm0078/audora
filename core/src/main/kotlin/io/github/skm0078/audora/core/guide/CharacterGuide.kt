// File: core/src/main/kotlin/io/github/skm0078/audora/core/guide/CharacterGuide.kt
// Purpose: The listener's own list of who is who in a book, shown only up to where they are (plan item 80).

package io.github.skm0078.audora.core.guide

import io.github.skm0078.audora.core.config.CharacterGuideConfig
import io.github.skm0078.audora.core.text.TextCuts

/**
 * One character as the listener wrote them down: [name], an optional [note] ("Ravi's sister, a doctor") and where
 * they first meet them: [page] (0-based) and [charStart], the place on it (0 = the page's start, as for an entry whose
 * page was typed in). The listener's own material: stored and backed up, never logged.
 */
data class GuideEntry(val id: Long, val name: String, val note: String, val page: Int, val charStart: Int = 0) {
    init {
        require(page >= 0) { "page must be >= 0, was $page" }
        require(charStart >= 0) { "charStart must be >= 0, was $charStart" }
    }
}

/** What the guide shows at a page: the characters met so far, by name, and how many come later (not named). */
data class GuideView(val shown: List<GuideEntry>, val later: Int)

/** Pure rules of the guide; [CharacterGuideStore] keeps the entries. */
class CharacterGuide(private val config: CharacterGuideConfig = CharacterGuideConfig()) {
    /**
     * Spoiler-free: only characters met at or before the place ([page], [charStart]) are shown, sorted by name; those
     * met later, on this page too, are only counted, so a re-listen or a jump back never reveals who comes next.
     */
    fun viewAt(entries: List<GuideEntry>, page: Int, charStart: Int = Int.MAX_VALUE): GuideView {
        val (shown, later) = entries.partition { it.page < page || (it.page == page && it.charStart <= charStart) }
        return GuideView(shown.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }), later.size)
    }

    /**
     * [entries] with [entry] added, or replacing the one with its id (id 0 means new); null when the name is blank
     * or a new one would pass [CharacterGuideConfig.maxEntries]. Name and note are trimmed and cut to their limits,
     * between whole characters.
     */
    fun saved(entries: List<GuideEntry>, entry: GuideEntry): List<GuideEntry>? {
        val name = TextCuts.take(entry.name.trim(), config.maxNameChars).trimEnd()
        val isNew = entries.none { it.id == entry.id }
        if (name.isEmpty() || (isNew && entries.size >= config.maxEntries)) return null
        val id = if (isNew) (entries.maxOfOrNull { it.id } ?: 0L) + 1 else entry.id
        val clean = entry.copy(
            id = id,
            name = name,
            note = TextCuts.take(entry.note.trim(), config.maxNoteChars).trimEnd(),
        )
        return entries.filterNot { it.id == id } + clean
    }

    fun removed(entries: List<GuideEntry>, id: Long): List<GuideEntry> = entries.filterNot { it.id == id }
}
