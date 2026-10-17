// File: app/src/main/kotlin/io/github/skm0078/audora/ui/notes/NotesScreen.kt
// Purpose: Notes tab (ADR-036): every book's bookmarks and notes (plan item 29) in one list; a tap opens the place.

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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.notes.Bookmark
import io.github.skm0078.audora.ui.library.titleOrUntitled
import io.github.skm0078.audora.ui.theme.Dimens

@Composable
fun NotesScreen(groups: List<BookNotes>, onOpen: (Bookmark) -> Unit, modifier: Modifier = Modifier) {
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
        if (groups.isEmpty()) EmptyNotes() else NotesList(groups, onOpen)
    }
}

@Composable
private fun NotesList(groups: List<BookNotes>, onOpen: (Bookmark) -> Unit) {
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
            items(group.bookmarks, key = { it.id }) { bookmark ->
                ListItem(
                    headlineContent = { Text(stringResource(R.string.notes_page, bookmark.anchor.page + 1)) },
                    supportingContent = if (bookmark.note.isNotBlank()) {
                        { Text(bookmark.note) }
                    } else {
                        null
                    },
                    leadingContent = { Icon(painterResource(R.drawable.ic_bookmark), contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onOpen(bookmark) },
                )
            }
        }
    }
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

/** Book ids are UUIDs and bookmark ids are numbers, so these keys never clash. */
private const val BOOK_KEY_PREFIX = "book-"
