// File: app/src/main/kotlin/io/github/skm0078/audora/ui/search/FoundIndex.kt
// Purpose: The small list the library search's "Actions and settings" is found from: every setting on You, the places
//          and screens, the library's tools and the book menu's choices for one book (ADR 2026-10-28-action-search).

package io.github.skm0078.audora.ui.search

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.BookTitles
import io.github.skm0078.audora.core.search.ActionEntry
import io.github.skm0078.audora.core.search.ActionSearch
import io.github.skm0078.audora.ui.AppDestination
import io.github.skm0078.audora.ui.library.BookAction
import io.github.skm0078.audora.ui.library.LibraryAction
import io.github.skm0078.audora.ui.library.LibraryItem
import io.github.skm0078.audora.ui.library.LibraryListState
import io.github.skm0078.audora.ui.library.MenuPlace
import io.github.skm0078.audora.ui.library.actionIcon
import io.github.skm0078.audora.ui.settings.SettingKey
import io.github.skm0078.audora.ui.settings.SettingsPage

/** The app's words in the screen's language, and in English, so "dark" finds the theme in Hindi too. */
interface FoundTexts {
    fun text(@StringRes id: Int): String

    fun english(@StringRes id: Int): String

    fun format(@StringRes id: Int, vararg args: String): String
}

/** The book whose menu choices search offers; [reading] when it is the one read last. */
class FoundBook(val item: LibraryItem, val reading: Boolean)

object FoundIndex {
    /**
     * Every entry, in the order ties keep: the book's choices, then settings, places and the library's tools.
     * [values] is each setting's current value where the library knows it, shown after its path.
     */
    fun entries(
        texts: FoundTexts,
        book: FoundBook?,
        values: Map<SettingKey, String> = emptyMap(),
    ): List<ActionEntry<FoundRow>> =
        bookEntries(texts, book) + settingEntries(texts, values) + screenEntries(texts) + libraryEntries(texts)

    /**
     * The book a search's book actions name: the first one, most recent first, with a title word that starts with a
     * typed word of [MIN_TITLE_WORD] letters or more ("gora audiobook"), else the one read last.
     */
    fun bookFor(query: String, list: LibraryListState.Loaded): FoundBook? {
        val typed = ActionSearch.words(query).filter { it.length >= MIN_TITLE_WORD }
        val named = list.all.firstOrNull { item ->
            !item.needsLocating &&
                ActionSearch.words(item.book.title).any { w -> typed.any { w.startsWith(it) } }
        }
        val item = named ?: list.latest?.takeUnless { it.needsLocating } ?: return null
        return FoundBook(item, reading = item.book.id == list.latest?.book?.id)
    }

    private fun settingEntries(texts: FoundTexts, values: Map<SettingKey, String>) = SettingKey.entries.map { key ->
        val path = settingPath(texts, key)
        val where = values[key]?.let { texts.format(R.string.found_now, path, it) } ?: path
        entry(
            texts,
            FoundRow(FoundTarget.Setting(key), texts.text(key.title), where, R.drawable.ic_person),
            key.title,
            key.terms,
        )
    }

    /** "You › Reading and look"; "You › Advanced › Help" on a page of its own. */
    private fun settingPath(texts: FoundTexts, key: SettingKey): String {
        val you = texts.text(R.string.tab_you)
        val page = texts.format(R.string.found_path, you, texts.text(key.page.title)).takeUnless {
            key.page == SettingsPage.MAIN
        } ?: you
        return key.group?.let { texts.format(R.string.found_path, page, texts.text(it)) } ?: page
    }

    private fun screenEntries(texts: FoundTexts): List<ActionEntry<FoundRow>> {
        val you = texts.text(R.string.tab_you)
        val listening = texts.format(R.string.found_path, you, texts.text(R.string.settings_group_listening))
        val app = texts.format(R.string.found_path, you, texts.text(R.string.settings_group_app))
        val place = texts.text(R.string.found_where_place)
        return listOf(
            Screen(AppDestination.Voices(), R.string.settings_voices_title, listening, R.drawable.ic_headphones),
            Screen(AppDestination.Storage, R.string.storage_title, app, R.drawable.ic_download),
            Screen(AppDestination.Licences, R.string.licences_title, app, R.drawable.ic_info),
            Screen(AppDestination.Made, R.string.tab_made, place, R.drawable.ic_made),
            Screen(AppDestination.Jobs, R.string.jobs_title, texts.text(R.string.tab_made), R.drawable.ic_jobs),
            Screen(AppDestination.Notes, R.string.tab_notes, place, R.drawable.ic_bookmark_border),
            Screen(AppDestination.Settings, R.string.tab_you, place, R.drawable.ic_person),
        ).map {
            entry(
                texts,
                FoundRow(FoundTarget.Screen(it.destination), texts.text(it.title), it.where, it.icon),
                it.title,
            )
        }
    }

    private class Screen(
        val destination: AppDestination,
        @StringRes val title: Int,
        val where: String,
        @DrawableRes val icon: Int,
    )

    private fun libraryEntries(texts: FoundTexts): List<ActionEntry<FoundRow>> {
        val where = texts.format(R.string.found_path, texts.text(R.string.tab_library), texts.text(R.string.found_more))
        return LibraryAction.entries.filter { it.inMenu }.map { action ->
            val row = FoundRow(FoundTarget.Library(action), texts.text(action.label), where, R.drawable.ic_library)
            entry(texts, row, action.label)
        }
    }

    /** Nothing for a book whose file cannot be opened: its menu offers nothing either. */
    private fun bookEntries(texts: FoundTexts, found: FoundBook?): List<ActionEntry<FoundRow>> {
        if (found == null || found.item.needsLocating) return emptyList()
        val book = found.item.book
        val title = book.title.takeUnless { it == BookTitles.UNTITLED } ?: texts.text(R.string.library_untitled)
        val whose = texts.text(if (found.reading) R.string.found_where_reading else R.string.found_where_library)
        val saveAs = texts.format(R.string.found_dot, whose, texts.text(R.string.menu_save_as))
        val picks = listOf(
            Pick(
                BookPick.Play,
                R.string.menu_play,
                R.string.found_book_play,
                R.drawable.ic_play,
                R.string.found_terms_play,
            ),
            Pick(BookPick.UpNext, R.string.library_add_to_up_next, R.string.found_book_up_next, R.drawable.ic_queue),
            Pick(
                BookPick.Audiobook,
                R.string.library_export_audiobook,
                R.string.found_book_audiobook,
                R.drawable.ic_headphones,
                R.string.found_terms_audiobook,
                saved = true,
            ),
        ) + BookAction.entries.filter { it.appliesTo(found.item) }.map { action ->
            Pick(
                BookPick.Action(action),
                action.label,
                phraseOf(action),
                actionIcon(action),
                termsOf(action),
                saved = action.place == MenuPlace.SAVE_AS,
            )
        }
        return picks.map { pick ->
            val row = FoundRow(
                FoundTarget.ForBook(book, pick.pick),
                texts.format(pick.phrase, title),
                if (pick.saved) saveAs else whose,
                pick.icon,
            )
            entry(texts, row, pick.label, pick.terms, context = listOf(title))
        }
    }

    private class Pick(
        val pick: BookPick,
        @StringRes val label: Int,
        @StringRes val phrase: Int,
        @DrawableRes val icon: Int,
        @StringRes val terms: Int? = null,
        val saved: Boolean = false,
    )

    private fun phraseOf(action: BookAction): Int = when (action) {
        BookAction.STUDIO -> R.string.found_book_studio
        BookAction.ACCESSIBLE_COPY -> R.string.found_book_accessible
        BookAction.TALKING_BOOK -> R.string.found_book_talking
        BookAction.EXPORT_NOTES -> R.string.found_book_notes
        BookAction.SHARE_CARD -> R.string.found_book_share
        BookAction.CHARACTER_GUIDE -> R.string.found_book_guide
        BookAction.MARK_FINISHED -> R.string.found_book_finished
        BookAction.MARK_NOT_STARTED -> R.string.found_book_not_started
        BookAction.KEEP_ON_PHONE -> R.string.found_book_keep
    }

    private fun termsOf(action: BookAction): Int? = when (action) {
        BookAction.STUDIO -> R.string.found_terms_studio
        BookAction.SHARE_CARD -> R.string.found_terms_share
        BookAction.CHARACTER_GUIDE -> R.string.found_terms_guide
        BookAction.KEEP_ON_PHONE -> R.string.found_terms_keep
        else -> null
    }

    /** Matched by [title]'s words; its own [terms], and the English title and terms, answer too. */
    private fun entry(
        texts: FoundTexts,
        row: FoundRow,
        @StringRes title: Int,
        @StringRes terms: Int? = null,
        context: List<String> = emptyList(),
    ): ActionEntry<FoundRow> {
        val words = listOfNotNull(terms?.let(texts::text), texts.english(title), terms?.let(texts::english))
        return ActionEntry(row, texts.text(title), words, context)
    }

    private const val MIN_TITLE_WORD = 3
}
