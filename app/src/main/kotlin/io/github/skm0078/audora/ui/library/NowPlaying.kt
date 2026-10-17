// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/NowPlaying.kt
// Purpose: The library's reading aids: the Continue reading card, each book's progress line and the mini player.

package io.github.skm0078.audora.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.BookProgress
import io.github.skm0078.audora.core.library.BookTitles
import io.github.skm0078.audora.ui.reader.PlaybackSnapshot
import kotlin.math.roundToInt

/**
 * What the mini player shows: the book being read, where, and whether it plays.
 *
 * @property title null when the book is not in the library list (removed while playing).
 */
data class NowPlaying(
    val bookId: BookId,
    val title: String?,
    val page: Int,
    val pageCount: Int?,
    val playing: Boolean,
    val buffering: Boolean,
)

@Composable
internal fun ContinueReadingCard(item: LibraryItem, onContinue: () -> Unit, onOpen: () -> Unit) {
    val progress = item.progress ?: return
    ElevatedCard(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.library_continue_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                displayTitle(item.book),
                style = MaterialTheme.typography.titleMedium,
                maxLines = TITLE_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            ProgressLine(progress)
            Button(onClick = onContinue) {
                Icon(painterResource(R.drawable.ic_play), contentDescription = null)
                Text(stringResource(R.string.library_continue), Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** "Page 12 of 300 · 4 %" over a thin bar. */
@Composable
internal fun ProgressLine(progress: BookProgress) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(
                R.string.library_progress,
                progress.page + 1,
                progress.pageCount,
                (progress.fraction * PERCENT).roundToInt(),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
internal fun MiniPlayer(playing: NowPlaying, actions: LibraryActions) {
    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth().clickable { actions.onOpenPlaying(playing.bookId) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    playing.title ?: stringResource(R.string.library_untitled),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val where = playing.pageCount?.let {
                    stringResource(R.string.library_playing_page_of, playing.page + 1, it)
                } ?: stringResource(R.string.library_playing_page, playing.page + 1)
                Text(where, style = MaterialTheme.typography.bodySmall)
            }
            if (playing.buffering) CircularProgressIndicator(Modifier.size(BUFFERING_SIZE))
            FilledIconButton(onClick = actions::onPlayPause) {
                if (playing.playing) {
                    Icon(painterResource(R.drawable.ic_pause), stringResource(R.string.reader_pause))
                } else {
                    Icon(painterResource(R.drawable.ic_play), stringResource(R.string.reader_play))
                }
            }
        }
    }
}

private const val PERCENT = 100
private const val TITLE_LINES = 2
private val BUFFERING_SIZE = 24.dp

/** The mini player's content from the player and the library; null when nothing is loaded in the player. */
fun nowPlaying(playback: PlaybackSnapshot, list: LibraryListState): NowPlaying? {
    val position = playback.position ?: return null
    val book = (list as? LibraryListState.Loaded)?.all?.firstOrNull { it.book.id == position.bookId }?.book
    return NowPlaying(
        bookId = position.bookId,
        title = book?.title?.takeUnless { it == BookTitles.UNTITLED },
        page = position.page,
        pageCount = book?.pageCount,
        playing = playback.wantsToPlay,
        buffering = playback.wantsToPlay && playback.buffering,
    )
}
