// File: app/src/main/kotlin/io/github/skm0078/audora/ui/made/MadePlayerScreen.kt
// Purpose: Plays a saved audiobook or studio audio file inside the app (ADR-068), laid out like the book's own
//          player (ADR 2026-10-28-made-tab): the book's cover and the chapter playing, a seek bar, and the controls
//          panel with the same speed and sleep timer as the book player, going on from where the listener stopped.

package io.github.skm0078.audora.ui.made

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.OutputSet
import io.github.skm0078.audora.di.LibraryEntryPoint
import io.github.skm0078.audora.playback.studio.studioDuration
import io.github.skm0078.audora.ui.library.BookCover
import io.github.skm0078.audora.ui.library.COVER_RATIO
import io.github.skm0078.audora.ui.library.CoverSource
import io.github.skm0078.audora.ui.library.titleOrUntitled
import io.github.skm0078.audora.ui.theme.Dimens
import io.github.skm0078.audora.ui.theme.WindowWidths
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine

/** The set by its key, with its book for the title and cover; the screen says the audio is gone once the set is. */
@Composable
fun MadePlayerRoute(setKey: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val entry = remember { LibraryEntryPoint.of(context) }
    val found by remember(setKey) {
        combine(entry.outputs().observeAll(), entry.library().books()) { outputs, books ->
            OutputSet.of(outputs.filter { it.setKey == setKey }).firstOrNull()?.let { set ->
                set to books.firstOrNull { it.id == set.bookId }
            }
        }
    }.collectAsStateWithLifecycle(null)
    val controller = remember { MadeAudioController(context.applicationContext) }
    val state by controller.state.collectAsStateWithLifecycle()
    val current = found
    val title = titleOrUntitled(current?.second?.title.orEmpty())
    DisposableEffect(controller) { onDispose(controller::release) }
    // True only on the listener's own open: a screen Android restores after closing the app is saved as false, so it
    // shows the audio paused instead of starting it unasked.
    var openedByListener by rememberSaveable(setKey) { mutableStateOf(true) }
    LaunchedEffect(current?.first?.key) {
        current?.let { (set, _) ->
            controller.connect(set, title, play = openedByListener)
            openedByListener = false
        }
    }
    LaunchedEffect(controller) {
        while (true) {
            controller.refresh()
            delay(POSITION_TICK_MS)
        }
    }
    val parts = MadePlayerParts(current?.second, entry.covers(), remember { ReaderConfig() })
    MadePlayerScreen(current?.first, title, state, controller, onBack, parts = parts)
}

/** The book (for its cover), where covers come from and the speed and timer choices: what previews leave out. */
class MadePlayerParts(
    val book: Book? = null,
    val covers: CoverSource = CoverSource.None,
    val config: ReaderConfig = ReaderConfig(),
)

@Composable
fun MadePlayerScreen(
    set: OutputSet?,
    title: String,
    state: MadePlayerState,
    controls: MadeAudioController?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    parts: MadePlayerParts = MadePlayerParts(),
) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            // A tablet keeps the phone's column at a readable width in the middle, as the book player does.
            Column(Modifier.widthIn(max = WindowWidths.READABLE).fillMaxWidth().weight(1f)) {
                MadePlayerTopBar(set, title, state, controls, onBack)
                BoxWithConstraints(Modifier.weight(1f)) {
                    NowPlaying(set, state, parts, height = maxHeight)
                }
                if (set != null) MadeSeekBar(state, controls, Modifier.padding(horizontal = Dimens.SpaceXl))
            }
            val readable = Modifier.widthIn(max = WindowWidths.READABLE)
            if (set != null) MadeControlsPanel(state, controls, parts.config, readable)
        }
    }
}

/** Back, what this is and its book, and the player's ways out: Chapters for a set of more than one, and Share. */
@Composable
private fun MadePlayerTopBar(
    set: OutputSet?,
    title: String,
    state: MadePlayerState,
    controls: MadeAudioController?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(TOP_AND_SIDES)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
        }
        Column(Modifier.weight(1f).padding(horizontal = Dimens.SpaceXs)) {
            set?.let {
                Text(
                    stringResource(R.string.made_player_kind, kindLabel(it.kind)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
        }
        if (set != null) {
            MadeChaptersButton(set, state, controls)
            IconButton(onClick = { MadeIntents.share(context, set.files) }) {
                Icon(painterResource(R.drawable.ic_share), stringResource(R.string.made_share))
            }
        }
    }
}

/** The book's cover large, the chapter playing under it and when the set was saved; scrolls in a short window. */
@Composable
private fun NowPlaying(set: OutputSet?, state: MadePlayerState, parts: MadePlayerParts, height: Dp) {
    // At least the room there is, so the cover and name sit in its middle; more scrolls (a large font).
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .heightIn(min = height)
            .padding(horizontal = Dimens.SpaceXl, vertical = Dimens.SpaceL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL, Alignment.CenterVertically),
    ) {
        if (height >= COMPACT_HEIGHT) {
            parts.book?.let {
                BookCover(it, parts.covers, Modifier.widthIn(max = COVER_MAX_WIDTH).aspectRatio(1f / COVER_RATIO))
            }
        }
        if (set == null || state.failed) {
            Text(
                stringResource(R.string.made_player_gone),
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        set?.let { NowPlayingText(it, state) }
    }
}

@Composable
private fun NowPlayingText(set: OutputSet, state: MadePlayerState) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
    ) {
        Text(
            set.files.getOrNull(state.index)?.name.orEmpty(),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            maxLines = NAME_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            savedLine(set),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** "Saved 27 Oct 2026 · 1 h 40 min · 48 MB", leaving out what is not known. */
@Composable
private fun savedLine(set: OutputSet): String {
    val context = LocalContext.current
    val date = DateUtils.formatDateTime(
        context,
        set.createdAtMillis,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR,
    )
    return listOfNotNull(
        stringResource(R.string.made_player_saved, date),
        set.durationMs?.let { context.studioDuration(it) },
        set.sizeBytes?.let { Formatter.formatShortFileSize(context, it) },
    ).joinToString(DETAIL_SEPARATOR)
}

private const val POSITION_TICK_MS = 500L
private const val NAME_LINES = 2
internal const val DETAIL_SEPARATOR = " · "

/** Below this the cover gives its room to the chapter's name (split screen, landscape, a large font). */
private val COMPACT_HEIGHT = 320.dp
private val COVER_MAX_WIDTH = 240.dp

/** The controls panel takes the bottom inset itself. */
private val TOP_AND_SIDES = WindowInsetsSides.Top + WindowInsetsSides.Horizontal
