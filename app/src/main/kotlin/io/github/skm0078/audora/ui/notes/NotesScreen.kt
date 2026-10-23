// File: app/src/main/kotlin/io/github/skm0078/audora/ui/notes/NotesScreen.kt
// Purpose: Notes tab (ADR-036): every book's bookmarks, highlights and voice notes (plan items 29, 30, 32) in one
//          list, filtered by book when there are several; a tap opens the place.

package io.github.skm0078.audora.ui.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.resume.ResumeAnchor
import io.github.skm0078.audora.ui.library.titleOrUntitled
import io.github.skm0078.audora.ui.reader.clock
import io.github.skm0078.audora.ui.theme.Dimens

@Composable
fun NotesScreen(groups: List<BookNotes>, onOpen: (ResumeAnchor) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        Text(
            stringResource(R.string.tab_notes),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceL)
                .semantics { heading() },
        )
        // Saved as the book id, so the filter survives rotation; a book that lost its notes falls back to all.
        var bookId by rememberSaveable { mutableStateOf<String?>(null) }
        if (groups.size > 1) BookFilter(groups, bookId) { bookId = it }
        if (groups.isEmpty()) EmptyNotes() else NotesList(notesOfBook(groups, bookId), onOpen)
    }
}

/** [groups] with only the book [bookId] (by id value); all of them when it is null or no longer has notes. */
internal fun notesOfBook(groups: List<BookNotes>, bookId: String?): List<BookNotes> =
    groups.filter { it.book.id.value == bookId }.ifEmpty { groups }

/** "All books" and one chip per book with its count of notes; scrolls sideways when the titles do not fit. */
@Composable
private fun BookFilter(groups: List<BookNotes>, selected: String?, onSelect: (String?) -> Unit) {
    val shown = selected?.takeIf { id -> groups.any { it.book.id.value == id } }
    LazyRow(
        contentPadding = PaddingValues(horizontal = Dimens.ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
    ) {
        item(key = ALL_BOOKS_KEY) {
            FilterChip(
                selected = shown == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.notes_filter_all)) },
            )
        }
        items(groups, key = { it.book.id.value }) { group ->
            val title = titleOrUntitled(group.book.title)
            FilterChip(
                selected = shown == group.book.id.value,
                onClick = { onSelect(group.book.id.value) },
                label = {
                    Text(
                        stringResource(R.string.notes_filter_book, title, group.entries.size),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = FILTER_CHIP_MAX_WIDTH),
                    )
                },
            )
        }
    }
}

@Composable
private fun NotesList(groups: List<BookNotes>, onOpen: (ResumeAnchor) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = Dimens.SpaceXl)) {
        groups.forEach { group ->
            item(key = BOOK_KEY_PREFIX + group.book.id.value) {
                Text(
                    titleOrUntitled(group.book.title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceS)
                        .semantics { heading() },
                )
            }
            items(group.entries, key = { it.key }) { entry -> NoteRow(entry, onOpen) }
        }
    }
}

@Composable
private fun NoteRow(entry: NoteEntry, onOpen: (ResumeAnchor) -> Unit) {
    val page = stringResource(R.string.notes_page, entry.anchor.page + 1)
    val (icon, kind) = when (entry) {
        is NoteEntry.Bookmark -> R.drawable.ic_bookmark to R.string.notes_kind_bookmark
        is NoteEntry.Highlight -> R.drawable.ic_format_quote to R.string.notes_kind_highlight
        is NoteEntry.VoiceNote -> R.drawable.ic_mic to R.string.notes_kind_voice
    }
    val lines = when (entry) {
        is NoteEntry.Bookmark -> listOf(entry.note)
        is NoteEntry.Highlight -> listOf(entry.quote, entry.note)
        is NoteEntry.VoiceNote -> listOf(stringResource(R.string.notes_voice_length, clock(entry.durationMs)))
    }.filter { it.isNotBlank() }
    ListItem(
        overlineContent = { Text(stringResource(kind)) },
        headlineContent = { Text(page) },
        supportingContent = if (lines.isEmpty()) {
            null
        } else {
            { Text(lines.joinToString("\n"), maxLines = SUPPORTING_MAX_LINES, overflow = TextOverflow.Ellipsis) }
        },
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(
            onClickLabel = stringResource(R.string.notes_open),
            role = Role.Button,
        ) { onOpen(entry.anchor) },
    )
}

@Composable
private fun EmptyNotes() {
    Box(Modifier.fillMaxSize().padding(Dimens.SpaceXl), contentAlignment = Alignment.Center) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painterResource(R.drawable.ic_bookmark),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.notes_empty_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.notes_empty_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Long titles are cut so a few chips still fit on a phone's width. */
private val FILTER_CHIP_MAX_WIDTH = 200.dp

/** A long quote and its note are cut here; the reader shows them whole. */
private const val SUPPORTING_MAX_LINES = 4

private const val ALL_BOOKS_KEY = "all-books"

/** Book ids are UUIDs and note keys start with their kind, so these keys never clash. */
private const val BOOK_KEY_PREFIX = "book-"
