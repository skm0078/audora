// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/LibraryBookPane.kt
// Purpose: The book pane beside the books on a wide screen: the picked book's menu, always in view, with Save as open
//          (ADR 2026-10-28-tablet-panes).

package io.github.skm0078.audora.ui.library

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.ui.theme.Dimens

/**
 * The picked book's menu in its own column, scrolled on its own. Each choice acts at once and the pane stays, so the
 * listener can export, then add to Up next, without reopening anything. [onOpen]: the cover and title open the book.
 */
@Composable
internal fun BookPane(
    item: LibraryItem,
    covers: CoverSource,
    actions: BookMenuActions,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxHeight().testTag(BOOK_PANE_TAG)) {
        VerticalDivider()
        Surface(Modifier.width(BOOK_PANE_WIDTH).fillMaxHeight(), color = MaterialTheme.colorScheme.surface) {
            // Keyed by book: a newly picked book starts at the top of the pane, not where the last one was scrolled.
            key(item.book.id.value) {
                BookMenuContent(
                    item,
                    covers,
                    actions,
                    Modifier
                        .windowInsetsPadding(PANE_INSETS)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = Dimens.SpaceL),
                    saveAsOpen = true,
                    onOpen = onOpen,
                ) { choice -> choice() }
            }
        }
    }
}

/** The book the pane shows: the one picked, else the one being read, else the first; null when nothing is shown. */
internal fun paneBook(list: LibraryListState.Loaded, pickedId: String?): LibraryItem? =
    list.items.firstOrNull { it.book.id.value == pickedId }
        ?: list.continueReading?.takeIf { latest -> list.items.any { it.book.id == latest.book.id } }
        ?: list.items.firstOrNull()

/** Whether the library is wide enough for the pane and still a readable grid beside it. */
internal fun showsBookPane(libraryWidth: Dp, kids: Boolean): Boolean = !kids && libraryWidth >= BOOK_PANE_MIN_LIBRARY

/** What the pane's choices do for [book]; Details opens the book's details sheet through [onDetails]. */
internal fun paneActions(book: Book, actions: LibraryActions, onDetails: () -> Unit) = BookMenuActions(
    onPlay = { actions.onContinue(book) },
    onAddToUpNext = { actions.onAddToUpNext(book) },
    onExport = { actions.onExportAudiobook(book) },
    onDetails = onDetails,
    onRemove = { actions.onRequestRemoval(book) },
    onAction = { action -> actions.onBookAction(book, action) },
)

/** The mockup's 360 dp: room for the four big buttons and two Save as tiles a row at 100 % text. */
internal val BOOK_PANE_WIDTH = 360.dp

/**
 * The pane plus at least 520 dp of books (four covers a row), so a 1280 dp tablet beside the 280 dp sidebar shows it
 * and a tablet in portrait, or a phone in landscape, keeps the sheet.
 */
internal val BOOK_PANE_MIN_LIBRARY = 880.dp

internal const val BOOK_PANE_TAG = "book_pane"

/** The status bar above and the system bar at the end; the books beside it keep their own start insets. */
private val PANE_INSETS: WindowInsets
    @Composable get() = WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.End)
