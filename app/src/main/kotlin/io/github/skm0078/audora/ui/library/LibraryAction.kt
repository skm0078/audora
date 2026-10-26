// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/LibraryAction.kt
// Purpose: The library's ⋮ menu entries, added without touching the top bar's layout (plan items 62, 26, 28; ADR-066).

package io.github.skm0078.audora.ui.library

import androidx.annotation.StringRes
import io.github.skm0078.audora.R

/**
 * Each entry with [inMenu] shows in the library's ⋮ menu, in this order, with [hint] under it; the activity does the
 * work. The others are buttons elsewhere on the library (the empty library's Try a sample).
 */
enum class LibraryAction(@StringRes val label: Int, @StringRes val hint: Int, val inMenu: Boolean = true) {
    /** Takes a photo of a page with the camera app and reads it as a one-page book (plan item 62, ADR-043). */
    POINT_AND_READ(R.string.library_point_and_read, R.string.menu_hint_point_and_read),

    /** Pictures from the gallery (the photo picker, no media permission) read as one book (ADR-066). */
    READ_PICTURES(R.string.library_read_pictures, R.string.menu_hint_read_pictures),

    /** The text copied last, read as a text book like text shared to ListenLock (ADR-066). */
    READ_CLIPBOARD(R.string.library_read_clipboard, R.string.menu_hint_read_clipboard),

    /** A web page from its address, fetched once on the listener's tap and read as its article (CF-12). */
    READ_WEB_PAGE(R.string.library_read_web_page, R.string.menu_hint_read_web_page),

    /** Free book catalogues (OPDS): Project Gutenberg, or one the listener adds; Download adds a book (CF-15). */
    BOOK_CATALOGUES(R.string.library_book_catalogues, R.string.menu_hint_book_catalogues),

    /** Lists the watched folders, whose new books join the library when it opens (plan item 26, ADR-044). */
    WATCHED_FOLDERS(R.string.library_watched_folders, R.string.menu_hint_watched_folders),

    /** Saves one backup file, or restores one on a new phone (plan item 28, ADR-046). */
    BACKUP(R.string.library_backup, R.string.menu_hint_backup),

    /** Fill-in-the-blank questions from every book's highlights, read aloud (plan item 70, ADR-048). */
    QUIZ(R.string.library_quiz, R.string.menu_hint_quiz),

    /** A short page about ListenLock in the app's language, saved as a text book and opened (empty library only). */
    TRY_SAMPLE(R.string.sample_try, R.string.sample_try_hint, inMenu = false),
}
