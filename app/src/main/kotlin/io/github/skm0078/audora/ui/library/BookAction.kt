// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/BookAction.kt
// Purpose: The book menu's studio and export entries, added without touching the menu's layout (plan items 6, 69, 76).

package io.github.skm0078.audora.ui.library

import androidx.annotation.StringRes
import io.github.skm0078.audora.R

/** Each entry shows in a book's menu while its file can be opened, in this order; the activity starts the work. */
enum class BookAction(@StringRes val label: Int) {
    /** Renders the book ahead while the phone charges (plan item 6, ADR-039). */
    STUDIO(R.string.library_studio_mode),

    /** Clean text as EPUB 3 and plain text for screen readers and other apps (plan item 69, ADR-040). */
    ACCESSIBLE_COPY(R.string.library_accessible_copy),

    /** EPUB 3 with every sentence's audio synced to its text (plan item 76, ADR-040). */
    TALKING_BOOK(R.string.library_talking_book),

    /** The book's highlights, bookmarks and voice notes as Markdown and text (plan item 30). */
    EXPORT_NOTES(R.string.library_export_notes),
}
