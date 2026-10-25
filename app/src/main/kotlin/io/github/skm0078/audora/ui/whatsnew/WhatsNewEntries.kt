// File: app/src/main/kotlin/io/github/skm0078/audora/ui/whatsnew/WhatsNewEntries.kt
// Purpose: "What's new" items as one small file per change (assets/whatsnew/), and which of them a listener has
//          not seen yet, so two PRs that each add an item never edit the same line
//          (ADR 2026-10-25-parallel-prs-merge-cleanly).

package io.github.skm0078.audora.ui.whatsnew

import io.github.skm0078.audora.ui.library.LibraryListState

/** One item, in each language the app speaks. [name] is its file name without ".txt" ("2026-10-25-indic-names"). */
data class WhatsNewEntry(val name: String, val text: Map<String, String>) {
    /** The item in [language], or in English when that language is missing. */
    fun textIn(language: String): String = text[language] ?: text.getValue(WhatsNewEntries.FALLBACK_LANGUAGE)
}

/** What to do at start: show the list, or mark it seen unshown (a new listener, who has nothing to compare). */
enum class WhatsNewStart { SHOW, MARK_SEEN, NOTHING }

/**
 * From whether any entry is unseen ([hasUnseen]) and the library's first settled state: only someone who already has
 * books is shown the unseen entries; someone with an empty library is new, so everything is marked seen for them.
 */
fun whatsNewStart(hasUnseen: Boolean, library: LibraryListState): WhatsNewStart = when {
    !hasUnseen || library !is LibraryListState.Loaded -> WhatsNewStart.NOTHING
    library.all.isEmpty() -> WhatsNewStart.MARK_SEEN
    else -> WhatsNewStart.SHOW
}

object WhatsNewEntries {
    /** The assets folder that holds one `<date>-<slug>.txt` per item. */
    const val DIRECTORY = "whatsnew"
    const val EXTENSION = ".txt"
    const val FALLBACK_LANGUAGE = "en"

    /** Every entry carries a line for each of these; a unit test parses every shipped file. */
    val LANGUAGES = listOf("en", "hi", "bn")

    /** Shown at once at most (newest first); the rest are marked seen with them, so a long gap is no wall of text. */
    const val MAX_SHOWN = 8

    /**
     * The last numbered list (ADR-070's `WHATS_NEW_ID`) and the entries it held. Someone who saw that list has seen
     * these; never changes again.
     */
    const val LEGACY_LAST_LIST = 9
    val LEGACY_ENTRIES = setOf(
        "2026-10-25-feelings-you-can-hear",
        "2026-10-25-indic-names-found",
        "2026-10-25-supertonic-reads-hindi",
        "2026-10-25-noncommercial-voices-removed",
        "2026-10-25-more-speakers-found",
        "2026-10-25-voices-match-gender",
    )

    private val NAME = Regex("""\d{4}-\d{2}-\d{2}-[a-z0-9]+(-[a-z0-9]+)*""")
    private val LINE = Regex("""^([a-z]{2}):\s*(.+)$""")

    /** A file in [DIRECTORY] that is an entry ("2026-10-25-indic-names.txt"); anything else there is ignored. */
    fun isEntryFile(fileName: String): Boolean =
        fileName.endsWith(EXTENSION) && NAME.matches(fileName.removeSuffix(EXTENSION))

    /**
     * Reads `en: …`, `hi: …` and `bn: …` lines (blank lines are skipped). Throws [IllegalArgumentException] for a
     * missing or repeated language or any other line, so a broken file fails the unit test instead of a phone.
     */
    fun parse(name: String, content: String): WhatsNewEntry {
        val text = LinkedHashMap<String, String>()
        content.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            val match = requireNotNull(LINE.matchEntire(line)) { "$name: not a 'language: text' line: $line" }
            val (language, words) = match.destructured
            require(language in LANGUAGES) { "$name: unknown language $language" }
            require(text.put(language, words.trim()) == null) { "$name: $language given twice" }
        }
        val missing = LANGUAGES - text.keys
        require(missing.isEmpty()) { "$name: missing $missing" }
        return WhatsNewEntry(name, text)
    }

    /** The entries not in [seen], newest first (names start with their date), at most [max]. */
    fun unseen(entries: List<WhatsNewEntry>, seen: Set<String>, max: Int = MAX_SHOWN): List<WhatsNewEntry> =
        entries.filter { it.name !in seen }.sortedByDescending { it.name }.take(max)

    /**
     * The entries seen so far: [stored] once it was ever written; before that, from the numbered list seen last
     * ([legacySeen]), so an update from a numbered build does not show its items again.
     */
    fun seenEntries(stored: Set<String>?, legacySeen: Int): Set<String> =
        stored ?: if (legacySeen >= LEGACY_LAST_LIST) LEGACY_ENTRIES else emptySet()
}
