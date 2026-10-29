// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/LibraryAction.kt
// Purpose: The library's own tools: the side panel's Your library rows and the Add a book sheet's ways in (plan items
//          62, 26, 28; ADR-066; ADR 2026-10-29-add-a-book; ADR 2026-10-29-tools-in-the-panel).

package io.github.skm0078.audora.ui.library

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.skm0078.audora.R

/** Where a [LibraryAction] is offered. */
enum class LibraryActionPlace {
    /** The side panel's Your library rows, in the phone drawer and the tablet sidebar. */
    PANEL,

    /** The Add a book sheet, under Choose a file: every way a book comes in (ADR 2026-10-29-add-a-book). */
    ADD_SHEET,

    /** Only its own button on the library (the empty library's Try a sample). */
    BUTTON,
}

/**
 * Each entry shows in its [place], in this order, with [icon] beside it and [hint] behind its info button; the
 * activity does the work, or the library screen for its own sheets.
 */
enum class LibraryAction(
    @StringRes val label: Int,
    @StringRes val hint: Int,
    @DrawableRes val icon: Int,
    val place: LibraryActionPlace = LibraryActionPlace.PANEL,
) {
    /** Takes a photo of a page with the camera app and reads it as a one-page book (plan item 62, ADR-043). */
    POINT_AND_READ(
        R.string.library_point_and_read,
        R.string.menu_hint_point_and_read,
        R.drawable.ic_camera,
        LibraryActionPlace.ADD_SHEET,
    ),

    /** Pictures from the gallery (the photo picker, no media permission) read as one book (ADR-066). */
    READ_PICTURES(
        R.string.library_read_pictures,
        R.string.menu_hint_read_pictures,
        R.drawable.ic_photos,
        LibraryActionPlace.ADD_SHEET,
    ),

    /** The text copied last, read as a text book like text shared to ListenLock (ADR-066). */
    READ_CLIPBOARD(
        R.string.library_read_clipboard,
        R.string.menu_hint_read_clipboard,
        R.drawable.ic_paste,
        LibraryActionPlace.ADD_SHEET,
    ),

    /** A web page from its address, fetched once on the listener's tap and read as its article (CF-12). */
    READ_WEB_PAGE(
        R.string.library_read_web_page,
        R.string.menu_hint_read_web_page,
        R.drawable.ic_globe,
        LibraryActionPlace.ADD_SHEET,
    ),

    /** Free book catalogues (OPDS), a curated list checked weekly; Download adds a book (CF-15, ADR 2026-10-28). */
    BOOK_CATALOGUES(
        R.string.library_book_catalogues,
        R.string.menu_hint_book_catalogues,
        R.drawable.ic_library,
        LibraryActionPlace.ADD_SHEET,
    ),

    /** The Add a book sheet; the library's Add book button opens it too (ADR 2026-10-29-add-a-book). */
    ADD_BOOK(R.string.library_add_pdf, R.string.add_book_hint, R.drawable.ic_add),

    /** The library screen's own sheet: a name, grid or list, and which home parts show (2026-10-28-make-it-yours). */
    MAKE_IT_YOURS(R.string.library_make_it_yours, R.string.menu_hint_make_it_yours, R.drawable.ic_palette),

    /** Makes, renames and removes the listener's collections; the library's own chip opens the same sheet. */
    EDIT_COLLECTIONS(R.string.collections_edit, R.string.menu_hint_edit_collections, R.drawable.ic_edit),

    /** Lists the watched folders, whose new books join the library when it opens (plan item 26, ADR-044). */
    WATCHED_FOLDERS(R.string.library_watched_folders, R.string.menu_hint_watched_folders, R.drawable.ic_folder),

    /** Syncs places, bookmarks, highlights and books through a folder the listener picks (CF-16). */
    SYNC_FOLDER(R.string.library_sync_folder, R.string.menu_hint_sync_folder, R.drawable.ic_sync),

    /** Saves one backup file, or restores one on a new phone (plan item 28, ADR-046). */
    BACKUP(R.string.library_backup, R.string.menu_hint_backup, R.drawable.ic_backup),

    /** Fill-in-the-blank questions from every book's highlights, read aloud (plan item 70, ADR-048). */
    QUIZ(R.string.library_quiz, R.string.menu_hint_quiz, R.drawable.ic_school),

    /** Time listened, streaks and books finished, kept on the phone only (CF-8). */
    LISTENING_STATS(R.string.library_listening_stats, R.string.menu_hint_listening_stats, R.drawable.ic_bar_chart),

    /** The ListenLock Cookbook: the site's recipes as a book in the app's language (ADR 2026-10-29-in-app-cookbook). */
    COOKBOOK(R.string.cookbook_open, R.string.menu_hint_cookbook, R.drawable.ic_headphones),

    /** A short page about ListenLock in the app's language, saved as a text book and opened (empty library only). */
    TRY_SAMPLE(R.string.sample_try, R.string.sample_try_hint, R.drawable.ic_article, LibraryActionPlace.BUTTON),
    ;

    companion object {
        /** The side panel's Your library rows, in order. */
        val inPanel: List<LibraryAction> get() = entries.filter { it.place == LibraryActionPlace.PANEL }
    }
}
