// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/BookMenu.kt
// Purpose: A library book's menu, one sheet: the book on top, its big buttons, Details, Save as tiles that open in
//          place, its other actions and Remove (ADR 2026-10-28-book-menu-sort-sheet).

package io.github.skm0078.audora.ui.library

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.BookProgress
import io.github.skm0078.audora.ui.kids.LocalKidsMode
import io.github.skm0078.audora.ui.theme.AppSheet
import io.github.skm0078.audora.ui.theme.Dimens
import io.github.skm0078.audora.ui.theme.HelpButton
import kotlin.math.roundToInt

/** The ⋮ that opens the menu; named per book, since rows that all say "More options" cannot be told apart by ear. */
@Composable
internal fun BookMenuButton(item: LibraryItem, onClick: () -> Unit) {
    if (LocalKidsMode.current) return
    IconButton(onClick = onClick) {
        Icon(
            painterResource(R.drawable.ic_more_vert),
            stringResource(R.string.library_book_menu_named, displayTitle(item.book)),
        )
    }
}

/**
 * The book's menu as one sheet: nothing in it opens another sheet over it. Every choice closes the menu first, so a
 * removal dialog or the details sheet is never hidden behind it. None in kids mode.
 */
@Composable
internal fun BookMenuSheet(item: LibraryItem, covers: CoverSource, actions: BookMenuActions, onDismiss: () -> Unit) {
    if (LocalKidsMode.current) return
    val choices = BookMenuChoices.of(item)
    val choose = { action: () -> Unit ->
        onDismiss()
        action()
    }
    AppSheet(onDismissRequest = onDismiss, skipPartiallyExpanded = true) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = Dimens.SpaceXl)) {
            MenuBook(item, covers)
            if (choices.quick.isNotEmpty()) {
                QuickButtons(choices.quick, Modifier.padding(horizontal = Dimens.ScreenPadding)) { quick ->
                    choose { quick.perform(actions) }
                }
            }
            MenuDivider()
            MenuRow(R.drawable.ic_info, stringResource(R.string.book_details_title), trailing = { Chevron() }) {
                choose(actions.onDetails)
            }
            if (choices.saveAs.isNotEmpty()) {
                SaveAsCard(choices.saveAs, Modifier.padding(horizontal = Dimens.ScreenPadding - Dimens.SpaceS)) {
                    choose { it.perform(actions) }
                }
            }
            choices.rows.forEach { action ->
                val label = stringResource(action.label)
                MenuRow(actionIcon(action), label, trailing = { HelpButton(label, stringResource(action.hint)) }) {
                    choose { actions.onAction(action) }
                }
            }
            MenuDivider()
            val remove = stringResource(R.string.library_remove)
            MenuRow(
                R.drawable.ic_delete,
                remove,
                colour = MaterialTheme.colorScheme.error,
                trailing = { HelpButton(remove, stringResource(R.string.menu_hint_remove)) },
            ) { choose(actions.onRemove) }
        }
    }
}

private fun QuickChoice.perform(actions: BookMenuActions) = when (this) {
    QuickChoice.Play -> actions.onPlay()
    QuickChoice.UpNext -> actions.onAddToUpNext()
    is QuickChoice.Action -> actions.onAction(action)
    is QuickChoice.Finished -> actions.onAction(action)
}

private fun SaveChoice.perform(actions: BookMenuActions) = when (this) {
    SaveChoice.Audiobook -> actions.onExport()
    is SaveChoice.Action -> actions.onAction(action)
}

/** The book the menu is for: its cover, title, where reading stands. TalkBack hears "What to do with …" first. */
@Composable
private fun MenuBook(item: LibraryItem, covers: CoverSource) {
    val spoken = stringResource(R.string.book_actions_title, displayTitle(item.book))
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Dimens.ScreenPadding).padding(bottom = Dimens.SpaceL),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceL),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(item.book, covers, Modifier.size(Dimens.CoverSmall, Dimens.CoverSmall * COVER_RATIO))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
            Text(
                displayTitle(item.book),
                style = MaterialTheme.typography.titleLarge,
                maxLines = TITLE_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics {
                    heading()
                    contentDescription = spoken
                },
            )
            val warning = availabilityWarning(item.availability)
            val colours = MaterialTheme.colorScheme
            Text(
                warning ?: whereItStands(item),
                style = MaterialTheme.typography.bodyMedium,
                color = if (warning != null) colours.error else colours.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            item.progress?.let { progress ->
                LinearProgressIndicator(progress = { progress.fraction }, Modifier.width(PROGRESS_WIDTH))
            }
        }
    }
}

/** "Page 3 of 12 · 25% · English", or "New · English" for a book not opened yet. */
@Composable
private fun whereItStands(item: LibraryItem): String {
    val progress: BookProgress? = item.progress
    val reading = if (progress == null) {
        stringResource(R.string.library_status_new)
    } else {
        stringResource(
            R.string.library_progress,
            progress.page + 1,
            progress.pageCount,
            (progress.fraction * PERCENT).roundToInt(),
        )
    }
    return listOfNotNull(reading, item.book.language?.let { languageName(it) }).joinToString(SEPARATOR)
}

@Composable
private fun MenuDivider() {
    HorizontalDivider(Modifier.padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceS))
}

/** One line of the menu: the icon in its tinted square, the label, and what sits at its end. */
@Composable
private fun MenuRow(
    @DrawableRes icon: Int,
    label: String,
    colour: Color = MaterialTheme.colorScheme.onSurface,
    trailing: @Composable () -> Unit = {},
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, color = Color.Transparent, contentColor = colour) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = ROW_HEIGHT)
                .padding(start = Dimens.ScreenPadding, end = Dimens.SpaceXs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceL),
        ) {
            IconSquare(icon, colour)
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Box(Modifier.size(Dimens.TouchTarget), contentAlignment = Alignment.Center) { trailing() }
        }
    }
}

@Composable
internal fun IconSquare(@DrawableRes icon: Int, colour: Color) {
    Surface(
        shape = RoundedCornerShape(ICON_SQUARE_CORNER),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = colour,
        modifier = Modifier.size(ICON_SQUARE),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(painterResource(icon), contentDescription = null) }
    }
}

@Composable
private fun Chevron() {
    Icon(
        painterResource(R.drawable.ic_chevron_right),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val ROW_HEIGHT = 56.dp
internal val ICON_SQUARE = 40.dp
private val ICON_SQUARE_CORNER = 12.dp
private val PROGRESS_WIDTH = 160.dp
private const val PERCENT = 100
private const val SEPARATOR = " · "
