// File: core/src/main/kotlin/io/github/skm0078/audora/core/library/BookTitles.kt
// Purpose: Turns a picked file's display name into the title shown in the library.

package io.github.skm0078.audora.core.library

object BookTitles {
    /** Used when the provider gives no usable name; the UI shows its own localized text for it. */
    const val UNTITLED = ""

    /** The book formats' extensions (ADR-042, ADR-066); any other extension is part of the name. */
    private val BOOK_EXTENSIONS =
        listOf(".pdf", ".epub", ".txt", ".docx", ".odt", ".rtf", ".html", ".htm", ".xhtml", ".md", ".markdown")

    /** "Moby Dick.PDF" → "Moby Dick"; "  " or null → [UNTITLED]. Underscores stay: they may be meaningful. */
    fun fromDisplayName(displayName: String?): String {
        val name = displayName?.trim().orEmpty()
        val extension = BOOK_EXTENSIONS.firstOrNull { name.endsWith(it, ignoreCase = true) }
        val withoutExtension = extension?.let { name.dropLast(it.length).trimEnd() } ?: name
        return withoutExtension.ifEmpty { UNTITLED }
    }
}
