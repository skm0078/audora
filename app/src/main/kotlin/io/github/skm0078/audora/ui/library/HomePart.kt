// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/HomePart.kt
// Purpose: The parts above the books the listener orders in "Make it yours": Continue listening, the line of the day,
//          Recently added and From ListenLock, built once for the grid and the list alike (ADR
//          2026-10-28-make-it-yours, ADR 2026-10-29-in-app-cookbook).

package io.github.skm0078.audora.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.home.HomeLayout
import io.github.skm0078.audora.core.home.HomeSection
import io.github.skm0078.audora.ui.theme.Dimens

/** One part above the books: [key] for the lazy list, [content] drawn edge to edge of the books' column. */
internal class HomePart(val key: String, val content: @Composable () -> Unit)

/**
 * The parts to show above [list]'s books, in the listener's order. Only on the whole library: while a search, a shelf
 * or a filter narrows it, the books come first, as the Continue card always did.
 */
internal fun homeParts(
    list: LibraryListState.Loaded,
    home: LibraryHome,
    covers: CoverSource,
    onContinue: (LibraryItem) -> Unit,
    onTap: (LibraryItem) -> Unit,
    onLibraryAction: (LibraryAction) -> Unit = {},
): List<HomePart> {
    val layout = home.layout?.takeIf { list.query.isDefault } ?: return emptyList()
    return layout.shownOrder.mapNotNull { section ->
        when (section) {
            HomeSection.CONTINUE -> list.continueReading?.let { latest ->
                HomePart(CONTINUE_KEY) {
                    ContinueReadingCard(
                        latest,
                        onContinue = { onContinue(latest) },
                        onOpen = { onTap(latest) },
                        covers = covers,
                        glance = home.glances[latest.book.id],
                        tinted = layout.coverTint,
                    )
                }
            }

            HomeSection.LINE_OF_THE_DAY -> home.line?.let { line ->
                HomePart(LINE_KEY) { LineOfTheDayCard(line) { home.onOpenLine(line.anchor) } }
            }

            HomeSection.RECENTLY_ADDED -> recentlyAdded(list).takeIf { it.isNotEmpty() }?.let { recent ->
                HomePart(RECENT_KEY) { RecentlyAddedRow(recent, covers, onTap) }
            }

            HomeSection.FROM_LISTENLOCK -> HomePart(FROM_LISTENLOCK_KEY) {
                FromListenLockRow(AppDocument.entries) { onLibraryAction(it.action) }
            }

            HomeSection.SHELF_CHIPS -> null // Pinned above the books, not one of these.
        }
    }
}

/** Whether the shelf chips show above the books. */
internal fun showsShelfChips(home: LibraryHome): Boolean = (home.layout ?: HomeLayout()).shows(HomeSection.SHELF_CHIPS)

/** The newest books first, at most [RECENT_COUNT]. */
internal fun recentlyAdded(list: LibraryListState.Loaded): List<LibraryItem> =
    list.all.sortedByDescending { it.book.addedAtMillis }.take(RECENT_COUNT)

/** "Your line of the day": one of the listener's highlights, its book and page; a tap opens the book there. */
@Composable
internal fun LineOfTheDayCard(line: DayLine, onOpen: () -> Unit) {
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(
            Modifier.padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceM),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
        ) {
            Text(
                stringResource(R.string.home_section_line),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.library_last_heard_line, line.quote),
                style = MaterialTheme.typography.titleMedium,
                maxLines = LINE_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.library_line_of_the_day_where, displayTitle(line.book), line.page),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A heading and a sideways row of the newest covers; a tap opens a book as on the grid. */
@Composable
internal fun RecentlyAddedRow(books: List<LibraryItem>, covers: CoverSource, onTap: (LibraryItem) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        Text(
            stringResource(R.string.home_section_recent),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceL),
            contentPadding = PaddingValues(end = Dimens.SpaceL),
        ) {
            items(books, key = { it.book.id.value }) { item ->
                Column(
                    Modifier.width(RECENT_COVER_WIDTH).clickable { onTap(item) },
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
                ) {
                    BookCover(item.book, covers, Modifier.size(RECENT_COVER_WIDTH, RECENT_COVER_WIDTH * COVER_RATIO))
                    Text(
                        displayTitle(item.book),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = TITLE_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

internal const val CONTINUE_KEY = "continue-reading"
internal const val LINE_KEY = "line-of-the-day"
internal const val RECENT_KEY = "recently-added"
internal const val FROM_LISTENLOCK_KEY = "from-listenlock"
internal const val RECENT_COUNT = 12
private const val LINE_LINES = 3
private val RECENT_COVER_WIDTH = 96.dp
