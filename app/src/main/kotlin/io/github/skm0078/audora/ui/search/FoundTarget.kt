// File: app/src/main/kotlin/io/github/skm0078/audora/ui/search/FoundTarget.kt
// Purpose: What a row of the library search's "Actions and settings" opens or starts, and how it is shown
//          (ADR 2026-10-28-action-search).

package io.github.skm0078.audora.ui.search

import androidx.annotation.DrawableRes
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.ui.AppDestination
import io.github.skm0078.audora.ui.library.BookAction
import io.github.skm0078.audora.ui.library.LibraryAction
import io.github.skm0078.audora.ui.library.LibraryActions
import io.github.skm0078.audora.ui.settings.SettingKey

/** Each one goes through the same [LibraryActions] call as its button elsewhere, so it does exactly what that does. */
sealed interface FoundTarget {
    /** A row on You, opened scrolled to and tinted. */
    data class Setting(val key: SettingKey) : FoundTarget

    /** A place or a screen: Voices, Storage, Jobs, Made, Notes. */
    data class Screen(val destination: AppDestination) : FoundTarget

    /** One of the library's ⋮ menu tools. */
    data class Library(val action: LibraryAction) : FoundTarget

    /** One of a book's menu choices, for [book]. */
    data class ForBook(val book: Book, val pick: BookPick) : FoundTarget
}

/** A book menu choice: the big buttons and Save as audiobook, then every [BookAction]. */
sealed interface BookPick {
    data object Play : BookPick

    data object UpNext : BookPick

    data object Audiobook : BookPick

    data class Action(val action: BookAction) : BookPick
}

/** [title] is the row's line ("Save Gora as an audiobook"); [where] says where it lives ("You › Reading and look"). */
class FoundRow(val target: FoundTarget, val title: String, val where: String, @DrawableRes val icon: Int)

/** Starts [target] as its own button would. */
internal fun LibraryActions.open(target: FoundTarget) {
    when (target) {
        is FoundTarget.Setting -> onOpenSetting(target.key)

        is FoundTarget.Screen -> onGoTo(target.destination)

        is FoundTarget.Library -> onLibraryAction(target.action)

        is FoundTarget.ForBook -> when (val pick = target.pick) {
            BookPick.Play -> onContinue(target.book)
            BookPick.UpNext -> onAddToUpNext(target.book)
            BookPick.Audiobook -> onExportAudiobook(target.book)
            is BookPick.Action -> onBookAction(target.book, pick.action)
        }
    }
}
