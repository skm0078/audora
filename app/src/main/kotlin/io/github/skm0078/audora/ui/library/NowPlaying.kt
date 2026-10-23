// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/NowPlaying.kt
// Purpose: The library's reading aids: the Continue reading card, each book's progress line and the mini player
//          (a tap or a swipe up opens the full player).

package io.github.skm0078.audora.ui.library

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.BookProgress
import io.github.skm0078.audora.core.library.BookTitles
import io.github.skm0078.audora.ui.motion.sharedCover
import io.github.skm0078.audora.ui.reader.PlaybackSnapshot
import io.github.skm0078.audora.ui.theme.Dimens
import kotlin.math.roundToInt

/**
 * What the mini player shows: the book being read, where, and whether it plays.
 *
 * @property title null when the book is not in the library list (removed while playing).
 * @property book the library's book, for its cover; null as for [title].
 */
data class NowPlaying(
    val bookId: BookId,
    val title: String?,
    val page: Int,
    val pageCount: Int?,
    val playing: Boolean,
    val buffering: Boolean,
    val book: Book? = null,
) {
    /** How far through the book by pages, as the library's progress counts it; null while the length is unknown. */
    val fraction: Float?
        get() = pageCount?.takeIf { it > 0 }?.let { ((page + 1).toFloat() / it).coerceIn(0f, 1f) }
}

/** The book read last, with its cover, where it stands and one big Continue button (ADR-036). */
@Composable
internal fun ContinueReadingCard(
    item: LibraryItem,
    onContinue: () -> Unit,
    onOpen: () -> Unit,
    covers: CoverSource = CoverSource.None,
) {
    val progress = item.progress ?: return
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Dimens.SpaceL),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceL),
        ) {
            BookCover(item.book, covers, Modifier.size(Dimens.CoverMedium, Dimens.CoverMedium * COVER_RATIO))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
                Text(
                    stringResource(R.string.library_continue_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    displayTitle(item.book),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = TITLE_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
                ProgressLine(progress)
                Button(onClick = onContinue, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) {
                    Icon(painterResource(R.drawable.ic_play), contentDescription = null)
                    Text(stringResource(R.string.library_continue), Modifier.padding(start = Dimens.SpaceS))
                }
            }
        }
    }
}

/** "Page 12 of 300 · 4 %" over a thin bar. */
@Composable
internal fun ProgressLine(progress: BookProgress) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
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

/**
 * Sits above the tabs on every tab (ADR-036). Inverse colours, so it reads as a floating player on light and dark
 * screens alike; the whole card opens the full player and the button plays or pauses.
 */
@Composable
fun MiniPlayer(
    playing: NowPlaying,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
    covers: CoverSource = CoverSource.None,
) {
    Surface(
        onClick = onOpen,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.SpaceS, vertical = Dimens.SpaceXs)
            .swipeUpToOpen(onOpen),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .heightIn(min = Dimens.MiniPlayerHeight)
                    .padding(start = Dimens.SpaceM, end = Dimens.SpaceS),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
            ) {
                playing.book?.let { book ->
                    BookCover(
                        book,
                        covers,
                        Modifier.sharedCover(book.id).size(
                            Dimens.CoverMini,
                            Dimens.CoverMini * COVER_RATIO,
                        ),
                    )
                }
                Column(Modifier.weight(1f).padding(vertical = Dimens.SpaceS)) {
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
                MiniPlayPause(playing, onPlayPause)
            }
            playing.fraction?.let { fraction ->
                LinearProgressIndicator(
                    progress = { fraction },
                    color = MaterialTheme.colorScheme.inversePrimary,
                    trackColor = Color.Transparent,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun MiniPlayPause(playing: NowPlaying, onPlayPause: () -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        if (playing.buffering) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.inversePrimary,
                modifier = Modifier.size(Dimens.PlayButtonSmall),
            )
        }
        FilledIconButton(
            onClick = onPlayPause,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.inversePrimary,
                contentColor = MaterialTheme.colorScheme.inverseSurface,
            ),
            modifier = Modifier.size(Dimens.PlayButtonSmall),
        ) {
            if (playing.playing) {
                Icon(painterResource(R.drawable.ic_pause), stringResource(R.string.reader_pause))
            } else {
                Icon(painterResource(R.drawable.ic_play), stringResource(R.string.reader_play))
            }
        }
    }
}

private const val PERCENT = 100

/**
 * A swipe up opens the full player, as music apps do (UI plan, mini player). A tap still opens it too, and TalkBack
 * users have the tap; the swipe is a shortcut, so it needs no semantics of its own.
 */
@Composable
private fun Modifier.swipeUpToOpen(onOpen: () -> Unit): Modifier {
    val open by rememberUpdatedState(onOpen)
    val density = LocalDensity.current
    val threshold = with(density) { SWIPE_OPEN_DISTANCE.toPx() }
    // Velocity arrives in px/s; the limit is in dp/s so a fling feels the same on every screen density.
    val flingSpeed = with(density) { SWIPE_OPEN_VELOCITY.toPx() }
    var dragged by remember { mutableFloatStateOf(0f) }
    return draggable(
        orientation = Orientation.Vertical,
        state = rememberDraggableState { delta -> dragged += delta },
        onDragStopped = { velocity ->
            if (dragged < -threshold || velocity < -flingSpeed) open()
            dragged = 0f
        },
    )
}

/** How far up the finger must travel, or how fast, for a swipe to open the player rather than be a stray touch. */
private val SWIPE_OPEN_DISTANCE = 48.dp

/** Per second: about 1,000 px/s on a 2.6x phone, as before. */
private val SWIPE_OPEN_VELOCITY = 400.dp

/** Covers are drawn 3:4, like a page. */
internal const val COVER_RATIO = 4f / 3f

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
        book = book,
    )
}
