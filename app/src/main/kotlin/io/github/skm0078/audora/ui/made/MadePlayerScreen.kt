// File: app/src/main/kotlin/io/github/skm0078/audora/ui/made/MadePlayerScreen.kt
// Purpose: Plays a saved audiobook or studio audio file inside the app (ADR-068): play and pause, a seek bar,
//          back 15 s and forward 30 s, chapters and speed, going on from where the listener stopped.

package io.github.skm0078.audora.ui.made

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.OutputSet
import io.github.skm0078.audora.di.LibraryEntryPoint
import io.github.skm0078.audora.ui.library.titleOrUntitled
import io.github.skm0078.audora.ui.theme.Dimens
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine

/** The set by its key, with its book's title; the screen says the audio is gone once the set is. */
@Composable
fun MadePlayerRoute(setKey: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val entry = remember { LibraryEntryPoint.of(context) }
    val found by remember(setKey) {
        combine(entry.outputs().observeAll(), entry.library().books()) { outputs, books ->
            OutputSet.of(outputs.filter { it.setKey == setKey }).firstOrNull()?.let { set ->
                set to books.firstOrNull { it.id == set.bookId }?.title.orEmpty()
            }
        }
    }.collectAsStateWithLifecycle(null)
    val controller = remember { MadeAudioController(context.applicationContext) }
    val state by controller.state.collectAsStateWithLifecycle()
    val current = found
    val title = titleOrUntitled(current?.second.orEmpty())
    DisposableEffect(controller) { onDispose(controller::release) }
    LaunchedEffect(current?.first?.key) { current?.let { (set, _) -> controller.connect(set, title) } }
    LaunchedEffect(controller) {
        while (true) {
            controller.refresh()
            delay(POSITION_TICK_MS)
        }
    }
    MadePlayerScreen(current?.first, title, state, controller, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MadePlayerScreen(
    set: OutputSet?,
    title: String,
    state: MadePlayerState,
    controls: MadeAudioController?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(title, Modifier.semantics { heading() }, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            Modifier.padding(innerPadding),
            contentPadding = PaddingValues(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceS),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
        ) {
            if (set == null || state.failed) {
                item { Text(stringResource(R.string.made_player_gone), color = MaterialTheme.colorScheme.error) }
            }
            if (set != null) {
                item { Controls(set, state, controls) }
                if (set.files.size > 1) {
                    itemsIndexed(set.files, key = { _, file -> file.part }) { index, file ->
                        val playing = index == state.index
                        ListItem(
                            headlineContent = {
                                Text(file.name, fontWeight = if (playing) FontWeight.Bold else FontWeight.Normal)
                            },
                            supportingContent = { file.durationMs?.let { Text(clockText(it)) } },
                            modifier = Modifier
                                .clickable { controls?.chapter(index) }
                                .semantics { selected = playing },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Controls(set: OutputSet, state: MadePlayerState, controls: MadeAudioController?) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        if (state.count > 1) {
            Text(stringResource(R.string.made_player_chapter, state.index + 1, state.count))
        }
        Text(set.files.getOrNull(state.index)?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
        SeekBar(state, controls)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { controls?.previousChapter() }, enabled = state.index > 0) {
                Icon(painterResource(R.drawable.ic_skip_previous), stringResource(R.string.made_player_previous))
            }
            SkipButton("−15", R.string.made_player_back) { controls?.skip(-BACK_MS) }
            FilledIconButton(onClick = { controls?.playPause() }, Modifier.size(Dimens.PlayButton)) {
                val (icon, label) = if (state.playing) {
                    R.drawable.ic_pause to R.string.made_player_pause
                } else {
                    R.drawable.ic_play to R.string.made_player_play
                }
                Icon(painterResource(icon), stringResource(label))
            }
            SkipButton("+30", R.string.made_player_forward) { controls?.skip(FORWARD_MS) }
            IconButton(onClick = { controls?.nextChapter() }, enabled = state.index < state.count - 1) {
                Icon(painterResource(R.drawable.ic_skip_next), stringResource(R.string.made_player_next))
            }
        }
        OutlinedButton(onClick = { controls?.nextSpeed() }, Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.made_player_speed, speedText(state.speed)))
        }
    }
}

/** While dragging the bar follows the finger; the seek happens once, when it is let go. */
@Composable
private fun SeekBar(state: MadePlayerState, controls: MadeAudioController?) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = state.durationMs.coerceAtLeast(1)
    val fraction = dragging ?: (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val position = clockText(dragging?.let { (it * duration).toLong() } ?: state.positionMs)
    val total = clockText(state.durationMs)
    val spoken = stringResource(R.string.made_player_position, position, total)
    Slider(
        value = fraction,
        onValueChange = { dragging = it },
        onValueChangeFinished = {
            dragging?.let { controls?.seekTo((it * duration).toLong()) }
            dragging = null
        },
        enabled = state.durationMs > 0,
        modifier = Modifier.semantics { stateDescription = spoken },
    )
    Text(spoken, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SkipButton(text: String, description: Int, onClick: () -> Unit) {
    val label = stringResource(description)
    OutlinedButton(onClick = onClick, Modifier.semantics { contentDescription = label }) { Text(text) }
}

/** "1:05:09" or "4:07": the player's clock, not words. */
internal fun clockText(ms: Long): String {
    val seconds = (ms / MS_PER_SECOND).coerceAtLeast(0)
    val hours = seconds / SECONDS_PER_HOUR
    val minutes = seconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val rest = seconds % SECONDS_PER_MINUTE
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, rest)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, rest)
    }
}

/** "1", "1.25", "0.75". */
internal fun speedText(speed: Float): String = String.format(Locale.ROOT, "%.2f", speed).trimEnd('0').trimEnd('.')

private const val POSITION_TICK_MS = 500L
private const val BACK_MS = 15_000L
private const val FORWARD_MS = 30_000L
private const val MS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
