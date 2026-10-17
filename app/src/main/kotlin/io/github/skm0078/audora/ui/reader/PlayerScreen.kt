// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/PlayerScreen.kt
// Purpose: The full player: the sentence being read in large type between its neighbours, and every control.

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.speech.voices.VoiceRow
import io.github.skm0078.audora.ui.library.BookCover
import io.github.skm0078.audora.ui.library.COVER_RATIO
import io.github.skm0078.audora.ui.library.CoverSource
import io.github.skm0078.audora.ui.theme.Dimens

/**
 * Runs on the same [ReaderModel] as the reader, so both always agree on where reading is. The sentences come from
 * the page being read; while the page loads, or before anything plays, a hint stands in.
 *
 * @param onShowPage opens the reader at this book, to see the page or tap a sentence.
 * @param onOpenUpNext shows the Up next sheet (plan item 25).
 * @param book the library's book, for its cover.
 */
@Composable
fun PlayerScreen(
    state: ReaderState,
    config: ReaderConfig,
    actions: ReaderActions,
    onShowPage: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenUpNext: (() -> Unit)? = null,
    book: Book? = null,
    covers: CoverSource = CoverSource.None,
    voices: List<VoiceRow> = emptyList(),
) {
    val title = state.title.orEmpty()
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(TOP_AND_SIDES))
                    .padding(horizontal = Dimens.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL),
            ) {
                PlayerTopBar(state, actions, onShowPage, onOpenUpNext)
                BookLine(title, book, covers)
                SentencesAround(state, Modifier.weight(1f))
                state.undoJumpTo?.let { target ->
                    TextButton(onClick = actions::onUndoJump) {
                        Icon(painterResource(R.drawable.ic_undo), contentDescription = null)
                        Text(stringResource(R.string.reader_undo_jump, target.page + 1))
                    }
                }
                BookProgress(state)
            }
            if (state.document is DocumentState.Open) ReaderControls(state, config, actions, voices = voices)
        }
    }
}

@Composable
private fun PlayerTopBar(
    state: ReaderState,
    actions: ReaderActions,
    onShowPage: () -> Unit,
    onOpenUpNext: (() -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = actions::onBack) {
            Icon(painterResource(R.drawable.ic_expand_more), stringResource(R.string.player_close))
        }
        Text(
            state.pageCount?.let { stringResource(R.string.reader_page_of, state.page + 1, it) }.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(horizontal = Dimens.SpaceS),
        )
        ChaptersButton(state, actions)
        onOpenUpNext?.let { open ->
            IconButton(onClick = open) {
                Icon(painterResource(R.drawable.ic_queue), stringResource(R.string.up_next_title))
            }
        }
        IconButton(onClick = onShowPage) {
            Icon(painterResource(R.drawable.ic_view_page), stringResource(R.string.player_show_page))
        }
    }
}

@Composable
private fun BookLine(title: String, book: Book?, covers: CoverSource) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceL)) {
        book?.let { BookCover(it, covers, Modifier.size(Dimens.CoverMedium, Dimens.CoverMedium * COVER_RATIO)) }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = TITLE_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
    }
}

/**
 * The previous and next sentences frame the spoken one, faded, so the listener can follow the text without the page.
 * Scrolls when a long sentence at a large font scale needs more room than the screen has (item 39).
 */
@Composable
private fun SentencesAround(state: ReaderState, modifier: Modifier = Modifier) {
    val units = (state.content as? PageContent.Ready)?.units.orEmpty()
    val spoken = state.spokenUnitIndex
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL, Alignment.CenterVertically),
    ) {
        when {
            state.content == PageContent.Loading || state.document == DocumentState.Loading ->
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))

            spoken == null -> Text(
                stringResource(R.string.player_idle),
                style = ReadingTextStyles.focus(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> {
                units.getOrNull(spoken - 1)?.let { NeighbourSentence(it) }
                Text(
                    units[spoken].text,
                    style = ReadingTextStyles.spoken(),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                units.getOrNull(spoken + 1)?.let { NeighbourSentence(it) }
            }
        }
    }
}

@Composable
private fun NeighbourSentence(unit: ReadingUnit) {
    Text(
        unit.text,
        style = ReadingTextStyles.body(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = NEIGHBOUR_LINES,
        overflow = TextOverflow.Ellipsis,
    )
}

/** How far through the book, by pages: the same measure as the library's progress. */
@Composable
private fun BookProgress(state: ReaderState) {
    val pages = state.pageCount ?: return
    val page = state.readingPosition?.page ?: state.page
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
        LinearProgressIndicator(
            progress = { ((page + 1).toFloat() / pages).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
        state.timeLeft?.let { TimeLeftLine(it, state.playback.speed) }
        Row {
            Text(
                stringResource(R.string.player_page, page + 1),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.player_pages, pages),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Dimens.SpaceS))
    }
}

/** The faded neighbours stay short, so the spoken sentence keeps the middle of the screen. */
private const val NEIGHBOUR_LINES = 3

private const val TITLE_LINES = 3

/** The controls panel takes the bottom inset itself. */
private val TOP_AND_SIDES = WindowInsetsSides.Top + WindowInsetsSides.Horizontal
