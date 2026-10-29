// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/AddBookSource.kt
// Purpose: The ways a book comes in that the Add a book sheet lists (ADR 2026-10-29-add-a-book): a file, or a
//          library tool placed in the sheet, each with its one line and icon.

package io.github.skm0078.audora.ui.library

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.skm0078.audora.R

/** One way in: a file from the picker, or a library tool placed [LibraryActionPlace.ADD_SHEET]. */
internal sealed interface AddBookSource {
    @get:StringRes val label: Int

    @get:StringRes val line: Int

    @get:DrawableRes val icon: Int

    data object File : AddBookSource {
        override val label = R.string.add_book_file
        override val line = R.string.add_book_line_file
        override val icon = R.drawable.ic_file
    }

    data class Tool(val action: LibraryAction) : AddBookSource {
        override val label get() = action.label
        override val line get() = lineOf(action)
        override val icon get() = action.icon
    }

    companion object {
        /** Choose a file first, the way most books come in; then the tools in their enum order. */
        val all: List<AddBookSource> = listOf(File) +
            LibraryAction.entries.filter { it.place == LibraryActionPlace.ADD_SHEET }.map(::Tool)
    }
}

@StringRes
private fun lineOf(action: LibraryAction): Int = when (action) {
    LibraryAction.POINT_AND_READ -> R.string.add_book_line_photo
    LibraryAction.READ_PICTURES -> R.string.add_book_line_pictures
    LibraryAction.READ_CLIPBOARD -> R.string.add_book_line_clipboard
    LibraryAction.READ_WEB_PAGE -> R.string.add_book_line_web
    LibraryAction.BOOK_CATALOGUES -> R.string.add_book_line_catalogues
    else -> action.hint
}
