// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReaderTopBar.kt
// Purpose: The reader's top bar: back, the book's title, and the book's buttons (Share and the named Page or Text
//          switch among them) once it is open.

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
                ShareButton(state, actions, notesActions)
                ViewToggle(state.view, actions)
                moreMenu()
            }
        },
    )
}

/**
 * Share, always in sight (ADR 2026-10-29-share-credit-and-link): the Share choices for the sentence being read on the
 * shown page, else its first; before the page has words, the book's own card.
 */
@Composable
private fun ShareButton(state: ReaderState, actions: ReaderActions, notes: BookNotesActions) {
    IconButton(onClick = { state.sentenceHere?.let(notes::openShare) ?: actions.onShareBook() }) {
        Icon(painterResource(R.drawable.ic_share), stringResource(R.string.reader_share))
    }
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

/**
 * Switches between the drawn page and its sentences as text, named in words (ADR 2026-10-30-player-line-actions): the
 * two icons looked alike, so the button says where it goes ("Text" on the page, "Page" on the text) beside its icon.
 */
@Composable
private fun ViewToggle(view: ReaderView, actions: ReaderActions) {
    val toText = view == ReaderView.PAGE
    val description = stringResource(if (toText) R.string.reader_show_text else R.string.reader_show_page)
    TextButton(
        onClick = { actions.onViewChanged(if (toText) ReaderView.TEXT else ReaderView.PAGE) },
        contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Icon(
            painterResource(if (toText) R.drawable.ic_view_text else R.drawable.ic_view_page),
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(
            stringResource(if (toText) R.string.reader_view_text else R.string.reader_view_page),
            maxLines = 1,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}
