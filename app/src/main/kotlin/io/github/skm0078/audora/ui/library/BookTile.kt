// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/BookTile.kt
// Purpose: One book in the library's cover grid (ADR-036): cover, title, where it stands, and its menu, which a long
//          press opens too (ADR 2026-10-28-book-menu-sort-sheet).

package io.github.skm0078.audora.ui.library

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.BookProgress
import io.github.skm0078.audora.playback.jobs.LongJob
import io.github.skm0078.audora.ui.jobs.JobProgressBar
import io.github.skm0078.audora.ui.jobs.jobStatus
import io.github.skm0078.audora.ui.jobs.runningJobFor
import io.github.skm0078.audora.ui.kids.LocalKidsMode
import io.github.skm0078.audora.ui.theme.Dimens
import kotlin.math.roundToInt

/**
 * One book in the cover grid (ADR-036): the cover, two lines of title and where it stands. TalkBack reads the tile
 * as one item, "title, status", and the menu as its own button; a long press on the cover opens the menu as well.
 */
@Composable
internal fun BookTile(
    item: LibraryItem,
    onTap: () -> Unit,
    onRemove: () -> Unit,
    onExport: () -> Unit,
    covers: CoverSource = CoverSource.None,
    onAddToUpNext: () -> Unit = {},
    onDetails: () -> Unit = {},
    onAction: (BookAction) -> Unit = {},
    onPlay: () -> Unit = {},
) {
    var menuOpen by rememberSaveable(item.book.id.value) { mutableStateOf(false) }
    val kids = LocalKidsMode.current
    val title = displayTitle(item.book)
    val status = availabilityWarning(item.availability) ?: tileStatus(item.progress)
    val job = runningJobFor(item.book.id)
    val jobLine = job?.let { jobStatus(it) }
    val author = item.book.author?.let { stringResource(R.string.book_details_author, it) }
    val named = listOfNotNull(title, author).joinToString(", ")
    val described = stringResource(R.string.library_tile_description, named, status)
    val description = jobLine?.let { "$described. $it" } ?: described
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
        Box(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .combinedClickable(
                    onLongClickLabel = stringResource(R.string.library_book_menu_named, title),
                    onLongClick = if (kids) null else ({ menuOpen = true }),
                    onClick = onTap,
                )
                .clearAndSetSemantics { contentDescription = description },
        ) {
            BookCover(item.book, covers, Modifier.fillMaxWidth().aspectRatio(1f / COVER_RATIO))
            item.progress?.let { progress ->
                LinearProgressIndicator(
                    progress = { progress.fraction },
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                )
            }
        }
        Row(verticalAlignment = Alignment.Top) {
            TileText(title, author, status, item.needsLocating, job, jobLine, Modifier.weight(1f))
            BookMenuButton(item) { menuOpen = true }
        }
    }
    if (menuOpen) {
        val actions = BookMenuActions(onPlay, onAddToUpNext, onExport, onDetails, onRemove, onAction)
        BookMenuSheet(item, covers, actions) { menuOpen = false }
    }
}

/** The words under the cover; TalkBack hears them in the tile's own description, so they stay silent. */
@Composable
private fun TileText(
    title: String,
    author: String?,
    status: String,
    needsLocating: Boolean,
    job: LongJob?,
    jobLine: String?,
    modifier: Modifier,
) {
    Column(modifier.padding(top = Dimens.SpaceXs).clearAndSetSemantics {}) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = TITLE_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        author?.let { TileLine(it) }
        Text(
            status,
            style = MaterialTheme.typography.bodySmall,
            color = if (needsLocating) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (job != null && jobLine != null) {
            Text(
                jobLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            JobProgressBar(job, Modifier.padding(top = Dimens.SpaceXs))
        }
    }
}

@Composable
private fun TileLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** "New", "42 % read" or "Finished", from the saved reading position. */
@Composable
private fun tileStatus(progress: BookProgress?): String = when {
    progress == null -> stringResource(R.string.library_status_new)
    progress.page + 1 >= progress.pageCount -> stringResource(R.string.library_status_finished)
    else -> stringResource(R.string.library_status_percent, (progress.fraction * PERCENT).roundToInt())
}

private const val PERCENT = 100
