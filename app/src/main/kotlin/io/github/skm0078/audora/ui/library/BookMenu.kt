// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/BookMenu.kt
// Purpose: A library book's actions in a sheet, one line each, with what each does behind an info button.

package io.github.skm0078.audora.ui.library

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.BookAvailability
import io.github.skm0078.audora.ui.kids.LocalKidsMode
import io.github.skm0078.audora.ui.theme.AppSheet
import io.github.skm0078.audora.ui.theme.Dimens
import io.github.skm0078.audora.ui.theme.HelpButton

/**
 * A book's actions, in a sheet: Details, then Up next, Save as audiobook and the [BookAction]s while the file can be
 * opened, and Remove. Each is one line; its info button says what it does, where the result goes and what it costs,
 * which a menu of bare labels could not. None in kids mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookMenu(
    item: LibraryItem,
    onRemove: () -> Unit,
    onExport: () -> Unit,
    onAddToUpNext: () -> Unit,
    onDetails: () -> Unit = {},
    onAction: (BookAction) -> Unit = {},
) {
    if (LocalKidsMode.current) return
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        // Named per book: a list of rows that all say "More options" cannot be told apart by ear.
        Icon(
            painterResource(R.drawable.ic_more_vert),
            stringResource(R.string.library_book_menu_named, displayTitle(item.book)),
        )
    }
    if (!open) return
    AppSheet(onDismissRequest = { open = false }, skipPartiallyExpanded = true) {
        val choose = { action: () -> Unit ->
            open = false
            action()
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = Dimens.SpaceL)) {
            Text(
                stringResource(R.string.book_actions_title, displayTitle(item.book)),
                style = MaterialTheme.typography.titleMedium,
                maxLines = TITLE_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceS)
                    .semantics { heading() },
            )
            ActionRow(R.string.book_details_title, R.string.menu_hint_details) { choose(onDetails) }
            // Only a book Audora can still open can be queued or read into audio files (ADR-034).
            if (item.availability == BookAvailability.AVAILABLE) {
                ActionRow(R.string.library_add_to_up_next, R.string.menu_hint_up_next) { choose(onAddToUpNext) }
                ActionRow(R.string.library_export_audiobook, R.string.menu_hint_audiobook) { choose(onExport) }
                BookAction.entries.filter { it.appliesTo(item) }.forEach { action ->
                    ActionRow(action.label, action.hint) { choose { onAction(action) } }
                }
            }
            ActionRow(R.string.library_remove, R.string.menu_hint_remove) { choose(onRemove) }
        }
    }
}

@Composable
private fun ActionRow(@StringRes label: Int, @StringRes hint: Int, onClick: () -> Unit) {
    // One line per action; what it does, where it saves and its cost wait behind the info button (ADR-062).
    val title = stringResource(label)
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = { HelpButton(title, stringResource(hint)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}
