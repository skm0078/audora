// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/BookAction.kt
// Purpose: The book menu's studio, export and progress entries, added without touching the menu's layout (plan items
//          6, 69, 76).

package io.github.skm0078.audora.ui.library

import androidx.annotation.StringRes
import io.github.skm0078.audora.R

/**
 * Each entry shows in a book's menu while its file can be opened and it [appliesTo] the book, in this order; the
 * activity starts the work.
 * [hint] is the line under the label: what it does, where the result goes and what it costs.
 */
enum class BookAction(@StringRes val label: Int, @StringRes val hint: Int) {
    /** Renders the book ahead while the phone charges (plan item 6, ADR-039). */
    STUDIO(R.string.library_studio_mode, R.string.menu_hint_studio),

    /** Clean text as EPUB 3 and plain text for screen readers and other apps (plan item 69, ADR-040). */
    ACCESSIBLE_COPY(R.string.library_accessible_copy, R.string.menu_hint_accessible),

    /** EPUB 3 with every sentence's audio synced to its text (plan item 76, ADR-040). */
    TALKING_BOOK(R.string.library_talking_book, R.string.menu_hint_talking_book),

    /** The book's highlights, bookmarks and voice notes as Markdown and text (plan item 30). */
    EXPORT_NOTES(R.string.library_export_notes, R.string.menu_hint_export_notes),

    /** The book as a picture to share: cover, title and author (ADR 2026-10-27-share-cards). */
    SHARE_CARD(R.string.share_card_book_title, R.string.menu_hint_share_card),

    /** The listener's own list of who is who, shown up to where they are (plan item 80). */
    CHARACTER_GUIDE(R.string.library_character_guide, R.string.menu_hint_character_guide),

    /** Puts the book's place on its last page, so it shows as Finished (the library's Finished filter). */
    MARK_FINISHED(R.string.library_mark_finished, R.string.menu_hint_mark_finished),

    /** Forgets the book's place: Not started, and it opens on its first page. */
    MARK_NOT_STARTED(R.string.library_mark_not_started, R.string.menu_hint_mark_not_started),
    ;

    /** Whether the action means anything for [item] now: a finished book is not marked finished again. */
    fun appliesTo(item: LibraryItem): Boolean = when (this) {
        MARK_FINISHED -> item.book.pageCount != null && item.progress?.isFinished != true
        MARK_NOT_STARTED -> item.progress != null
        else -> true
    }
}
