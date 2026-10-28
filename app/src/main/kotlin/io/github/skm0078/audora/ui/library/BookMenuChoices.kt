// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/BookMenuChoices.kt
// Purpose: What a book's menu offers and in which part of it, worked out from the book alone so tests can check that
//          every action keeps a home, with each choice's icon and short name (ADR 2026-10-28-book-menu-sort-sheet).

package io.github.skm0078.audora.ui.library

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.BookAvailability

/** One of the big buttons under the book's cover. */
sealed interface QuickChoice {
    data object Play : QuickChoice

    data object UpNext : QuickChoice

    /** A [BookAction] placed [MenuPlace.QUICK]. */
    data class Action(val action: BookAction) : QuickChoice

    /** Shown pressed once the book is [finished]; pressing it then marks the book not started again. */
    data class Finished(val finished: Boolean) : QuickChoice {
        val action: BookAction get() = if (finished) BookAction.MARK_NOT_STARTED else BookAction.MARK_FINISHED
    }
}

/** One tile under Save as. */
sealed interface SaveChoice {
    data object Audiobook : SaveChoice

    data class Action(val action: BookAction) : SaveChoice
}

/**
 * The menu's parts, top to bottom: [quick] buttons, then Details, the [saveAs] tiles, the [rows] and Remove, which
 * are always there. A book whose file cannot be opened offers only Details and Remove: nothing else can work on it.
 */
@Immutable
data class BookMenuChoices(val quick: List<QuickChoice>, val saveAs: List<SaveChoice>, val rows: List<BookAction>) {
    companion object {
        private val NOTHING_OPENS = BookMenuChoices(emptyList(), emptyList(), emptyList())

        fun of(item: LibraryItem): BookMenuChoices {
            if (item.availability != BookAvailability.AVAILABLE) return NOTHING_OPENS
            val offered = BookAction.entries.filter { it.appliesTo(item) }
            fun placed(place: MenuPlace) = offered.filter { it.place == place }
            val finished = item.progress?.isFinished == true
            val quick = buildList {
                add(QuickChoice.Play)
                add(QuickChoice.UpNext)
                placed(MenuPlace.QUICK).mapTo(this, QuickChoice::Action)
                if (finished || BookAction.MARK_FINISHED in offered) add(QuickChoice.Finished(finished))
            }
            val saveAs = listOf(SaveChoice.Audiobook) + placed(MenuPlace.SAVE_AS).map(SaveChoice::Action)
            return BookMenuChoices(quick, saveAs, placed(MenuPlace.ROW))
        }
    }
}

/** What a book's menu can ask for; the tile or row that owns the menu builds one per book. */
@Immutable
internal class BookMenuActions(
    val onPlay: () -> Unit,
    val onAddToUpNext: () -> Unit,
    val onExport: () -> Unit,
    val onDetails: () -> Unit,
    val onRemove: () -> Unit,
    val onAction: (BookAction) -> Unit,
)

internal fun QuickChoice.perform(actions: BookMenuActions) = when (this) {
    QuickChoice.Play -> actions.onPlay()
    QuickChoice.UpNext -> actions.onAddToUpNext()
    is QuickChoice.Action -> actions.onAction(action)
    is QuickChoice.Finished -> actions.onAction(action)
}

internal fun SaveChoice.perform(actions: BookMenuActions) = when (this) {
    SaveChoice.Audiobook -> actions.onExport()
    is SaveChoice.Action -> actions.onAction(action)
}

/**
 * On a wide screen a tap picks a book and the pane shows its menu; the ⋮ and a long press pick it too, so no sheet
 * covers the books. [selected] marks the picked book's tile or row.
 */
@Immutable
internal class PaneChoice(val selected: Boolean, val onPick: () -> Unit)

@DrawableRes
internal fun actionIcon(action: BookAction): Int = when (action) {
    BookAction.STUDIO -> R.drawable.ic_studio
    BookAction.ACCESSIBLE_COPY -> R.drawable.ic_article
    BookAction.TALKING_BOOK -> R.drawable.ic_talking_book
    BookAction.EXPORT_NOTES -> R.drawable.ic_bookmark_border
    BookAction.SHARE_CARD -> R.drawable.ic_share
    BookAction.CHARACTER_GUIDE -> R.drawable.ic_person
    BookAction.MARK_FINISHED, BookAction.MARK_NOT_STARTED -> R.drawable.ic_check
    BookAction.KEEP_ON_PHONE -> R.drawable.ic_download
}

/** A tile's short name and the line under it; the row label stays the long one the info text uses. */
@StringRes
internal fun saveLabel(choice: SaveChoice): Int = when (choice) {
    SaveChoice.Audiobook -> R.string.menu_save_audiobook

    is SaveChoice.Action -> when (choice.action) {
        BookAction.TALKING_BOOK -> R.string.menu_save_talking_book
        BookAction.ACCESSIBLE_COPY -> R.string.menu_save_accessible
        BookAction.EXPORT_NOTES -> R.string.menu_save_notes
        else -> choice.action.label
    }
}

@StringRes
internal fun saveLine(choice: SaveChoice): Int? = when (choice) {
    SaveChoice.Audiobook -> R.string.menu_save_audiobook_line

    is SaveChoice.Action -> when (choice.action) {
        BookAction.TALKING_BOOK -> R.string.menu_save_talking_book_line
        BookAction.ACCESSIBLE_COPY -> R.string.menu_save_accessible_line
        BookAction.EXPORT_NOTES -> R.string.menu_save_notes_line
        else -> null
    }
}
