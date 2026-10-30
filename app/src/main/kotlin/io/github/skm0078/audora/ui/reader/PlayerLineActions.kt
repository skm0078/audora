// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/PlayerLineActions.kt
// Purpose: The player's buttons for the line being read (ADR 2026-10-30-player-line-actions): Bookmark, Highlight and
//          Share, each with its name under its icon, so none hides behind a long press seen once.

package io.github.skm0078.audora.ui.reader

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.kids.LocalKidsMode
import io.github.skm0078.audora.ui.theme.Dimens

/**
 * For [ReaderState.sentenceHere]: Bookmark (the one-tap bookmark, CF-2), Highlight (on and off, plan item 30) and Share
 * (the reader's Share button, ADR 2026-10-29-share-credit-and-link). Bookmark and Highlight wait for the page's
 * sentences; Share then offers the book's own card. Shown once the book is open, and never in kids mode (plan item 71).
 */
@Composable
internal fun PlayerLineActions(
    state: ReaderState,
    notes: NotesState,
    actions: ReaderActions,
    notesActions: BookNotesActions,
    modifier: Modifier = Modifier,
) {
    if (LocalKidsMode.current || state.document !is DocumentState.Open) return
    val here = state.sentenceHere
    val marked = here != null && notes.isBookmarked(here)
    val highlighted = here != null && notes.highlightsOn(here).isNotEmpty()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        LineAction(
            icon = if (marked) R.drawable.ic_bookmark else R.drawable.ic_bookmark_border,
            label = stringResource(if (marked) R.string.player_line_bookmarked else R.string.player_line_bookmark),
            description = stringResource(if (marked) R.string.player_bookmark_edit else R.string.player_bookmark_add),
            enabled = here != null,
            onTap = actions::onQuickBookmark,
        )
        LineAction(
            icon = R.drawable.ic_highlight,
            label = stringResource(
                if (highlighted) R.string.player_line_highlighted else R.string.player_line_highlight,
            ),
            description = stringResource(
                if (highlighted) R.string.sentence_menu_unhighlight else R.string.sentence_menu_highlight,
            ),
            enabled = here != null,
            onTap = { here?.let(notesActions::toggleHighlight) },
        )
        LineAction(
            icon = R.drawable.ic_share,
            label = stringResource(R.string.reader_share),
            description = stringResource(R.string.reader_share),
            onTap = { here?.let(notesActions::openShare) ?: actions.onShareBook() },
        )
    }
}

/** An icon with its name under it, one tap target as wide as its share of the row; TalkBack hears [description]. */
@Composable
private fun RowScope.LineAction(
    @DrawableRes icon: Int,
    label: String,
    description: String,
    onTap: () -> Unit,
    enabled: Boolean = true,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
        modifier = Modifier
            .weight(1f)
            .heightIn(min = MIN_TARGET)
            .clickable(enabled = enabled, role = Role.Button, onClick = onTap)
            // One node with the full name ("Bookmark this sentence"); the short label is for the eye.
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                if (enabled) {
                    onClick {
                        onTap()
                        true
                    }
                } else {
                    disabled()
                }
            }
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .padding(vertical = Dimens.SpaceXs),
    ) {
        Icon(painterResource(icon), contentDescription = null)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = LABEL_LINES,
        )
    }
}

private val MIN_TARGET = 48.dp
private const val LABEL_LINES = 2
private const val DISABLED_ALPHA = 0.38f
