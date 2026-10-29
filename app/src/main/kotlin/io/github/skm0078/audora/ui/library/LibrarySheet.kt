// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/LibrarySheet.kt
// Purpose: The one sheet the library shows at a time over its books: "Make it yours" or Add a book.

package io.github.skm0078.audora.ui.library

import androidx.compose.runtime.Composable

/** Which of the library's own sheets is open; one at a time, so opening one can never stack on the other. */
internal enum class LibrarySheet { MAKE_IT_YOURS, ADD_BOOK }

/** Shows [sheet], and [onClose] when it is dismissed. "Make it yours" needs the home's layout; without one, nothing. */
@Composable
internal fun LibrarySheetFor(
    sheet: LibrarySheet,
    home: LibraryHome,
    view: LibraryLayoutState,
    onFile: () -> Unit,
    onTool: (LibraryAction) -> Unit,
    onClose: () -> Unit,
) {
    when (sheet) {
        LibrarySheet.MAKE_IT_YOURS -> home.layout?.let { MakeItYoursSheet(it, home.onLayout, view, onClose) }
        LibrarySheet.ADD_BOOK -> AddBookSheet(onFile, onTool, onClose)
    }
}
