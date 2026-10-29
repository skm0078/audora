// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/AddBookSheet.kt
// Purpose: The Add a book sheet (ADR 2026-10-29-add-a-book): every way a book comes in, from a file to a free
//          catalogue, each with one line on what it reads, opened by the library's Add book button.

package io.github.skm0078.audora.ui.library

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.theme.LinkOption
import io.github.skm0078.audora.ui.theme.OptionDivider
import io.github.skm0078.audora.ui.theme.OptionGroup
import io.github.skm0078.audora.ui.theme.OptionSheet

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
        override val icon get() = iconOf(action)
    }

    companion object {
        /** Choose a file first, the way most books come in; then the tools in their enum order. */
        val all: List<AddBookSource> = listOf(File) +
            LibraryAction.entries.filter { it.place == LibraryActionPlace.ADD_SHEET }.map(::Tool)
    }
}

/**
 * Each row closes the sheet before it runs, so the camera, a picker or the catalogues' own sheet never opens on top
 * of this one. What a tool does in full stays behind its info button.
 */
@Composable
internal fun AddBookSheet(onFile: () -> Unit, onTool: (LibraryAction) -> Unit, onDismiss: () -> Unit) {
    OptionSheet(stringResource(R.string.add_book_title), onDismiss) {
        OptionGroup(title = null, modifier = Modifier.testTag(ADD_BOOK_SHEET_TAG)) {
            AddBookSource.all.forEachIndexed { index, source ->
                if (index > 0) OptionDivider()
                LinkOption(
                    stringResource(source.label),
                    onClick = {
                        onDismiss()
                        when (source) {
                            AddBookSource.File -> onFile()
                            is AddBookSource.Tool -> onTool(source.action)
                        }
                    },
                    subtitle = stringResource(source.line),
                    help = (source as? AddBookSource.Tool)?.let { stringResource(it.action.hint) },
                    leadingIcon = source.icon,
                )
            }
        }
    }
}

@StringRes
private fun lineOf(action: LibraryAction): Int = when (action) {
    LibraryAction.POINT_AND_READ -> R.string.add_book_line_photo
    LibraryAction.READ_PICTURES -> R.string.add_book_line_pictures
    LibraryAction.READ_CLIPBOARD -> R.string.add_book_line_clipboard
    LibraryAction.READ_WEB_PAGE -> R.string.add_book_line_web
    LibraryAction.BOOK_CATALOGUES -> R.string.add_book_line_catalogues
    LibraryAction.WATCHED_FOLDERS -> R.string.add_book_line_watched
    else -> action.hint
}

@DrawableRes
private fun iconOf(action: LibraryAction): Int = when (action) {
    LibraryAction.POINT_AND_READ -> R.drawable.ic_camera
    LibraryAction.READ_PICTURES -> R.drawable.ic_photos
    LibraryAction.READ_CLIPBOARD -> R.drawable.ic_paste
    LibraryAction.READ_WEB_PAGE -> R.drawable.ic_globe
    LibraryAction.BOOK_CATALOGUES -> R.drawable.ic_library
    LibraryAction.WATCHED_FOLDERS -> R.drawable.ic_folder
    else -> R.drawable.ic_add
}

/** For UI tests: the sheet's list of ways in. */
internal const val ADD_BOOK_SHEET_TAG = "add_book_sheet"
