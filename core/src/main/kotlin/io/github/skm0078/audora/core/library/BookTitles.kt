// File: core/src/main/kotlin/io/github/skm0078/audora/core/library/BookTitles.kt
// Purpose: Turns a picked file's display name into the title shown in the library.

package io.github.skm0078.audora.core.library

object BookTitles {
    /** Used when the provider gives no usable name; the UI shows its own localized text for it. */
    const val UNTITLED = ""

    private const val PDF_EXTENSION = ".pdf"

    /** "Moby Dick.PDF" → "Moby Dick"; "  " or null → [UNTITLED]. Underscores stay: they may be meaningful. */
    fun fromDisplayName(displayName: String?): String {
        val name = displayName?.trim().orEmpty()
        val withoutExtension = if (name.endsWith(PDF_EXTENSION, ignoreCase = true)) {
            name.dropLast(PDF_EXTENSION.length).trimEnd()
        } else {
            name
        }
        return withoutExtension.ifEmpty { UNTITLED }
    }
}
