// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/AddBookSheet.kt
// Purpose: The Add a book sheet (ADR 2026-10-29-add-a-book): every way a book comes in, from a file to a free
//          catalogue, each with one line on what it reads, opened by the library's Add book button.

package io.github.skm0078.audora.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.theme.LinkOption
import io.github.skm0078.audora.ui.theme.OptionDivider
import io.github.skm0078.audora.ui.theme.OptionGroup
import io.github.skm0078.audora.ui.theme.OptionSheet

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

/** For UI tests: the sheet's list of ways in. */
internal const val ADD_BOOK_SHEET_TAG = "add_book_sheet"
