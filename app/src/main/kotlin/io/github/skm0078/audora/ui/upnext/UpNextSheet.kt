// File: app/src/main/kotlin/io/github/skm0078/audora/ui/upnext/UpNextSheet.kt
// Purpose: The Up next sheet (plan item 25): the queue with reorder and remove, add from the library, two switches.

package io.github.skm0078.audora.ui.upnext

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.BookTitles
import io.github.skm0078.audora.ui.library.BookCover
import io.github.skm0078.audora.ui.library.COVER_RATIO
import io.github.skm0078.audora.ui.library.CoverSource
import io.github.skm0078.audora.ui.library.LibraryListState
import io.github.skm0078.audora.ui.library.titleOrUntitled
import io.github.skm0078.audora.ui.reader.durationText
import io.github.skm0078.audora.ui.theme.Dimens

/**
 * @param library every book in the library, to name queued books and offer the rest; a queued book it no longer
 *   holds shows without a cover.
 * @param playing the book being read: never offered, and the queue starts after it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpNextSheet(
    state: UpNextState,
    library: List<Book>,
    playing: BookId?,
    queue: UpNextPort,
    onDismiss: () -> Unit,
    covers: CoverSource = CoverSource.None,
) {
    val byId = library.associateBy { it.id }
    val queued = state.entries.map { it.bookId }.toSet()
    val addable = library.filter { it.id != playing && it.id !in queued }
    var adding by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.SpaceXl)
                .padding(bottom = Dimens.SpaceXl),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
        ) {
            Header(canClear = state.entries.isNotEmpty(), onClear = queue::clear)
            if (state.entries.isEmpty()) {
                Text(
                    stringResource(R.string.up_next_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.entries.forEachIndexed { index, entry ->
                QueuedRow(
                    entry = entry,
                    book = byId[entry.bookId],
                    covers = covers,
                    onMoveUp = if (index > 0) ({ queue.move(index, index - 1) }) else null,
                    onRemove = { queue.remove(entry.entryId) },
                )
            }
            if (addable.isNotEmpty()) {
                TextButton(onClick = { adding = !adding }) {
                    Text(stringResource(if (adding) R.string.up_next_done_adding else R.string.up_next_add))
                }
            }
            if (adding) addable.forEach { book -> AddableRow(book, covers) { queue.add(book.id) } }
            HorizontalDivider()
            SwitchRow(
                stringResource(R.string.up_next_keep_going),
                stringResource(R.string.up_next_keep_going_body),
                state.keepGoingToNextChapter,
                queue::setKeepGoingToNextChapter,
            )
            SwitchRow(
                stringResource(R.string.up_next_play_queue),
                stringResource(R.string.up_next_play_queue_body),
                state.thenPlayQueue,
                queue::setThenPlayQueue,
            )
        }
    }
}

@Composable
private fun Header(canClear: Boolean, onClear: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.up_next_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (canClear) TextButton(onClick = onClear) { Text(stringResource(R.string.up_next_clear)) }
    }
}

@Composable
private fun QueuedRow(
    entry: UpNextEntry,
    book: Book?,
    covers: CoverSource,
    onMoveUp: (() -> Unit)?,
    onRemove: () -> Unit,
) {
    val title = titleOrUntitled(entry.title.ifBlank { book?.title ?: BookTitles.UNTITLED })
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.TouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
    ) {
        book?.let { SmallCover(it, covers) }
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis)
            entryDetail(entry)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        onMoveUp?.let { move ->
            IconButton(onClick = move) {
                Icon(painterResource(R.drawable.ic_arrow_upward), stringResource(R.string.up_next_move_up, title))
            }
        }
        IconButton(onClick = onRemove) {
            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.up_next_remove, title))
        }
    }
}

/** "From Chapter 3 · About 2 h 10 min", whichever parts are known. */
@Composable
private fun entryDetail(entry: UpNextEntry): String? {
    val from = entry.startLabel?.let { stringResource(R.string.up_next_from, it) }
        ?: entry.startPage?.let { stringResource(R.string.up_next_from_page, it + 1) }
    val time = entry.timeLeftMillis?.let { stringResource(R.string.up_next_time_left, durationText(it)) }
    return listOfNotNull(from, time).takeIf { it.isNotEmpty() }?.joinToString(DETAIL_SEPARATOR)
}

@Composable
private fun AddableRow(book: Book, covers: CoverSource, onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TouchTarget)
            .clickable(onClickLabel = stringResource(R.string.up_next_add_one), onClick = onAdd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
    ) {
        SmallCover(book, covers)
        Text(titleOrUntitled(book.title), Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Icon(painterResource(R.drawable.ic_add), contentDescription = null)
    }
}

@Composable
private fun SmallCover(book: Book, covers: CoverSource) {
    BookCover(book, covers, Modifier.size(Dimens.CoverSmall, Dimens.CoverSmall * COVER_RATIO))
}

/** The whole row toggles; the switch only shows the state, so it is not a second target for TalkBack. */
@Composable
private fun SwitchRow(title: String, body: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = on, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = Dimens.SpaceS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = on, onCheckedChange = null)
    }
}

private const val DETAIL_SEPARATOR = " · "

/** Every book in the loaded library, in library order; empty while it loads. */
fun libraryBooks(list: LibraryListState): List<Book> = (list as? LibraryListState.Loaded)?.all.orEmpty().map { it.book }
