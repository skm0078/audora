// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/PlayerScreen.kt
// Purpose: The full player: the sentence being read in large type between its neighbours, "What did it say?"
//          (plan item 5) and every control; in kids mode (plan item 71) just three big buttons.

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.speech.voices.VoiceRow
import io.github.skm0078.audora.ui.jobs.LongJobsPanel
import io.github.skm0078.audora.ui.kids.KidsControls
import io.github.skm0078.audora.ui.kids.LocalKidsMode
import io.github.skm0078.audora.ui.library.BookCover
import io.github.skm0078.audora.ui.library.COVER_RATIO
import io.github.skm0078.audora.ui.library.CoverSource
import io.github.skm0078.audora.ui.motion.sharedCover
import io.github.skm0078.audora.ui.theme.Dimens
import io.github.skm0078.audora.ui.theme.WindowWidths

/**
 * Runs on the same [ReaderModel] as the reader, so both always agree on where reading is. The sentences come from
 * the page being read; while the page loads, or before anything plays, a hint stands in.
 *
 * @param onShowPage opens the reader at this book, to see the page or tap a sentence.
 * @param onOpenUpNext shows the Up next sheet (plan item 25).
 * @param book the library's book, for its cover.
 * @param spokenWord the word being said in the spoken sentence (plan item 14), marked like the reader marks it; read
 *   only by the sentence, so the rest of the player does not redraw on every word.
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
    spokenWord: () -> IntRange? = { null },
) {
    val title = state.title.orEmpty()
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val parts = PlayerParts(state, config, actions, onShowPage, onOpenUpNext, title, book, covers)
            // A phone in landscape, a foldable open or a tablet on its side: book and progress on one side, the
            // sentence and the controls on the other, instead of one tall column squeezed into a short window.
            if (rememberPanes(maxWidth, maxHeight)) {
                PlayerPanes(parts, voices, spokenWord)
            } else {
                // A tablet in portrait keeps the phone layout at a readable width in the middle.
                Column(Modifier.widthIn(max = WindowWidths.READABLE).fillMaxSize()) {
                    BoxWithConstraints(Modifier.weight(1f)) {
                        // A large font with the replay card in a short window: the column scrolls, the cover goes,
                        // and the sentence keeps a fixed share instead of being squeezed to nothing.
                        PlayerBody(parts, spokenWord, compact = maxHeight < COMPACT_HEIGHT)
                    }
                    PlayerControls(state, config, actions, voices)
                }
            }
        }
    }
}

/**
 * Two panes once the window is clearly wider than tall, back to one once it is clearly taller: a live resize near
 * square (a foldable, split screen) does not flip layouts, and lose the panes' scroll, on every frame.
 */
@Composable
private fun rememberPanes(width: Dp, height: Dp): Boolean {
    var last by remember { mutableStateOf(false) }
    val ratio = width / height
    val panes = width >= WindowWidths.MEDIUM && if (last) ratio > PANES_OFF_RATIO else ratio > PANES_ON_RATIO
    // Remembered after the frame is drawn, never during composition; an unchanged answer recomposes nothing.
    SideEffect { last = panes }
    return panes
}

private const val PANES_ON_RATIO = 1.1f
private const val PANES_OFF_RATIO = 0.9f

/** What both player layouts draw, gathered once. */
private data class PlayerParts(
    val state: ReaderState,
    val config: ReaderConfig,
    val actions: ReaderActions,
    val onShowPage: () -> Unit,
    val onOpenUpNext: (() -> Unit)?,
    val title: String,
    val book: Book?,
    val covers: CoverSource,
)

@Composable
private fun PlayerControls(state: ReaderState, config: ReaderConfig, actions: ReaderActions, voices: List<VoiceRow>) {
    when {
        state.document !is DocumentState.Open -> Unit

        LocalKidsMode.current -> KidsControls(
            playing = state.playback.wantsToPlay,
            onPlayPause = actions::onPlayPause,
            onBack = actions::onSkipBack,
            onForward = actions::onSkipForward,
        )

        else -> ReaderControls(state, config, actions, voices = voices)
    }
}

@Composable
private fun PlayerBody(parts: PlayerParts, spokenWord: () -> IntRange?, compact: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(if (compact) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(TOP_AND_SIDES))
            .padding(horizontal = Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL),
    ) {
        PlayerTopBar(parts.state, parts.actions, parts.onShowPage, parts.onOpenUpNext)
        BookLine(parts.title, parts.book.takeUnless { compact }, parts.covers)
        LongJobsPanel(book = parts.state.bookId)
        SentencesAround(
            parts.state,
            spokenWord,
            if (compact) Modifier.height(COMPACT_SENTENCE_HEIGHT) else Modifier.weight(1f),
        )
        ReplayAndProgress(parts)
    }
}

/** Two panes side by side; each scrolls on its own if a large font needs more room than the window has. */
@Composable
private fun PlayerPanes(parts: PlayerParts, voices: List<VoiceRow>, spokenWord: () -> IntRange?) {
    Row(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing.only(START_PANE))
                .padding(horizontal = Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL),
        ) {
            PlayerTopBar(parts.state, parts.actions, parts.onShowPage, parts.onOpenUpNext)
            BookLine(parts.title, parts.book, parts.covers)
            LongJobsPanel(book = parts.state.bookId)
            ReplayAndProgress(parts)
        }
        // The controls' navigation-bar padding must not add the start side here: that side is the other pane's.
        Column(
            Modifier.weight(
                1f,
            ).fillMaxHeight().consumeWindowInsets(WindowInsets.navigationBars.only(WindowInsetsSides.Start)),
        ) {
            SentencesAround(
                parts.state,
                spokenWord,
                Modifier
                    .weight(1f)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(END_PANE))
                    .padding(horizontal = Dimens.ScreenPadding),
            )
            PlayerControls(parts.state, parts.config, parts.actions, voices)
        }
    }
}

/** "What did it say?", the undo of a jump and how far through the book: below the sentence, or beside it. */
@Composable
private fun ReplayAndProgress(parts: PlayerParts) {
    val state = parts.state
    if (state.document is DocumentState.Open && !LocalKidsMode.current) {
        WhatDidItSay(rememberWhatDidItSay(state, parts.actions))
    }
    state.undoJumpTo?.let { target ->
        TextButton(onClick = parts.actions::onUndoJump) {
            Icon(painterResource(R.drawable.ic_undo), contentDescription = null)
            Text(stringResource(R.string.reader_undo_jump, target.page + 1))
        }
    }
    BookProgress(state, parts.actions)
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
        if (!LocalKidsMode.current) PlayerExtras(state, actions, onShowPage, onOpenUpNext)
    }
}

/** Chapters, Up next and the page: the player's ways out, which kids mode (plan item 71) leaves off. */
@Composable
private fun PlayerExtras(
    state: ReaderState,
    actions: ReaderActions,
    onShowPage: () -> Unit,
    onOpenUpNext: (() -> Unit)?,
) {
    Row {
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
        book?.let {
            BookCover(
                it,
                covers,
                Modifier.sharedCover(it.id).size(Dimens.CoverMedium, Dimens.CoverMedium * COVER_RATIO),
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = TITLE_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
    }
}

/** How far through the book, by pages (the library's measure), with chapter marks to tap. */
@Composable
private fun BookProgress(state: ReaderState, actions: ReaderActions) {
    val pages = state.pageCount ?: return
    val page = state.readingPosition?.page ?: state.page
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
        // A child's stray tap must not jump chapters, so kids mode shows the bar only.
        val kids = LocalKidsMode.current
        ChapterProgress(
            progress = (page + 1).toFloat() / pages,
            chapters = state.chapters,
            pageCount = pages,
            onChapter = if (kids) null else actions::onChapter,
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
                pluralStringResource(R.plurals.player_pages, pages, pages),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Dimens.SpaceS))
    }
}

private const val TITLE_LINES = 3

/** Below this the player's upper part scrolls (split screen, or a large font in a short window). */
private val COMPACT_HEIGHT = 420.dp

/** The spoken sentence's share when the player scrolls: about four lines of large type, scrolling inside. */
private val COMPACT_SENTENCE_HEIGHT = 160.dp

/** The controls panel takes the bottom inset itself. */
private val TOP_AND_SIDES = WindowInsetsSides.Top + WindowInsetsSides.Horizontal

/** Side by side, the book pane meets the top, start and bottom edges; the sentence pane the top and end. */
private val START_PANE = WindowInsetsSides.Vertical + WindowInsetsSides.Start
private val END_PANE = WindowInsetsSides.Top + WindowInsetsSides.End
