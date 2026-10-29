// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/OwnSheets.kt
// Purpose: The library screen's own sheets, Make it yours, the collections and Add a book, opened from the ⋮ menu,
//          search, the Add book button and the side panel alike (ADR 2026-10-28-make-it-yours, ADR
//          2026-10-28-phone-drawer, ADR 2026-10-29-add-a-book).

package io.github.skm0078.audora.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable

/** Which own sheet is open, and [actions]: the screen's actions with these opening here instead of going on. */
@Stable
internal class OwnSheets(private val open: MutableState<LibraryAction?>, actions: LibraryActions) {
    val shown: LibraryAction? get() = open.value

    val actions: LibraryActions = object : LibraryActions by actions {
        override fun onLibraryAction(action: LibraryAction) {
            if (action in OWN) open.value = action else actions.onLibraryAction(action)
        }
    }

    fun close() {
        open.value = null
    }

    private companion object {
        val OWN = setOf(LibraryAction.MAKE_IT_YOURS, LibraryAction.EDIT_COLLECTIONS, LibraryAction.ADD_BOOK)
    }
}

/**
 * Saveable, so a sheet is still open after a rotation. [requested] is an action asked for from elsewhere (the side
 * panel); it runs here once, as the ⋮ menu's would, and [onRequestTaken] clears it.
 */
@Composable
internal fun rememberOwnSheets(
    actions: LibraryActions,
    requested: LibraryAction?,
    onRequestTaken: () -> Unit,
): OwnSheets {
    val open = rememberSaveable { mutableStateOf<LibraryAction?>(null) }
    val sheets = remember(actions) { OwnSheets(open, actions) }
    LaunchedEffect(requested) {
        requested?.let { action ->
            onRequestTaken()
            sheets.actions.onLibraryAction(action)
        }
    }
    return sheets
}

/**
 * Shows the open sheet; the collections wait for the list, Make it yours for the listener's home. Add a book's file
 * row runs [onFile], the system picker; its tools go on as the screen's own actions.
 */
@Composable
internal fun OwnSheetHost(
    sheets: OwnSheets,
    listState: LibraryListState,
    home: LibraryHome,
    view: LibraryLayoutState,
    onFile: () -> Unit,
) {
    when (sheets.shown) {
        LibraryAction.MAKE_IT_YOURS -> home.layout?.let { MakeItYoursSheet(it, home.onLayout, view, sheets::close) }

        LibraryAction.EDIT_COLLECTIONS -> (listState as? LibraryListState.Loaded)?.let { list ->
            CollectionsRoute(list, remember(list.members) { collectionCounts(list.members) }, sheets::close)
        }

        LibraryAction.ADD_BOOK -> AddBookSheet(onFile, sheets.actions::onLibraryAction, sheets::close)

        else -> Unit
    }
}
