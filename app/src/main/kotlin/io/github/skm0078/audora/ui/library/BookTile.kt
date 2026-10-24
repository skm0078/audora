// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/BookTile.kt
// Purpose: One book in the library's cover grid (ADR-036): cover, title, where it stands, and its menu.

package io.github.skm0078.audora.ui.library

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.BookProgress
import io.github.skm0078.audora.ui.jobs.JobProgressBar
import io.github.skm0078.audora.ui.jobs.jobStatus
import io.github.skm0078.audora.ui.jobs.runningJobFor
import io.github.skm0078.audora.ui.theme.Dimens
import kotlin.math.roundToInt

/**
 * One book in the cover grid (ADR-036): the cover, two lines of title and where it stands. TalkBack reads the tile
 * as one item, "title, status", and the menu as its own button.
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
) {
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
                .clickable(onClick = onTap)
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
            Column(Modifier.weight(1f).padding(top = Dimens.SpaceXs).clearAndSetSemantics {}) {
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
                    color = if (item.needsLocating) {
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
            BookMenu(item, onRemove, onExport, onAddToUpNext, onDetails, onAction)
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
