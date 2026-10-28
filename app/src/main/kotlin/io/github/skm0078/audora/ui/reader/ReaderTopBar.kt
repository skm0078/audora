// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReaderTopBar.kt
// Purpose: The reader's top bar: back, the book's title, and the book's buttons once it is open.

package io.github.skm0078.audora.ui.reader

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.github.skm0078.audora.R

/**
 * Title and back; the book's buttons and [moreMenu] once it is open. [panelShown]: the side panel already holds the
 * chapters and bookmarks, so their buttons go.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderTopBar(
    state: ReaderState,
    actions: ReaderActions,
    notes: NotesState,
    notesActions: BookNotesActions,
    panelShown: Boolean = false,
    moreMenu: @Composable () -> Unit,
) {
    TopAppBar(
        title = { ReaderTitle(state.title.orEmpty()) },
        navigationIcon = { BackButton(actions::onBack) },
        actions = {
            if (state.document is DocumentState.Open) {
                if (!panelShown) {
                    BookmarksButton(notes, notesActions)
                    ChaptersButton(state, actions)
                }
                ViewToggle(state.view, actions)
                moreMenu()
            }
        },
    )
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
    }
}

/** The book's title in the top bar, one line; TalkBack's heading, so a swipe by headings lands on it. */
@Composable
private fun ReaderTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.semantics { heading() },
    )
}
